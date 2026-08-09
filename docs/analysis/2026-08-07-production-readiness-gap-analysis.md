# 生产化差距分析：集成到线上系统做日常运营运维

日期：2026-08-07
类型：analysis（可行性评估 + 缺口清单）
基线：git `eeb6cefa`（2026-08-04，feat(deploy): 添加 Docker 部署配置）

## 结论

**不建议现在就整体接入生产做日常运维。** 平台骨架完整可用（Portal、Console 五页 SPA、magic-editor SSO 免登、诊断 UI、审批+签名+推送闭环已演示），但治理闭环存在多处"声明有、强制无"的断点，且关键生产能力（密钥持久化、Runtime 包持久化、执行审计持久化、回滚、可观测）缺失。

可行的路径是**灰度试运行**：先只开放只读动态查询接口（风险最低），补齐 P0 强制项后再开放数据修复，Arthas 诊断在真实 Tunnel 校准后再上线。

## 2026-08-09 复核更新

本节为 2026-08-09 针对 main 分支的**代码级独立复核**结论（非仅依据文档/计划状态）。下方 A1-A6 仍为 2026-08-07 基线快照，部分条目已被切片 30-32 关闭，状态以本节为准。

**结论更新：P0-1 至 P0-5 全部关闭（切片 30-34）。代码级治理强制闭环已完成；剩余上线阻断为运营支撑能力（可观测、CI、真实数据库验证、业务数据源管理 API），见下方"仍开放"与 A5/A6。

已关闭并代码验证（P0-1/P0-2/P0-3）：

- P0-1 治理强制化（切片 30）：A1 中"数据修复无审批校验""SQL Guard 字符串前缀""行数上限未生效""包元数据权限空转"均已修复。`SqlGuardService` 走 JSQLParser 解析树；`RepairExecutionService` 含审批凭据校验 + contentHash 绑定 + 表白名单 + 事务化 `MAX_AFFECTED_ROWS=100` 回滚；`PackageBuildService` 打包时填充 `datasourcePermissions`/`approvals`。
- P0-2 身份与追责（切片 31）：A1 中"操作人硬编码""无提交人/审批人分离""prod 仍初始化测试账号"均已修复。`ScriptLifecycleService:167` 强制提交人≠审批人；actor 绑定 SecurityContext 登录用户；`DataInitializer` prod 分支不建测试账号；新增角色回收端点。
- P0-3 密钥与凭据固化（切片 32）：A3 中"生产签名密钥可能为临时密钥""根 compose 硬编码密码""prod 仍初始化测试账号"已修复。`KeyProvider` prod fail-fast 禁临时密钥；收包端点 `PushSecretAuthenticationFilter` 401；凭据外部化为 `${VAR:?}` 必填引用 + `.env.example`。

仍开放（硬阻断，上线前必须解决）：

- P0-4 Runtime 闭环（A4）= 切片 33 **已关闭（2026-08-09）**：激活包落 `active_packages` 表并 `@PostConstruct` 重载验签（替换 AtomicReference 内存态）；Runtime 审计持久化（`RuntimeApplication` 装配 `@EnableJpaRepositories`/`@EntityScan`，消除内存 fallback）；`HttpTargetSyncService` 启动+定时从 `http_targets` 同步；`POST /api/packages/deactivate` 下线端点受共享密钥保护。Docker Compose E2E on PostgreSQL 待切片 34 收口执行。
- P0-5 双轨/执行语义（A2）= 切片 34 **已关闭（2026-08-09）**：query/repair/adapter 执行端点改为脚本引用模式（`ScriptResolver` 按 scriptId 从激活包解析内容并校验 contentHash），拒绝裸 SQL/裸目标调用（缺 scriptId 返回 400）。E2E 脚本引用执行通过、裸 SQL 被拒。

修正（A6）：前端构建产物现已正确 gitignore（`.gitignore` 覆盖 `magicops-console/src/main/resources/static/console/`，git 跟踪数为 0），原"前端构建产物直接提交 git"不再成立；但"前端未纳入 Maven 构建（无 frontend-maven-plugin）"仍成立。其余 A5（可观测）、A6（无 CI、集成测试全 H2、无达梦验证、Docker Compose 未真实跑）仍开放。

## A. 阻断性缺口（上线前必须解决）

### A1 治理强制断点（保护区）

| 问题 | 证据 |
|---|---|
| 数据修复无审批校验 | `RepairExecutionService.execute()` 只做 dry-run→SQL Guard→执行→审计，Javadoc 声称的"验证审批已通过"未实现；`RepairController` 同样不校验 |
| SQL Guard 为字符串前缀匹配 | `SqlGuardService` 注释"后续切片接入 JSQLParser"，至今未接入；无表级白名单；UPDATE 的 WHERE 校验用 `contains` |
| 行数上限未生效 | `RepairExecutionService.MAX_AFFECTED_ROWS=100` 仅声明，grep 无使用处 |
| 操作人未绑定登录用户 | `ScriptController` 中 actor 硬编码 `"system"/"reviewer"/"publisher"`，审计与审批追责断链 |
| 无提交人/审批人分离 | `decide()` 不校验审批人≠提交人，可自审自批 |
| 资源级权限未启用 | `@ResourcePermission` 注解 + Aspect 存在，业务代码零使用 |
| 角色只进不出 | 无"移除角色"端点（`docs/logs/2026-07-30.md` 遗留项） |
| Runtime 收包端点无认证 | `runtime/SecurityConfig` 对 `/api/packages` permitAll，防线仅剩验签；且 `PushService` 推送不带任何认证头 |
| 包元数据权限空转 | `PackageBuildService` 中 datasourcePermissions/httpTargetPermissions/keyRefPermissions 硬编码空列表 |

### A2 双轨路径绕过治理（结构性问题）

magic-editor（magic-api 原生）与 MagicOps 脚本生命周期是两条独立路径：编辑器中的脚本由 magic-api 以 `/magic-api/**` 前缀在 Console 内直接执行，**不经过审批、签名、SQL Guard**。发布链路（签名包→Runtime 激活）的脚本内容实际也未被执行——Runtime 的 Query/Repair/Adapter 端点执行的 SQL/参数来自请求体，发布包只充当"激活门票"。

这意味着日常运营人员若习惯在编辑器写脚本直接跑，治理链条整体被绕过。这是上线前必须做的产品+安全决策：要么打通编辑器→发布链路，要么明确编辑器仅用于调试、生产执行必须走发布链路并在 Runtime 强制。

### A3 密钥与账号

| 问题 | 证据 |
|---|---|
| 生产签名密钥可能为临时密钥 | `KeyProvider` 在 env 缺失时自动生成临时密钥对（日志 "NOT FOR PRODUCTION"）；JKS 多密钥 Provider 已实现但无装配点 |
| 密钥轮换状态不落盘 | `KeyRotationService` 密钥只存内存，重启即失 |
| 密码明文入库 | `deploy/.env` 含 `DB_PASSWORD=magicops_dev_2026`；根 `docker-compose.yml` 硬编码多个密码 |
| prod 仍初始化测试账号 | `DataInitializer` 首启创建 admin/developer/approver（弱默认密码），prod profile 同样执行 |
| CryptoService 对称密钥内存态 | 加密能力重启失效 |

### A4 Runtime 可靠性

| 问题 | 证据 |
|---|---|
| 激活包仅存内存 | `AtomicReference` 持有，Runtime 重启即丢激活状态 |
| Runtime 执行审计落内存 | `RuntimeApplication` 无 `@EnableJpaRepositories`，audit Repository 注入为 null，走内存 fallback |
| HTTP 适配实际不可用 | `HttpTargetRegistry.register()` 只在测试中调用，Console `http_targets` 表与 Runtime 注册表无同步，生产调用会返回"目标未注册" |
| prod 配置缺口 | Runtime `application-prod.yml` 无 security 用户凭据、无 Flyway 配置 |
| 无回滚/下线端点 | 状态机有 ROLLED_BACK/DISABLED，无任何 API 触发 |
| `HttpClientService` 能力缩水 | Javadoc"超时、重试和熔断"，实际仅 30s 超时；`authType/authConfig` 字段完全未使用 |

### A5 可观测与运维支撑

> 2026-08-09 更新（切片 35）：actuator+micrometer-prometheus 已接入 Console+Runtime（`/actuator/health`、`/actuator/prometheus` 真实端点，需 `management.prometheus.metrics.export.enabled=true`）；logback-spring.xml 结构化日志（含 traceId MDC）；Console 补 TraceIdFilter+RateLimitFilter。诊断会话持久化与 Arthas 真实联调仍开放。

- 无 actuator/micrometer/prometheus；SecurityConfig `permitAll("/actuator/health")` 但端点不存在，Docker healthcheck 打静态页。
- 无结构化日志（无 logback 配置）；Console 无 traceId/限流过滤器（Runtime 有）。
- 诊断会话内存态：`diagnosis_sessions/command_templates/command_executions` 三表无代码使用；`cleanupExpiredSessions()` 无 @Scheduled 调用；诊断未接 AuditService；V7 预置的 9 个命令模板运行时不加载。
- Arthas 默认 simulate-fallback，真实 `ArthasTunnelClient` 未见联调记录。

### A6 部署与数据

> 2026-08-09 更新（切片 35）：根 docker-compose.yml 已含 Console+Runtime+PostgreSQL 三服务；GitHub Actions CI 已建（push/PR 触发 mvn test）；Docker Compose E2E on PostgreSQL 已通过（16 步）。仍开放：前端未纳入 Maven、达梦真实验证、Testcontainers、业务数据源管理 API（切片 36）。

- `deploy/` 仅 Console 单容器，无 Runtime 服务定义。
- 前端构建未纳入 Maven（无 frontend-maven-plugin），构建产物直接提交 git。
- 无 CI；无达梦真实验证记录（只有 compose 文件）；集成测试全基于 H2，无 Testcontainers/真实 PostgreSQL。
- 数据源/项目/环境管理只有实体无 Controller，`DynamicDataSourceManager` 默认硬编码 H2——日常运营接入业务数据源的前置能力缺失。
- known-good-baselines 停在 2026-07-11，多份文档状态落后于代码（混合 UI 计划、Arthas 需求、backlog）。

## B. 优化路线（按优先级）

### P0 — 上线门槛

1. **治理强制化**：修复链路接入审批校验；JSQLParser 接入 SQL Guard；表级白名单（落地包元数据权限）；强制执行 MAX_AFFECTED_ROWS。
2. **身份与追责**：actor 绑定登录用户；提交人≠审批人强制；角色回收端点；prod 禁用测试账号初始化；Runtime 收包端点加共享密钥或 mTLS。
3. **密钥固化**：prod 强制外部注入签名密钥（env 或 JKS），禁止临时密钥；`.env` 与 compose 密码移出仓库进密钥管理。
4. **Runtime 闭环**：激活包持久化+启动重载；执行语义对齐（执行发布包脚本内容而非裸请求体）；`HttpTargetRegistry` 从 Console DB 同步；prod 凭据 + Flyway + 审计 JPA 装配；发布回滚/下线端点。
5. **双轨决策**：明确 magic-editor 与发布链路的关系并强制执行。

### P1 — 运营首月

6. 可观测：actuator + metrics + 告警；结构化日志；Console 补 traceId/限流。
7. 数据源/项目/环境管理 API + `DynamicDataSourceManager` 真实注册（动态查询上线前置）。
8. 诊断持久化：会话/执行落库、模板从 DB 加载、定时清理、接审计；Arthas 真实 Tunnel 联调校准。
9. 构建流水线：前端纳入 Maven/CI，产物不入 git。
10. 验证补齐：达梦 + PostgreSQL 集成测试；更新 known-good-baselines；同步落后的文档状态。

### P2 — 持续演进

11. 单级审批→可配置多级；诊断高危命令从"直接 403"改为走审批流。
12. `HttpClientService` 补齐重试/熔断与 authConfig 注入。
13. 审计事件补全（PACKAGE_SIGNED/PACKAGE_PUSHED 已定义未写入）。
14. 如需多实例部署：Session 与诊断会话状态外置（Redis）。

## C. 可以立即做的低风险切入

如果希望尽快产生运营价值，建议按此顺序灰度：

1. 只读动态查询接口（SQL Guard 仅允许 SELECT，已有 1000 行上限）——先接 1-2 个低风险业务查询。
2. HTTP 接口适配（补 P0-4 注册表同步后）——对已有内部系统做只读拉取。
3. 数据修复（必须完成 P0-1/P0-2 之后）。
4. Arthas 诊断（真实 Tunnel 校准之后）。
