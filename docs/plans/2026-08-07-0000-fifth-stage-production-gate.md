# 第五阶段实施计划：生产准入强制化（P0 门槛）

## 状态

进行中。切片 30（2026-08-07）、切片 31（2026-08-08）已获人工确认并实现，见各切片关闭记录。依据 `docs/analysis/2026-08-07-production-readiness-gap-analysis.md` 的 P0 清单整理。

## 目标

在允许 MagicOps 接入线上系统承担日常运营运维之前，把治理链条从"流程约定"升级为"代码强制"，补齐密钥、Runtime 可靠性和执行语义的最小闭环。

关闭标准：全部切片关闭、`mvn test` 全量通过且无既有测试回归、Docker E2E 回归（含治理强制负例）通过、known-good-baselines.md 更新至新基线。

## 已确认关键决策

以下决策来自差距分析，标记为"待确认"的条目在实现前须获得人工确认：

- 治理强制优先于新功能：任何新能力不得挤占本计划切片。
- SQL Guard 升级为解析器方案（JSQLParser），放弃字符串前缀匹配。
- 数据修复执行纳入事务，超过影响行数上限即回滚。
- prod profile 下禁止自动生成临时签名密钥，启动即失败（fail-fast）。
- Runtime 与 Console 共享 PostgreSQL 作为目标同步通道（接受当前部署拓扑耦合，记录在架构文档）。
- 【待确认】双轨路径处置采用方向 A：magic-editor 仅用于调试，生产执行必须走发布链路（详见切片 34）。

## 实施切片

### 切片 30：SQL Guard 与数据修复强制化（已关闭，2026-08-07）

目标：

- `SqlGuardService` 接入 JSQLParser，基于解析树做语句分类，替换字符串前缀匹配；
- 支持表级白名单：发布包 metadata 的 `datasourcePermissions` 落地（`PackageBuildService` 当前硬编码空列表），Runtime 执行时校验目标表在包授权范围内；
- `RepairExecutionService.execute()` 增加审批校验：执行前验证关联脚本版本存在 APPROVED 审批决定，否则拒绝；
- 强制执行 `MAX_AFFECTED_ROWS`：修复在事务中执行，影响行数超限时回滚并记录阻断审计。

产物：

- 重构后的 `SqlGuardService` + JSQLParser 依赖；
- `PackageBuildService` 表权限填充逻辑（脚本声明目标表 → 打包时写入 metadata）；
- `RepairExecutionService` 审批校验与事务化执行；
- Runtime 侧表授权校验；
- 治理强制测试（含绕过负例）。

关闭标准：

- SQL Guard 通过绕过用例：注释混淆、大小写混合、多语句、UNION/子查询内写操作、空白字符变形；
- 未审批的 UPDATE/DELETE 修复请求被拒绝（测试覆盖）；
- 影响行数超过 100 的修复被回滚且落阻断审计（测试覆盖）；
- 表白名单之外的写操作被 Runtime 拒绝（测试覆盖）；
- 既有 `SqlGuardServiceTest`（19 例）全部通过或等价迁移。

关闭记录：

- `SqlGuardService` 重构为 JSQLParser 4.9 解析树方案：`parseSingleStatement`（拒绝空语句/多语句）、`classify`、`validateForRepair`（AST 级 WHERE 检查）、`tablesIn`（排除 CTE 名）、`targetTables`；`TRUNCATE/DROP/ALTER/CREATE/GRANT` 恒拒绝；`#{x}`/`${x}` 占位符解析前规范化为 `?`。
- 审批强制采用签名包凭据模式：`PackageBuildService` 打包时将 APPROVED 审批凭据（approvalId、决定、审批人、时间）与 SQL 引用表清单写入 metadata 的 `approvals` 与 `datasourcePermissions`，随包签名保护；无审批记录、审批被拒绝、SQL 无法解析均 fail-closed 拒绝打包。
- `RepairExecutionService` 执行顺序：DryRun 检查、SQL Guard、审批凭据校验、内容 hash 绑定校验（执行 SQL 必须与包内脚本 contentHash 一致）、表级白名单校验、事务化执行（`MAX_AFFECTED_ROWS=100`，超限回滚）、关键审计。
- `QueryExecutionService` 增加表级白名单校验；无 `datasourcePermissions` 声明的遗留包按告警放行（兼容既有 E2E 包）。
- 验证：全量 `mvn test` 243 例通过（0 失败），较基线新增 34 例。其中 `SqlGuardServiceTest` 46 例（含注释混淆、内联注释写操作、多语句、占位符等绕过负例），`RepairExecutionServiceTest` 16 例（含未审批拒绝、内容不一致拒绝、越权表拒绝、150 行超限回滚并验证数据未变、99 行内提交），`QueryExecutionServiceTest` 10 例（含子查询越权表拒绝），`PackageBuildServiceTest` 8 例（凭据嵌入与验签、fail-closed 负例、HTTP_ADAPTER 跳过表提取）。E2E 闭环测试 10 步全部通过（含新治理凭据随包推送、验签加载与执行）。
- 附带修复：Runtime 自切片 9-11 以来的启动失败（`AuditController` 硬依赖 JPA 仓储被宽扫描捕获），`RuntimeApplication` 排除该控制器，完整持久化由切片 33 收口后移除排除项。
- 遗留事项：JSQLParser 达梦方言抽样验证（风险清单条目）推迟到切片 33 Docker E2E 阶段一并执行。

### 切片 31：身份与追责（已关闭，2026-08-08）

目标：

- 操作人绑定登录用户：`ScriptController` 及相关服务从 `SecurityContextHolder` 取用户名，移除 `"system"/"reviewer"/"publisher"` 硬编码；
- 提交人/审批人分离：`decide()` 校验决策人 ≠ 提交人，违反时拒绝；
- 新增角色回收端点 `DELETE /api/users/{id}/roles/{roleId}`（权限 `user:manage`，关键审计）；
- `DataInitializer` 按 profile 约束：prod 下仅当 `MAGICOPS_ADMIN_PASSWORD` 显式提供时初始化 admin，永不初始化 developer/approver 测试账号。

产物：

- actor 上下文传递改造（Controller → Service → AuditService）；
- 审批分离校验与角色回收端点；
- `DataInitializer` profile 分支；
- 对应测试。

关闭标准：

- 提交/审批/发布/查询/修复的审计记录 actor 为实际登录用户（测试覆盖）；
- 审批本人提交的版本返回 403（测试覆盖）；
- 角色回收端点生效且 RBAC 测试覆盖（含越权拒绝）；
- prod profile 下不产生测试账号（`DataInitializerTest` 覆盖）。

关闭记录：

- 新增 `CurrentActor` 从 SecurityContext 取登录用户名；`ScriptController` 创建/版本/提交/审批/发布五处硬编码 actor 全部替换，发布包 publishedBy 成为真实操作人，Runtime 查询/修复审计 operator 随之真实化；`UserController` 角色授予人强制取登录身份。
- `ScriptLifecycleService.decide()` 增加提交人/审批人分离校验，违规抛 `ApprovalSeparationException` 映射 403，先于任何状态变更。
- 新增 `DELETE /api/users/{id}/roles/{roleId}` 角色回收端点（user:manage），`UserService.revokeRole` 落 ROLE_REVOKED 关键审计；`assignRole` 同部落 ROLE_ASSIGNED 关键审计（`AuditEventType` 新增两枚举）。
- `DataInitializer` profile 分支：prod 仅当显式提供 `MAGICOPS_ADMIN_PASSWORD` 时建 admin，永不建测试账号；非 prod 维持原行为。
- 附带修复：通用异常处理器吞掉 `AccessDeniedException` 导致 RBAC 越权返回 500 的既有缺陷，安全异常现重抛交还过滤链（403/401）。
- 验证：全量 `mvn test` 253 例通过（0 失败），新增 `GovernanceActorTest` 6 例（审计 actor、自审自批 403、越权 403、角色回收闭环与负例）与 `DataInitializerTest` 4 例；E2E 更新为 admin/approver 双账号流程并新增自审自批负例，11 步全部通过。
- 遗留事项：HttpTarget 等资源 CRUD 创建人字段绑定登录身份不在本切片范围，随后续治理切片处理。

### 切片 32：密钥与凭据固化

目标：

- prod fail-fast：签名密钥（`MAGICOPS_PRIVATE_KEY/PUBLIC_KEY` 或 JKS）缺失时启动失败；自动生成临时密钥对的路径仅限 dev profile；
- 装配 `KeyStoreKeyProvider`（JKS 多 keyId）为配置驱动的可选项；
- Runtime 收包端点加共享密钥认证：`PushService` 推送携带 `MAGICOPS_RUNTIME_SHARED_SECRET` 头，`PackageReceiveController` 校验；补齐 Runtime prod 安全凭据；
- 凭据出库：`deploy/.env` 移除真实密码，改为 `.env.example`；根 `docker-compose.yml` 硬编码密码改为环境变量引用。

产物：

- `KeyProvider` 启动校验与 profile 分支；
- JKS 配置装配；
- 推送/接收双向共享密钥认证；
- `.env.example` 与 compose 凭据外部化。

关闭标准：

- prod profile 缺密钥时应用启动失败并给出明确错误；
- 未携带或不匹配共享密钥的包推送被 401 拒绝（测试覆盖）；
- 仓库内不再存在真实密码（`deploy/.env` 与 compose 检查通过）；
- 密钥轮换测试在持久化 Provider 下仍通过。

### 切片 33：Runtime 闭环与可靠性

目标：

- 激活包持久化：Runtime 将激活包（含签名与 metadata）落库，重启后重新验签并加载（新增 Flyway 迁移与 prod 配置）；
- `HttpTargetRegistry` 从 `http_targets` 表同步：启动加载 + 定时刷新，打通 Console CRUD 到 Runtime 生效链路；
- Runtime 审计持久化：`RuntimeApplication` 装配 `@EnableJpaRepositories`/`@EntityScan`，消除内存 fallback，并移除切片 30 对 `AuditController` 的扫描排除；
- 新增发布回滚/下线端点：Console 侧下线与回滚操作构建对应状态包推送 Runtime，Runtime 验证后停用。

产物：

- Runtime Flyway 迁移（active_packages 表）与 prod 配置补全；
- 激活包持久化与启动重载逻辑；
- HTTP 目标同步服务；
- 回滚/下线端点与 Runtime 停用逻辑；
- 对应测试。

关闭标准：

- Runtime 重启后自动重载激活包并通过验签与环境校验（测试覆盖）；
- Console 创建的 HTTP 目标在刷新周期内对 Runtime 生效（测试覆盖）；
- Runtime 重启后关键执行审计仍可从数据库查询（Docker E2E 验证）；
- 下线后 Runtime 拒绝对应脚本执行（测试覆盖）；
- Docker Compose E2E 回归通过（含重启场景）。

### 切片 34：执行语义对齐（依赖切片 33，需人工确认）

目标：

- 落实双轨决策方向 A：Runtime 执行端点（`/api/query`、`/api/repair/*`、`/api/adapter/execute`）改为脚本引用模式——请求携带脚本标识与参数，执行内容为激活包中的脚本，拒绝裸 SQL/裸目标调用；
- magic-editor 保留调试能力，但明确其与生产执行路径的边界（入口提示与文档）。

产物：

- Runtime 执行端点契约变更与脚本解析执行逻辑；
- `docs/architecture/release-and-runtime.md`、`docs/architecture/security-and-governance.md` 更新；
- E2E 用例更新。

关闭标准：

- Runtime 拒绝不引用激活包脚本的执行请求（测试覆盖）;
- E2E：创建→审批→发布→脚本引用执行全链路成功，裸 SQL 请求被拒绝；
- 既有调用方影响清单已盘点并记录（本计划内 Console/插件为唯一已知调用方）；
- 架构 owner docs 反映新契约。

## 验证策略

- 每个切片附单元测试；治理强制项必须包含负例（绕过/越权/未审批）测试。
- 切片 30-31 完成后执行一次全量 `mvn test` 回归。
- 切片 33 完成后执行 Docker Compose E2E 回归（含重启与审计持久化验证）。
- 切片 34 完成后重跑 E2E 并更新 `docs/testing/known-good-baselines.md`。

## 风险和约束

- 切片 34 属 API 契约变更且触及保护区，实现前必须获得人工确认；若存在未盘点的既有调用方会被破坏。
- JSQLParser 对达梦方言的兼容性未验证，切片 30 需在达梦 compose 环境抽样验证。
- Runtime 共享 Console 数据库引入部署拓扑耦合，接受为当前阶段约束并记录在架构文档。
- 全部切片触及 AGENTS.md 保护区，每个切片合并前需人工评审。

## 暂不做

- 多级/可配置审批（P2）；
- Arthas 真实 Tunnel 联调与诊断持久化（另行计划，P1）;
- 可观测体系（actuator/metrics/结构化日志，P1）；
- 数据源/项目/环境管理 API（P1）；
- Redis/MQ 与多实例部署；
- magic-api fork 深度改造（方向 B，已否决为当前方案）。
