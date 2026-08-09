# 项目上下文

## 目的

这是 AI Agent 开始有效工作前需要读取的最短静态基线：项目身份、文档新鲜度、技术栈和验证命令。

本文件不追踪当前进行中的工作。活跃执行状态请查看 `docs/plans/` 下未完成计划。

## 项目身份

- 项目名称：MagicOps
- 产品类型：医卫/政务内网动态开发、集成运维、数据修复和 Java 诊断平台
- 主要用户：项目开发、实施工程师、运维工程师、技术审批人、安全审计员
- 文档新鲜度：`fresh`

当前可信部分：

- 第一阶段范围（切片 0-5 全部完成）；
- 第二阶段生产可用（切片 6-11 全部完成）；
- 第三阶段功能完善（切片 12-15 完成，切片 16-17 部分完成）；
- Console 到 Runtime 在线推送发布决策；
- 平台持有签名私钥决策；
- 动态 API 与数据修复共用脚本生命周期决策；
- Arthas 诊断中心需求和架构设计已完成；
- Maven 多模块脚手架和构建/验证命令；
- 285 个测试全部通过（截至第五阶段切片 33）；
- E2E 12 步闭环验证通过。

仍不完整或尚未验证：

- 切片 34：执行语义对齐（query/adapter 执行签名包脚本而非裸请求体，需人工确认）；
- magic-api fork 代码整合（javax → jakarta 迁移，切片 16）；
- 达梦数据库真实环境验证（当前使用 H2）；
- Docker Compose 端到端验证（含 Runtime 重启重载与审计持久化）。

## 当前技术基线

- 当前仓库状态：五阶段切片 33 完成，285 个测试通过，20 张数据库表，11 个模块。
- 后端栈：Java 21、Spring Boot 3.3.5、Spring Security、Spring Data JPA、Flyway、PostgreSQL。
- 前端/编辑器目标栈：第一阶段复用或扩展 `magic-editor`，后续再评估 UI 重写。
- 数据库 schema：Flyway V1-V10 迁移脚本管理（Console 拥有生产 schema，Runtime 共享 PostgreSQL 以 `ddl-auto=validate` 校验）。
- 认证：数据库用户 + BCrypt + UserDetailsService（替换了静态 admin/admin）。
- 授权：@PreAuthorize 方法级 + @ResourcePermission 资源级 + 7 角色 17 权限。
- 审计：PostgreSQL 持久化 + 敏感数据脱敏 + 分页查询 API。
- 密钥管理：KeyRotationService（轮换/共存/移除）+ JKS 文件 + SM4 国密。
- Runtime 流水线：TraceIdFilter → RateLimitFilter → ResponseMaskingFilter。
- 发布模型：Console 在线推送已签名发布包到 Runtime。
- 部署：Docker Compose（PostgreSQL + Console + Runtime）。

## 验证命令

| 目的 | 命令 |
|---|---|
| 安装依赖并编译 | `mvn compile` |
| 安装到本地仓库 | `mvn install -DskipTests` |
| 编译检查 | `mvn compile` |
| 构建（含打包） | `mvn package -DskipTests` |
| 单元测试 | `mvn test`（148 个测试） |
| E2E 闭环测试 | `bash docs/testing/e2e/e2e-closed-loop-test.sh` |
| Console 本地启动 | `java -jar magicops-console/target/magicops-console-0.1.0-SNAPSHOT.jar` |
| Runtime 本地启动 | `java -jar magicops-runtime/target/magicops-runtime-0.1.0-SNAPSHOT.jar` |
| Docker 一键启动 | `docker-compose up` |

Console 默认端口 8080，Runtime 默认端口 8081。默认管理员：admin / magicops-admin。

## 当前启用的可选层

- [x] `docs/audits/`
- [x] `docs/testing/`
- [x] `docs/discussions/`
- [ ] `docs/analysis/`
- [ ] `docs/lessons/`
- [ ] `docs/skills/`
- [ ] `docs/retrospectives/`

## AI 阻塞条件

出现以下情况时，AI 必须停止或先询问，不能直接实现：

- 修改依赖缺失的构建或验证命令；
- 修改触及保护区，但没有控制它的 owner doc；
- 修改会放松签名、审计、Runtime、SQL Guard 或数据修复控制；
- 代码和 owner docs 的冲突会改变公开契约或安全行为；
- 外部集成行为没有被已提交文档或测试描述。
