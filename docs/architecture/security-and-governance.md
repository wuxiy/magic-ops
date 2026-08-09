# 安全与治理

## 核心原则

1. 生产 Runtime 只读。
2. 平台签名，Runtime 验签。
3. 脚本引用 key ID，不包含原始密钥。
4. 资源权限必须在 Runtime 执行，不能只依赖 Console。
5. 高风险动作必须审批。
6. 审计必须覆盖成功和失败路径。
7. 敏感数据进入持久日志或审计存储前必须脱敏。

## 认证

可支持身份来源：

- local accounts;
- LDAP;
- OAuth2/OIDC;
- CAS;
- existing intranet SSO.

第一版实现应从能支持角色和权限校验的最小可用认证路径开始。

第一版认证采用 Spring Security Basic。后续再评估 form login、JWT、LDAP、OIDC 或既有内网 SSO。

## 授权

使用 RBAC + 资源权限。

资源包括：

- project;
- environment;
- script/API;
- data source;
- table;
- HTTP target;
- HTTP path;
- key reference;
- publish package;
- 第二阶段诊断命令。

## SQL Guard 规则

默认策略：

| SQL 类型 | 默认策略 |
|---|---|
| SELECT | 允许，但限制结果大小 |
| INSERT | 需要授权 |
| UPDATE | 需要授权、WHERE、影响行数限制 |
| DELETE | 高风险，需要审批，必须有 WHERE |
| TRUNCATE | 默认拒绝 |
| DROP | 默认拒绝 |
| ALTER | 默认拒绝 |
| CREATE | 默认拒绝 |
| GRANT | 默认拒绝 |

数据修复在审批前必须 dry-run 并生成影响范围报告。

第一版数据修复约束：

- 目标业务库第一版为达梦（H2/PostgreSQL 用于开发与验证）；
- 不允许自由 UPDATE/DELETE；
- 只允许受限 SQL 模板，或单表带主键/WHERE 的写操作；
- dry-run 第一版只做 SQL 解析、权限校验、危险语句拦截和影响范围估算；
- 自第五阶段切片 30 起，修复在事务内执行：影响行数超过 `MAX_AFFECTED_ROWS`（100）即回滚并落阻断审计；
- SQL Guard 基于 JSQLParser 解析树分类（切片 30），不再使用字符串前缀匹配；
- 达梦方言行为仍需抽样验证（见第五阶段遗留事项）。

## 加解密与密钥安全

- 平台管理 key references。
- 脚本使用 `keyId`。
- Runtime 或配置的 key service 解析真实密钥。
- 日志和审计不得记录原始 key、token、password、database URL、private key 或解密后的敏感 payload。
- 第一阶段加解密支持围绕接口适配需求：SM4、AES、RSA、HMAC 和脱敏。

签名密钥装配（第五阶段切片 32 起）：

- 装配优先级：KeyStore 持久化密钥（JDK 标准 `java.security.KeyStore`，JKS/PKCS12，`magicops.sign.keystore.*`）→ 环境变量（`MAGICOPS_PRIVATE_KEY/PUBLIC_KEY`）→ 临时密钥（仅 `magicops.sign.allow-ephemeral-keys=true`）；
- prod profile 强制 `allow-ephemeral-keys: false`：无任何持久化密钥时启动 fail-fast，错误信息附修复路径；
- KeyStore 模式下全部别名进入信任集，支持密钥轮换过渡期内新旧 keyId 共存验签；
- 仓库内不落真实凭据：compose 一律 `${VAR:?}` 必填引用，凭据经 `.env`（不入库）注入，仓库只保留 `.env.example` 模板。

## 审计可靠性

审计类型：

- 操作审计；
- 执行审计；
- 发布审计；
- 审批审计；
- 安全审计；
- 未来诊断审计。

高风险操作在关键审计写入失败时必须被阻断。

只有当关键审计信封已经持久化后，大体量执行详情才可以异步写入。

审计失败分级：

- 发布、审批、签名、Runtime 加载、数据修复：关键审计失败即阻断；
- 动态查询：关键执行审计失败默认阻断，后续引入本地可靠队列后才可放宽；
- HTTP 接口适配：外部写入或推送类调用审计失败即阻断，纯查询类调用按风险等级处理；
- 第一版不实现本地可靠队列，审计存储短暂不可用时不做静默降级。

## 敏感数据脱敏

至少脱敏：

- ID card number;
- phone number;
- medical insurance card number;
- medical card number;
- patient name;
- address;
- token;
- password;
- secret;
- private key;
- 数据库连接串。

## 网络边界

- Console 只部署在管理网或运维网。
- Runtime 部署在业务网或生产网。
- Console 通过认证通道向 Runtime 推送已签名发布包。自第五阶段切片 32 起，收包端点强制共享密钥认证（`X-MagicOps-Push-Secret`，环境变量 `MAGICOPS_RUNTIME_SHARED_SECRET`）：缺失或不匹配返回 401；prod 未配置密钥时 fail-closed 拒绝所有推送。
- Runtime 不得暴露编辑端点。
- Arthas Tunnel 不得公网暴露，且属于第二阶段。
