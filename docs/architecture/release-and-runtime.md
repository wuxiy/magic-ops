# 发布与 Runtime

## 决策

第一阶段发布路径是 Console 在线推送到生产 Runtime。

平台持有签名私钥。Runtime 只持有公钥验签材料。

离线包是后续交付增强能力，不是第一阶段主发布路径。

第一版 Console 与 Runtime 理论上部署在同一网段，Console 通过 REST 协议主动推送发布包到 Runtime。

第一版签名私钥从项目环境变量读取。后续可迁移到 KMS、HSM、独立发布中心或受控 keystore。

## 发布包

```text
publish-package.zip
├── manifest.json
├── scripts/
│   ├── api-001.magic
│   └── api-002.magic
├── metadata/
│   ├── permissions.json
│   ├── datasource-permissions.json
│   ├── http-target-permissions.json
│   ├── key-ref-permissions.json
│   ├── route-mapping.json
│   └── policy.json
└── signature/
    ├── content.sha256
    ├── metadata.sha256
    ├── policy.sha256
    └── signature.bin
```

## Manifest 字段

第一阶段必需 manifest 字段：

- `projectCode`
- `environment`
- `packageVersion`
- `runtimeVersion`
- `publishedBy`
- `publishedAt`
- `keyId`
- `scripts`
- `metadataHash`
- `policyHash`
- `signAlg`
- `signature`

每个脚本条目必须包含：

- `scriptId`
- `path`
- `method`
- `version`
- `scenario`
- `riskLevel`
- `contentHash`
- `metadataHash`

## 签名范围

签名必须覆盖：

- 脚本内容；
- 路由映射；
- 数据源权限；
- HTTP 目标权限；
- key reference 权限；
- 风险和策略元数据；
- 环境；
- Runtime 兼容版本；
- 发布包版本；
- 发布人身份；
- 发布时间。

任何已签名组件发生变化时，Runtime 必须拒绝发布包。

发布包规范化规则：

- 签名输入不直接使用 zip 二进制；
- 签名输入由 canonical manifest、canonical metadata 和 normalized scripts bytes 组成；
- JSON 使用 UTF-8 编码；
- JSON 字段按字典序排序；
- JSON 不包含无意义空白；
- 文件路径按字典序排序；
- 脚本内容统一 LF 换行；
- hash 算法使用 SHA-256；
- 签名算法第一版使用 SHA256withRSA；
- 签名输入必须形成稳定字节序列，并由测试覆盖。

## Runtime 加载流程

```text
receive publish package
  -> unpack in staging area
  -> read manifest
  -> verify environment and Runtime version
  -> verify package signature
  -> verify script hashes
  -> verify metadata and policy hashes
  -> verify script status and risk policy
  -> persist active package to active_packages table
  -> atomically activate package
  -> record load audit
```

Runtime 激活包持久化（切片 33 起）：验签通过的发布包以 `PublishPackage.toMap()` 序列化为 JSON 落 `active_packages` 表（同一时刻至多一行 ACTIVE，新激活将旧行置 INACTIVE）。`@PostConstruct` 启动时读取最近 ACTIVE 行，反序列化并重新验签后激活；重载验签/反序列化失败时该行置 INACTIVE 并落 `PACKAGE_REJECTED` 关键审计，不激活，保证重启后不会加载被篡改的发布包。

Console 与 Runtime 共享同一 PostgreSQL。Console 经 Flyway 拥有生产 schema，Runtime 以 `ddl-auto=validate` 校验，不自建迁移，避免 schema 双写漂移。Runtime 执行审计经共享 `AuditRecordRepository` 落 `audit_records` 表（不再走内存回退）。Runtime 启动 + 定时从 `http_targets` 表同步 HTTP 目标到注册表，打通 Console CRUD 到 Runtime 生效链路。

## Runtime 拒绝规则

Runtime 必须拒绝：

- 未签名发布包；
- 使用未知 key ID 签名的发布包；
- 属于其他环境的发布包；
- Runtime 版本不兼容的发布包；
- content、metadata 或 policy hash 不匹配的发布包；
- 脚本状态不在允许发布状态中的发布包；
- 缺少资源权限元数据的发布包；
- 未授权 Console 身份推送的发布包。

## 执行语义（切片 34）

Runtime 执行端点（`/api/query`、`/api/repair/*`、`/api/adapter/execute`）采用**脚本引用模式**：

- 请求只携带 `scriptId`（查询/修复）或 `scriptId` + `body`（适配），不携带裸 SQL/裸目标。
- `ScriptResolver` 按 `scriptId` 从激活包 manifest 查 `ScriptEntry`、从 `pkg.scripts()` 取脚本内容、校验 contentHash，返回解析后的内容。
- 执行内容来自已签名发布包：查询/修复执行包内脚本内容（SQL），适配执行包内脚本定义的 `{targetId,path,method}` + 请求 body。
- 缺 `scriptId` 的裸 SQL/裸目标请求一律 400 拒绝。magic-editor 保留调试能力，但生产执行必须走发布链路（双轨决策方向 A）。

## 回滚与下线

下线（切片 33-d）：Console 向 `POST /api/packages/deactivate` 推送下线指令（受共享密钥认证保护）。Runtime 将 ACTIVE 行置 INACTIVE、清空激活缓存、落 `PACKAGE_DEACTIVATED` 关键审计。下线后 `getActivePackage()` 返回 null，查询/修复/适配端点据此拒绝执行（"没有已激活的发布包"）。下线后重新上线走发布链路：重新推送已签名发布包经 `verifyAndActivate` 验签激活。

回滚必须表现为一次已签名发布动作。

Runtime 不应静默从磁盘选择旧文件。它应激活上一个已签名发布包或已签名回滚包，并记录原因和操作人。
