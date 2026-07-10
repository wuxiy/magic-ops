# 功能清单

## 第一阶段功能

| 功能 | 状态 | 归属 | 说明 |
|---|---|---|---|
| 项目脚手架 | **done** | architecture | 基于 magic-api fork 的 MagicOps 单仓库结构，Spring Boot 3.3.5 + JDK 21。 |
| 动态查询 API | **done** | runtime | 查询优先的动态 HTTP API，SQL 默认只读，真实 JDBC 执行。 |
| 受控数据修复 | **done** | runtime | 共用脚本生命周期；dry-run、审批、SQL Guard 写约束、审计和回滚记录。 |
| HTTP 接口适配 | **done** | runtime | 注册目标系统、路径 allowlist、AES/HMAC 加解密、脱敏和审计。 |
| 脚本生命周期 | **done** | governance + console | 12 种状态，DRAFT → APPROVED → PUBLISHED → DISABLED/ROLLED_BACK。 |
| 审批流 | **done** | console | 高风险脚本和写操作必须审批，关键审计阻断。 |
| 操作审计 | **done** | audit | Console 操作持久化到 PostgreSQL，脱敏后存储。 |
| 执行审计 | **done** | runtime | Runtime 执行和外部操作审计，含 trace ID/SQL 摘要/耗时。 |
| 签名发布 | **done** | sign | SHA-256 + SHA256withRSA，canonical JSON，环境/keyId 校验。 |
| Runtime 只读模式 | **done** | runtime | 生产 Runtime 不可编辑，拒绝未签名/环境不匹配/未知 keyId 包。 |
| SQL Guard | **done** | sql-guard | SQL 分类、只读策略、修复约束、危险语句拦截、结果大小限制。 |
| CryptoModule 最小集 | **done** | crypto | AES-GCM、HMAC-SHA256、SM4 国密、敏感数据脱敏。 |

## 第二阶段功能

| 功能 | 状态 | 归属 | 说明 |
|---|---|---|---|
| 元数据持久化 | **done** | console | Flyway V1-V6 迁移，19 张表，15 个 JPA 实体。 |
| 用户管理与认证 | **done** | console | BCrypt + UserDetailsService + DataInitializer。 |
| RBAC 权限控制 | **done** | console | 7 种角色 + 17 种权限 + @PreAuthorize 方法级授权。 |
| 资源级权限 | **done** | console | @ResourcePermission + AOP + SpEL 资源 ID 提取。 |
| 审计持久化 | **done** | audit | JPA 持久化 + 脱敏 + 分页查询 API。 |
| 密钥轮换 | **done** | sign | KeyRotationService + KeyManagementController。 |
| Docker 部署 | **done** | docker | Dockerfile + docker-compose.yml + prod profile。 |
| Runtime 拦截器流水线 | **done** | runtime | TraceId + 限流 + 响应脱敏 Filter 链。 |

## 暂缓功能

| 功能 | 目标阶段 | 说明 |
|---|---|---|
| Arthas 诊断中心 | 已设计 | 需求和架构文档已完成，模块骨架已创建。 |
| magic-api Fork 整合 | 切片 16 | javax → jakarta 迁移 + 脚本引擎接入主构建。 |
| Redis 完整治理 | 后续 | 当前不需要完整 Redis 操作治理。 |
| MQ 完整治理 | 后续 | 当前不需要完整 MQ 操作治理。 |
| 多租户管理 | 后续 | 等项目级隔离需求被验证后再做。 |
| 离线发布包作为主路径 | 后续 | 当前使用在线推送。 |
| 完整 UI 重写 | 后续 | 当前复用或轻量扩展 magic-editor。 |
| 报表/表单设计器 | 范围外 | 不属于 MagicOps 核心定位。 |

## 功能边界

动态查询 API 默认只读，不应成为隐藏的数据修复路径。

数据修复与动态 API 使用相同脚本生命周期，但必须通过风险等级、资源、审批策略、SQL Guard 模式和执行审计区分。

接口适配可以做转换、签名、加密并调用已注册 HTTP 目标，但不得变成任意外部网络访问。

Arthas 诊断不得混入 Runtime 执行路径。它需要独立的会话、命令、输出和安全模型。
