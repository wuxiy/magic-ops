# 第四阶段实施计划：生产加固与诊断中心

## 状态

Draft，等待实现前确认。

## 目标

修复已发现的部署缺陷，补全测试覆盖，实现 Arthas 诊断中心核心功能。

关闭标准：Docker Compose 可正常启动并通过 E2E 验证、所有模块有测试覆盖、诊断中心核心功能可用。

## 已确认关键决策

- Docker 部署修复为优先事项（阻塞生产部署）。
- Arthas 诊断中心使用 Tunnel 模式，Console 作为 WebSocket 代理。
- 诊断命令模板采用白名单模式，默认拒绝自由命令。
- DM 数据库验证使用 Docker 镜像模拟（dm8_single）。
- 所有新增模块必须有测试覆盖。

## 实施切片

### 切片 18：部署修复与 Docker E2E 验证

目标：

- 修复 docker-compose.yml 环境变量不匹配（MAGICOPS_ADMIN_PASSWORD → CONSOLE_ADMIN_PASSWORD）；
- 完善 Runtime application-prod.yml（PostgreSQL + HikariCP + Flyway）；
- Runtime Dockerfile 添加 SPRING_PROFILES_ACTIVE=prod；
- docker-compose up 启动成功并通过 E2E 验证。

产物：

- 修复后的 docker-compose.yml、application-prod.yml、Dockerfile.runtime；
- Docker E2E 测试脚本；
- known-good-baselines.md 更新。

关闭标准：

- `docker-compose up` 三个容器全部 healthy；
- E2E 10 步在 Docker 环境全部通过；
- 审计记录在 PostgreSQL 中持久化（重启后仍可查询）。

### 切片 19：测试覆盖补全

目标：

- magicops-http 模块添加测试（HttpClientService、HttpTargetRegistry、allowlist 验证）；
- magicops-crypto 补充 AES-GCM 和 HMAC 测试；
- magicops-audit 补充持久化和脱敏集成测试；
- 整体测试覆盖率达到合理水平。

产物：

- `HttpClientServiceTest`；
- `HttpTargetRegistryTest`；
- `CryptoServiceTest`（AES-GCM + HMAC）；
- `AuditPersistenceTest`。

关闭标准：

- magicops-http 至少有 8 个测试；
- magicops-crypto 至少有 8 个测试；
- 所有模块测试覆盖率 > 0%；
- `mvn test` 全部通过。

### 切片 20：Arthas 诊断中心核心实现

目标：

- 实现 SessionManager（会话生命周期、超时、并发限制）；
- 实现 CommandTemplateRegistry（模板 CRUD、参数校验、风险分级）；
- 实现 DiagnosisController（REST API）；
- 实现 DiagnosisWebSocket（实时输出代理）；
- Flyway V7 迁移（diagnosis_sessions、command_templates、command_executions）。

产物：

- `SessionManager` + `CommandTemplateRegistry`；
- `DiagnosisController` + `DiagnosisWebSocket`；
- `V7__diagnosis_tables.sql`；
- 诊断中心测试。

关闭标准：

- 可创建诊断会话并设置超时；
- 只能执行已注册命令模板；
- 高风险命令需要额外审批标记；
- 会话超时后自动关闭；
- 至少 10 个诊断中心测试通过。

### 切片 21：诊断输出脱敏与审计集成

目标：

- 实现 OutputMaskingService（诊断输出脱敏）；
- 诊断操作审计集成（接入 AuditService）；
- 诊断报告生成和导出；
- 权限控制接入（@PreAuthorize）。

产物：

- `OutputMaskingService`；
- `DiagnosisReport` 模型和导出逻辑；
- 审计集成和权限注解；
- 脱敏和审计测试。

关闭标准：

- 诊断输出中的密码/密钥/token 被脱敏；
- 所有诊断操作有审计记录；
- 运维人员可创建会话，其他角色被拒绝；
- 安全审计员可查看审计但不能操作会话。

### 切片 22：达梦数据库验证与文档收尾

目标：

- DM8 Docker 镜像环境搭建；
- SQL Guard 在达梦环境验证；
- 数据修复 dry-run 在达梦环境验证；
- 文档状态同步（plan 状态更新为 closed）。

产物：

- `docker-compose.dm.yml`（达梦测试环境）；
- 达梦兼容性测试报告；
- 文档状态更新。

关闭标准：

- SQL Guard 在达梦环境正确分类和拦截 SQL；
- 数据修复 dry-run 在达梦环境生成影响范围报告；
- 所有 plan 文件状态更新为 closed。

## 验证策略

切片 18 结束时执行 Docker Compose E2E 回归。
切片 20 结束时执行诊断中心集成测试。
切片 22 结束时执行达梦兼容性测试。

## 风险和约束

- Arthas Tunnel Server 需要独立部署，测试环境可能需要额外配置。
- DM8 Docker 镜像可能不在公共 registry，需要确认来源。
- WebSocket 代理实现需要考虑连接管理和超时。

## 暂不做

- LDAP/OIDC/SSO 认证集成；
- Redis/MQ 治理；
- 多租户；
- 完整 UI 重写；
- 离线发布包主流程。
