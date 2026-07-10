# 第二阶段实施计划：生产可用

## 状态

Closed。所有切片已完成并通过验证。

## 目标

将 MagicOps 从第一阶段原型推进到**生产可用**状态。解决以下核心差距：

1. 所有数据持久化到 PostgreSQL（元数据 + 审计）；
2. 用户管理和 RBAC（7 种角色、资源级权限）；
3. 审计持久化和脱敏；
4. 密钥管理升级；
5. 生产配置和部署就绪。

关闭标准：MagicOps 可以在 PostgreSQL 环境下启动、登录、完成完整的脚本生命周期闭环，审计记录持久化且可查询，不同角色用户权限隔离。

## 已确认关键决策

- 元数据库和审计库共用 PostgreSQL，使用独立 schema 或表前缀区分。
- 第一版用户管理使用本地账户，后续评估 LDAP/OIDC/SSO。
- RBAC 采用用户-角色-权限三层模型，权限覆盖资源维度。
- 审计脱敏使用 `magicops-crypto` 的 `CryptoService.mask()`。
- 密钥管理第一版使用 Java KeyStore（JKS），不依赖外部 KMS。
- 生产配置使用 Spring Profile 区分 `dev`/`prod`。
- Flyway 管理所有 schema 变更，生产环境 `ddl-auto=validate`。

## 实施切片

### 切片 6：元数据持久化

目标：

- 完善 schema（users、roles、permissions、projects、environments、data_sources、key_references、publish_packages）；
- Console 和 Runtime 接入真实 PostgreSQL；
- Flyway 管理 schema 迁移，开发和生产统一流程；
- JPA 实体与 migration 保持一致。

产物：

- `V2__users_roles_permissions.sql`；
- `V3__projects_environments.sql`；
- `V4__data_sources_key_references.sql`；
- `V5__publish_packages.sql`；
- `application-dev.yml` 和 `application-prod.yml`；
- 所有 JPA 实体同步更新；
- Repository 层接入新表。

关闭标准：

- Flyway 迁移在 PostgreSQL 上成功执行；
- 所有 JPA 实体与新 schema 一致；
- `mvn test` 使用 H2（兼容模式）通过；
- Console 在 PostgreSQL 上启动并持久化数据。

### 切片 7：用户管理与认证

目标：

- 实现 User、Role、UserRole 实体和 Repository；
- 实现 UserDetailsService 接入 Spring Security；
- 支持用户创建、角色分配；
- 替换静态 admin/admin 为数据库用户；
- 提供用户管理 API。

产物：

- `UserEntity`、`RoleEntity`、`UserRoleEntity`；
- `MagicOpsUserDetailsService`；
- `UserController`（CRUD + 角色分配）；
- 初始化数据脚本（默认管理员 + 默认角色）；
- 认证测试。

关闭标准：

- 通过数据库用户登录 Console；
- 不存在默认 admin/admin；
- 用户创建和角色分配 API 可用；
- 7 种角色全部预置。

### 切片 8：RBAC 权限控制

目标：

- 实现 Permission 和 RolePermission 实体；
- 启用 Spring Security 方法级授权（`@PreAuthorize`）；
- 在关键 API 上添加权限检查；
- 实现资源级权限（项目、数据源、HTTP 目标）。

产物：

- `PermissionEntity`、`RolePermissionEntity`；
- `ResourcePermissionEntity`（用户/角色对特定资源的权限）；
- 各 Controller 添加 `@PreAuthorize` 注解；
- 权限校验拦截器；
- RBAC 测试（角色隔离验证）。

关闭标准：

- 开发人员不能审批脚本；
- 审批人员不能创建脚本；
- 安全审计员不能修改脚本；
- 受保护操作需要对应角色；
- 资源权限校验生效。

### 切片 9：审计持久化与脱敏

目标：

- AuditService 接入 PostgreSQL `audit_records` 表；
- 审计写入使用独立事务（关键审计失败阻断）；
- 敏感数据脱敏后写入审计；
- 审计查询 API（分页、按实体/事件/时间过滤）。

产物：

- `AuditRecordEntity` + `AuditRecordRepository`；
- `PersistentAuditService`（替代内存实现）；
- `AuditMaskingService`（脱敏逻辑）；
- `AuditController`（查询 API）；
- 审计持久化测试；
- 脱敏测试。

关闭标准：

- 审计记录在 PostgreSQL 中可查；
- 重启后审计记录不丢失；
- 关键审计写入失败时阻断执行；
- 身份证号、手机号、密码等敏感字段脱敏；
- 审计查询 API 支持分页和过滤。

### 切片 10：密钥管理升级

目标：

- KeyProvider 支持 Java KeyStore（JKS）文件；
- 支持多个 keyId 并存；
- 支持密钥轮换（新旧 keyId 共存过渡）；
- 移除环境变量明文私钥；
- CryptoService 支持 SM4 加解密（国密算法）。

产物：

- `KeyStoreKeyProvider`（JKS 实现）；
- `KeyRotationService`（轮换逻辑）；
- `V6__key_references.sql`；
- SM4 加解密实现；
- 密钥管理 API；
- 密钥轮换测试。

关闭标准：

- 密钥从 JKS 文件加载，不从环境变量读取；
- 多个 keyId 可并存，Runtime 信任所有有效 keyId；
- 密钥轮换时旧签名包仍可通过验证；
- SM4 加解密往返测试通过。

### 切片 11：生产就绪与端到端验证

目标：

- 生产配置 profile 完善（PostgreSQL、JKS、日志级别、连接池）；
- Flyway 在 PostgreSQL 上完整执行；
- 端到端闭环在 PostgreSQL 环境下验证；
- 数据源管理接入真实 PostgreSQL 数据源配置；
- Backlog 状态同步更新。

产物：

- `application-prod.yml` 完善；
- Docker Compose（PostgreSQL + Console + Runtime）；
- E2E 测试在 PostgreSQL 环境下通过；
- Backlog 状态更新；
- project-context.md 更新。

关闭标准：

- Docker Compose 一键启动；
- E2E 10 步在 PostgreSQL 上全部通过；
- 审计记录持久化验证（重启后仍存在）；
- RBAC 权限隔离验证通过。

## 验证策略

每个切片结束时更新：

- `docs/logs/` 当天日志；
- `docs/testing/known-good-baselines.md`；
- 相关 owner docs。

切片 11 结束时执行完整 E2E 回归。

## 风险和约束

- PostgreSQL 实例需要提前准备（开发可用 Docker）。
- Flyway 从 H2 切换到 PostgreSQL 可能有方言差异，需要测试。
- JKS 文件管理增加了部署复杂度，需要提供生成工具。
- SM4 算法依赖 BouncyCastle，需要确认许可证兼容。
- RBAC 模型设计需要与团队确认权限粒度。

## 暂不做

- Arthas 诊断中心（属于第三阶段）；
- LDAP/OIDC/SSO 认证集成（后续评估）；
- Redis/MQ 治理；
- 完整 UI 重写；
- 多租户；
- 离线发布包主流程。
