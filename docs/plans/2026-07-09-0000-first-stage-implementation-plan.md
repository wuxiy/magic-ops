# 第一阶段实施计划

## 状态

Closed。所有 5 个切片（0-4）已完成并通过验证。

## 目标

完成 MagicOps 项目脚手架，并跑通第一阶段三个受控闭环：

1. 动态查询；
2. 数据修复；
3. HTTP 接口适配。

最低闭环为：

```text
创建脚本 -> 调试 -> 提交 -> 审批 -> 签名 -> 推送 -> Runtime 验签加载 -> 执行 -> 审计
```

第一版接受薄 Console 和 API/测试驱动闭环。

## 已确认关键决策

- `magic-api` 通过 `git subtree` 引入或维护。
- Maven `groupId` 使用 `top.cywu`。
- Java package 默认使用 `top.cywu.magicops`。
- 第一版采用多应用形态，至少拆分 Console 与 Runtime。
- 平台元数据库使用 PostgreSQL。
- 第一版业务数据源只支持达梦数据库。
- 认证使用 Spring Security Basic。
- Console 通过 REST 主动推送发布包到 Runtime。
- Console 与 Runtime 第一版按同网段部署假设设计。
- 平台私钥第一版来自环境变量。
- 发布包签名使用 canonical manifest + canonical metadata + normalized scripts bytes。
- hash 使用 SHA-256，签名算法使用 SHA256withRSA。
- 数据修复不支持自由 UPDATE/DELETE，只支持受限 SQL 模板或单表带主键/WHERE 的写操作。
- dry-run 第一版只做解析、权限校验、危险语句拦截和影响范围估算。
- 关键审计失败按已定义分级阻断。

## 实施切片

### 切片 0：仓库脚手架

目标：

- 建立 Maven 多模块项目；
- 引入 `magic-api` subtree；
- 建立 Console 和 Runtime 两个可独立启动应用；
- 建立真实构建与测试命令。

产物：

- 根 `pom.xml`；
- `magicops-console`；
- `magicops-runtime`；
- `magicops-core`；
- `magicops-governance`；
- `magicops-audit`；
- `magicops-sign`；
- `magicops-sql-guard`；
- `magicops-http`；
- `magicops-crypto`；
- `docs/context/project-context.md` 中真实验证命令。

关闭标准：

- Console 应用可启动；
- Runtime 应用可启动；
- Maven 编译命令成功；
- 至少一个基础测试命令成功。

### 切片 1：脚本生命周期和元数据模型

目标：

- 建立 Script、Draft、Script Version、Approval、Publish Package 元模型；
- 建立状态机；
- 建立 PostgreSQL 元数据库迁移。

产物：

- 元数据库 schema/migration；
- 生命周期服务；
- 最小 Console API；
- 状态迁移测试。

关闭标准：

- 脚本可以从 DRAFT 走到 APPROVED；
- 非法状态迁移被拒绝；
- 审批记录可审计。

### 切片 2：发布包签名和 Runtime 验签

目标：

- 实现 canonical 发布包生成；
- 使用环境变量私钥签名；
- Runtime 使用公钥验签；
- Console 通过 REST 推送发布包到 Runtime。

产物：

- canonical JSON 和脚本规范化实现；
- hash 和签名服务；
- Runtime 发布包接收 API；
- Runtime 验签与原子激活流程；
- 签名和验签测试。

关闭标准：

- Runtime 接受合法签名包；
- Runtime 拒绝 hash 不匹配包；
- Runtime 拒绝环境不匹配包；
- Runtime 拒绝未知 key ID 包；
- Runtime 加载审计成功写入。

### 切片 3：动态查询闭环

目标：

- 支持查询脚本发布到 Runtime；
- Runtime 执行查询；
- SQL 默认只读；
- 执行审计落库。

产物：

- 查询脚本 API；
- 达梦查询数据源配置；
- SQL Guard 只读校验；
- Runtime 动态路由执行；
- 执行审计。

关闭标准：

- 查询脚本能完成完整闭环；
- 非 SELECT 默认拒绝；
- 查询结果大小受限；
- 审计记录包含 trace ID、script ID、版本、SQL 摘要、结果大小、耗时、成功/失败。

### 切片 4：受控数据修复闭环

目标：

- 支持修复脚本共用生命周期；
- 支持 dry-run；
- 支持审批后执行；
- 支持回滚意图记录；
- 高风险审计失败阻断。

产物：

- 修复脚本风险声明；
- 受限 SQL 模板或单表主键/WHERE 写操作；
- dry-run 影响范围估算；
- 修复执行 API；
- 执行报告和回滚记录。

关闭标准：

- 无 dry-run 不允许提交审批；
- 无审批不允许发布执行；
- 自由 UPDATE/DELETE 被拒绝；
- dry-run 生成影响范围报告；
- 审计失败时修复执行被阻断。

### 切片 5：HTTP 接口适配闭环

目标：

- 注册 HTTP 目标系统；
- 限制 allowlist 路径；
- 支持字段映射、签名/加密和响应转换；
- 记录外部调用审计。

产物：

- HTTP target 元数据模型；
- Runtime HTTP client 模块；
- CryptoModule 最小能力；
- 外部调用审计；
- 接口适配脚本示例。

关闭标准：

- 脚本只能调用已注册目标；
- 非 allowlist 路径被拒绝；
- 请求可使用配置签名/加密；
- trace ID 可透传；
- 外部写/推送类调用审计失败时阻断。

## 验证策略

实现后必须更新 `docs/context/project-context.md` 中的命令：

- 编译检查；
- 单元测试；
- 集成测试；
- Console 本地启动；
- Runtime 本地启动；
- 端到端闭环测试。

每个切片结束时更新：

- `docs/logs/2026/07-09.md` 或当天日志；
- `docs/testing/known-good-baselines.md`，仅当达到有意义 known-good 状态时更新；
- 相关 owner docs。

## 风险和约束

- `git subtree` 引入上游 magic-api 需要先确认上游仓库 URL、分支和许可证兼容。
- 达梦 JDBC 驱动可能不在公共 Maven 仓库，需要提前确认内网制品来源。
- Basic 认证只适合作为第一版最小认证，不代表最终生产身份方案。
- 环境变量私钥只适合第一版，后续必须迁移到更受控的密钥管理方案。
- 第一版不实现本地可靠审计队列，因此关键审计存储不可用时会导致阻断。

## 暂不做

- Arthas 诊断中心；
- Redis / MQ 完整治理；
- 完整 UI 重写；
- 多租户；
- 离线发布包主流程；
- 达梦事务内执行后 rollback dry-run。
