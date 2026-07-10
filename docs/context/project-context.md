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

- 第一阶段范围；
- Console 到 Runtime 在线推送发布决策；
- 平台持有签名私钥决策；
- 动态 API 与数据修复共用脚本生命周期决策；
- Arthas 第二阶段定位；
- Maven 多模块脚手架和构建/验证命令。

仍不完整或尚未验证：

- fork 后的具体代码结构；
- 数据库 schema 来源；
- Runtime API 契约。

## 当前技术基线

- 当前仓库状态：脚手架已完成，Maven 多模块项目已建立，Console 和 Runtime 可独立启动。
- 后端目标栈：Java、Spring Boot、magic-api fork、PostgreSQL、Spring Security。
- 前端/编辑器目标栈：第一阶段复用或扩展 `magic-editor`，后续再评估 UI 重写。
- 数据库/模型来源：尚未创建；未来 schema/model 文件将成为数据库事实源。
- 发布模型：Console 在线推送已签名发布包到 Runtime。
- Runtime 签名模型：平台使用私钥签名，Runtime 使用公钥验签。

## 验证命令

| 目的 | 命令 |
|---|---|
| 安装依赖并编译 | `mvn compile` |
| 安装到本地仓库 | `mvn install -DskipTests` |
| 编译检查 | `mvn compile` |
| 构建（含打包） | `mvn package -DskipTests` |
| 单元测试 | `mvn test` |
| 集成测试 | `mvn verify`（集成测试待补充） |
| Console 本地启动 | `java -jar magicops-console/target/magicops-console-0.1.0-SNAPSHOT.jar` |
| Runtime 本地启动 | `java -jar magicops-runtime/target/magicops-runtime-0.1.0-SNAPSHOT.jar` |

Console 默认端口 8080，Runtime 默认端口 8081。启动后可通过 HTTP Basic 认证访问。

## 当前启用的可选层

- [x] `docs/audits/`
- [x] `docs/testing/`
- [ ] `docs/analysis/`
- [ ] `docs/lessons/`
- [ ] `docs/discussions/`
- [ ] `docs/skills/`
- [ ] `docs/retrospectives/`

## AI 阻塞条件

出现以下情况时，AI 必须停止或先询问，不能直接实现：

- 修改依赖缺失的构建或验证命令；
- 修改触及保护区，但没有控制它的 owner doc；
- 修改会放松签名、审计、Runtime、SQL Guard 或数据修复控制；
- 代码和 owner docs 的冲突会改变公开契约或安全行为；
- 外部集成行为没有被已提交文档或测试描述。
