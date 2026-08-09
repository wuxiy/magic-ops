# 已知良好基线

## 当前基线

截至 2026-08-09 复核的已知良好状态：

**第一阶段（切片 0-5）**：项目脚手架、脚本生命周期、签名验签、动态查询、数据修复、HTTP 适配、E2E 10 步闭环。

**第二阶段（切片 6-11）**：元数据持久化（Flyway V1-V6，19 张表）、用户管理与 BCrypt 认证、RBAC 7 角色 17 权限、审计 PostgreSQL 持久化与脱敏、密钥管理 JKS + SM4 国密、Docker Compose 部署配置。

**第三阶段（切片 12-15）**：Docker 生产配置、资源级权限 @ResourcePermission + AOP、Runtime 拦截器流水线（TraceId + 限流 + 响应脱敏）、密钥轮换服务。

**第三阶段（切片 17）**：文档全量同步、Arthas 诊断中心需求与架构设计、magicops-diagnosis 模块骨架。

**第五阶段（切片 30-35）**：SQL Guard 解析器化与数据修复事务化强制、身份与追责、密钥与凭据固化、Runtime 闭环（激活包持久化与启动重载、Runtime 审计持久化、HttpTarget 从 DB 同步、发布下线端点）、执行语义对齐（脚本引用模式，拒绝裸 SQL/裸目标调用）、可观测（actuator+Prometheus+结构化日志+Console 限流/traceId）+ CI。

**测试**：296 个单元测试全部通过（2026-08-09 切片 35 关闭复核）。

## 最近完整验证

2026-08-09：切片 35 可观测 + CI

- `mvn test`：通过（296 个测试，0 失败，12 模块全绿），较切片 34 基线 292 新增 4 例（ConsoleActuatorTest 2 + RuntimeActuatorTest 2）。
- actuator + micrometer-registry-prometheus 接入 Console/Runtime：`/actuator/health`、`/actuator/prometheus`、`/actuator/metrics` 匿名可访问（需显式 `management.prometheus.metrics.export.enabled=true`）。
- 结构化日志：logback-spring.xml 含 traceId MDC（Console+Runtime）。
- Console 补 TraceIdFilter + RateLimitFilter（与 Runtime 行为一致）。
- GitHub Actions CI：push/PR 触发 `mvn -B test`，失败上传 surefire 报告。
- Docker E2E on PostgreSQL 16/16 通过（新增 Step 16 actuator/health）。

2026-08-09：第五阶段切片 34（执行语义对齐）

- `mvn test`：通过（292 个测试，0 失败，12 模块全绿），较切片 33 基线 285 新增 7 例。
  - `ScriptResolverTest` 4 例（scriptId 解析成功、未知 scriptId 拒绝、空 scriptId 拒绝、contentHash 不匹配拒绝）。
  - `QueryExecutionContractTest` 3 例（脚本引用执行成功、裸 SQL 拒绝、未知 scriptId 拒绝）。
  - 既有 34 例服务单元测试（Query/Repair/Adapter Service）零改动通过。
- E2E 闭环 12 步全部通过：Step 9 脚本引用执行查询 rows=1，Step 10 裸 SQL 请求被拒（缺 scriptId）。
- 代码级实现：`ScriptResolver` 按 scriptId 从激活包解析内容并校验 contentHash；`QueryController`/`RepairController`/`HttpAdapterController` 改为脚本引用模式，缺 scriptId 返回 400。
- 既有调用方影响：本计划内 Console（PushService）与 Runtime 插件为唯一已知调用方，须改用 scriptId 引用。

2026-08-09：第五阶段切片 33（Runtime 闭环与可靠性）

- `mvn test`：通过（285 个测试，0 失败，12 模块全绿），较切片 32 基线 277 新增 8 例。
  - `RuntimeAuditPersistenceTest` 2 例（AuditRecordRepository 装配非 null、关键审计落库可查回）。
  - `ActivePackagePersistenceTest` 2 例（激活包落库 + 重启重载验签、篡改 payload 重载验签失败置 INACTIVE 不激活）。
  - `HttpTargetSyncTest` 2 例（DB enabled 目标同步到注册表、disabled 目标不同步）。
  - `PackageDeactivateTest` 2 例（下线后 getActivePackage 返回 null + 关键审计、未认证 401）。
  - 既有 `PackageVerificationServiceTest`（5）、`PackageReceiveAuthIntegrationTest`（4）、`HttpTargetRegistryTest`（8）无回归。
- 代码级实现：Runtime 装配 `@EntityScan`/`@EnableJpaRepositories`/`@EnableScheduling`；`active_packages` 表（V10）+ `PackageVerificationService` 落库与 `@PostConstruct` 重载验签；`HttpTargetSyncService` 启动+定时从 `http_targets` 同步；`deactivate()` 下线端点受共享密钥保护。
- 共享 PostgreSQL 拓扑：Console 经 Flyway 建表，Runtime `ddl-auto=validate` 校验，不自建迁移，避免 schema 双写漂移。
- 仍待 Docker E2E on PostgreSQL 验证（含重启重载、审计持久化、HTTP 目标同步），随切片 34 收口执行。

2026-08-09：生产准入复核（独立代码级重审，非仅依据文档）

- `mvn test`：通过（277 个测试，0 失败，12 模块全绿），独立重跑确认切片 32 基线成立。
- 代码级复核切片 30-32 强制项均已真实落地（非"声明有、强制无"）：
  - `SqlGuardService` 基于 JSQLParser 解析树分类（非字符串前缀），`TRUNCATE/DROP/ALTER/CREATE/GRANT` 恒拒绝。
  - `RepairExecutionService` 审批凭据校验 + contentHash 内容绑定 + 表级白名单 + 事务化 `MAX_AFFECTED_ROWS=100` 超限回滚。
  - `ScriptLifecycleService:167` 提交人≠审批人强制（违例 403）；actor 绑定登录用户。
  - `KeyProvider` prod fail-fast（禁临时密钥）；`PushSecretAuthenticationFilter` 收包端点 401。
- 硬阻断确认（切片 33/34 未实现）：激活包仅内存（`PackageVerificationService:44` AtomicReference，重启即丢）、Runtime 执行审计落内存 fallback、`HttpTargetRegistry` 生产不注册、无回滚/下线端点；query/adapter 仍执行请求体内容而非签名包脚本。
- 修正：前端构建产物现已正确 gitignore（`static/console/` 跟踪数为 0），原"产物直接提交 git"不再成立；但前端仍未纳入 Maven 构建。
- Docker Compose E2E on PostgreSQL 已通过（2026-08-09，15 步）；仍不完整/未验证：达梦方言未抽样验证；无 CI；集成测试全 H2。

2026-08-08：第五阶段切片 32（密钥与凭据固化）

- `mvn test`：通过（277 个测试，0 失败，较切片 31 新增 24 例）
  - KeyProviderTest 10 例（prod fail-fast、环境变量密钥、JDK keytool 生成 JKS 的多 keyId 装配/覆盖/回退/错误密码）
  - PushSecretAuthenticationFilterTest 7 例（401 拒绝/放行/GET 不受影响）
  - PackageReceiveAuthIntegrationTest 4 例（真实 Spring Security 过滤器链）
  - PushServiceTest 3 例（JDK HttpServer 验证共享密钥头）
- prod fail-fast 真实 jar 启动验证：缺密钥时 EXIT=1，错误信息含两种修复路径。
- E2E 闭环测试 12 步全部通过（新增 Step 5c 未认证推送 401 负例；推送链路携带共享密钥）。
- 凭据出库：根 compose 全部密码改为必填环境变量引用，新增根与 deploy 的 `.env.example`；`docker compose config` 校验通过。
- 实现原则：仅用 JDK 标准 KeyStore 与 Spring Security/Boot 标准机制，未新增第三方依赖。

2026-08-08：第五阶段切片 31（身份与追责）

- `mvn test`：通过（253 个测试，0 失败，较切片 30 新增 10 例）
  - GovernanceActorTest 6 例（审计 actor 为登录用户、自审自批 403、越权 403、角色分配/回收关键审计与 RBAC 负例）
  - DataInitializerTest 4 例（prod 不产生测试账号、缺密码不初始化）
- E2E 闭环测试 11 步全部通过（新增自审自批拒绝负例；提交/审批改为双账号流程）。
- 附带修复既有缺陷：RBAC 越权原返回 500（AccessDeniedException 被通用异常处理器吞掉），现正确返回 403。

2026-08-07：第五阶段切片 30（SQL Guard 与数据修复强制化）

- `mvn test`：通过（243 个测试，0 失败，较前次基线新增 34 例）
  - SqlGuardServiceTest 46 例（JSQLParser 解析树方案，含注释混淆、多语句、内联注释写操作等绕过负例）
  - RepairExecutionServiceTest 16 例（审批凭据校验、内容 hash 绑定、表级白名单、超行数上限事务回滚）
  - QueryExecutionServiceTest 10 例（表级白名单，含子查询越权拒绝）
  - PackageBuildServiceTest 8 例（治理凭据嵌入与签名保护，fail-closed 负例）
- E2E 闭环测试 10 步全部通过（修复 Runtime 启动回归后，含治理凭据的新发布链路验签加载与执行正常）。
- 当前基线整体升级待第五阶段全部切片关闭后执行（见 `docs/plans/2026-08-07-0000-fifth-stage-production-gate.md`）。

2026-07-11：切片 12-15 功能完善

- `mvn test`：通过（148 个测试，0 失败）
  - 密钥轮换测试 7 个（初始化/轮换/多轮换共存/旧密钥可用/移除/异常）
  - RBAC 全覆盖测试 10 个（7 种角色权限隔离 + 交叉验证）
  - 前序测试 131 个继续通过

2026-07-11：切片 9-11 审计持久化 + 密钥管理 + Docker

- `mvn test`：通过（130 个测试，0 失败）
  - 审计服务测试 8 个（写入/查询/脱敏/关键审计）
  - SM4 国密测试 5 个（往返/空串/长文/错误密钥/不同 IV）

2026-07-10：端到端集成测试

- E2E 闭环测试：10 步全部通过

## 待验证

- magic-api fork 代码整合（javax → jakarta 迁移，切片 16）；
- Docker Compose E2E on PostgreSQL 已通过（2026-08-09，15 步：含激活包重启重载、审计持久化、脚本引用执行、裸 SQL 拒绝）；
- 达梦数据库真实环境验证。
