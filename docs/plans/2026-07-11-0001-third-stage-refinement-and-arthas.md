# 第三阶段实施计划：功能完善与 Arthas 诊断

## 状态

Closed。所有切片已完成并通过验证。

## 目标

补全第二阶段遗留差距，实现 Runtime 拦截器流水线，集成 magic-api fork，启动 Arthas 诊断中心架构设计。

关闭标准：Docker Compose 可启动、Runtime 生产配置完整、资源级权限生效、拦截器流水线覆盖核心路径、magic-api fork 参与主构建。

## 已确认关键决策

- Docker 镜像使用 Eclipse Temurin JDK 21 Alpine 基础镜像。
- Runtime 拦截器流水线使用 Spring HandlerInterceptor + Filter 组合实现。
- 资源级权限通过 AOP 切面 + ResourcePermissionService 实现。
- magic-api fork 整合分两步：先 javax → jakarta 自动转换，再逐模块接入主构建。
- Arthas 诊断中心第一阶段只做架构设计和接口定义，不做完整实现。
- KeyRotationService 支持新旧 keyId 共存过渡期。

## 实施切片

### 切片 12：Phase 2 收尾 — Docker 与生产配置

目标：

- 创建 Console 和 Runtime Dockerfile；
- Runtime 添加 `application-prod.yml`（PostgreSQL + 环境变量驱动）；
- `docker-compose up` 可一键启动完整环境；
- E2E 测试在 Docker Compose 环境下通过。

产物：

- `docker/Dockerfile.console`；
- `docker/Dockerfile.runtime`；
- `magicops-runtime/src/main/resources/application-prod.yml`；
- E2E Docker 测试脚本。

关闭标准：

- `docker-compose up` 成功启动 PostgreSQL + Console + Runtime；
- E2E 10 步在 Docker 环境全部通过；
- Console 和 Runtime 均可通过 `prod` profile 连接 PostgreSQL。

### 切片 13：资源级权限与权限补全

目标：

- 实现 ResourcePermissionService（检查用户/角色对特定资源的权限）；
- 实现 @ResourcePermission 自定义注解 + AOP 切面；
- 在关键操作（脚本发布、数据源查询、HTTP 目标调用）上启用资源级权限检查；
- 补全 RBAC 测试覆盖全部 7 种角色。

产物：

- `ResourcePermissionService`；
- `@ResourcePermission` 注解 + `ResourcePermissionAspect`；
- `RbacFullCoverageTest`（7 种角色隔离验证）。

关闭标准：

- 开发人员只能在所属项目内创建脚本；
- 安全审计员不能修改脚本、审批或密钥；
- 运维人员不能创建脚本或审批；
- 只读观察者只能查看审计，无任何写操作权限。

### 切片 14：Runtime 拦截器流水线

目标：

- 实现统一的 Runtime 请求处理流水线（来自 `module-boundaries.md`）；
- 覆盖：TraceId 注入 → 认证 → 权限 → 包验证 → 限流 → 脱敏 → 脚本解析 → SQL Guard / HTTP Guard → 执行 → 审计；
- 替换现有分散的权限/审计检查为统一流水线。

产物：

- `RuntimeInterceptorChain`；
- `TraceIdFilter`；
- `RateLimitFilter`（基于令牌桶）；
- `ResponseMaskingFilter`；
- 流水线集成测试。

关闭标准：

- 所有 Runtime 请求经过统一流水线；
- trace ID 在每个请求中注入并透传；
- 限流生效（超出阈值返回 429）；
- 响应中的敏感数据被脱敏。

### 切片 15：密钥轮换与 Spring Bean 集成

目标：

- 实现 KeyRotationService（新旧 keyId 共存、轮换触发、旧包验证）；
- SM4CryptoService 和 KeyStoreKeyProvider 注册为 Spring Bean；
- 密钥管理 REST API（查看/注册/轮换）；
- Profile 驱动配置（dev 用环境变量，prod 用 JKS）。

产物：

- `KeyRotationService`；
- `KeyManagementController`；
- `CryptoAutoConfiguration`（Spring Boot 自动配置）；
- 密钥轮换测试。

关闭标准：

- 密钥轮换后旧签名包仍可通过验证（新旧 keyId 共存）；
- SM4 和 JKS 在 Spring 上下文中可用；
- 密钥管理 API 可查询已注册 keyId 列表。

### 切片 16：magic-api Fork 整合

目标：

- 将 `magic-api-source` 从 javax 命名空间迁移到 jakarta；
- 将核心模块接入主 Maven 构建；
- 验证 magic-api 脚本引擎与 MagicOps 治理层集成；
- Runtime 使用 magic-api 引擎执行动态 API（替换自定义 JDBC 执行）。

产物：

- javax → jakarta 迁移补丁/脚本；
- `magicops-core` 集成 magic-api 脚本引擎；
- Runtime 动态 API 路由使用 magic-api 引擎；
- 集成测试验证脚本引擎执行。

关闭标准：

- `mvn compile` 包含 magic-api 模块且通过；
- Runtime 可通过 magic-api 引擎执行动态 API 脚本；
- 现有 SQL Guard 和审计与 magic-api 引擎兼容。

### 切片 17：文档同步与 Arthas 架构设计

目标：

- 更新所有过时文档（backlog、feature inventory、project-context、known-good baselines）；
- 编写 Arthas 诊断中心需求文档和架构设计（第二阶段功能，本阶段只做设计）；
- 定义 `magicops-diagnosis` 模块接口契约。

产物：

- 更新后的 backlog/feature inventory/project-context；
- `docs/requirements/arthas-diagnosis-center.md`；
- `docs/architecture/diagnosis-design.md`；
- `magicops-diagnosis/` 模块骨架 + 接口定义。

关闭标准：

- 所有文档反映当前实现状态；
- Arthas 诊断中心需求和架构文档完成；
- `magicops-diagnosis` 模块骨架存在。

## 验证策略

每个切片结束时更新文档和 known-good baselines。

切片 12 结束时执行 Docker Compose E2E 回归。
切片 14 结束时执行拦截器流水线集成测试。
切片 16 结束时执行 magic-api 引擎集成测试。

## 风险和约束

- magic-api javax → jakarta 迁移可能涉及大量文件修改，需要自动化工具辅助。
- Runtime 拦截器流水线重构可能影响现有测试，需要逐步替换。
- Docker Compose 测试依赖本地 Docker 环境，CI 环境可能需要额外配置。
- Arthas Tunnel Server 的网络安全要求较高，设计阶段需充分考虑。

## 暂不做

- Arthas 诊断中心完整实现（属于第四阶段）；
- LDAP/OIDC/SSO 认证集成；
- Redis/MQ 治理；
- 完整 UI 重写；
- 多租户；
- 离线发布包主流程。
