# AGENTS.md

## 项目意图

MagicOps 使用轻量级 Attractor-Guided Engineering 工作流来支撑 AI 辅助的产品和平台开发。

仓库是长期事实源。聊天只作为临时工作界面。重要需求、设计决策、架构决策、计划、验证证据和经验沉淀都必须落到 `docs/` 下。

MagicOps 是基于 `magic-api` fork 的医卫/政务内网动态开发与运维平台。第一阶段聚焦：

1. 动态查询接口；
2. 受控数据修复；
3. HTTP 接口适配。

Arthas 线上 Java 诊断是核心差异化能力，但属于第二阶段。

## 优先阅读

开始非平凡工作前，先读：

- `docs/index.md`
- `docs/context/project-context.md`
- `docs/context/ai-autonomy-policy.md`
- `docs/context/codebase-map.md`
- `docs/context/source-of-truth-and-precedence.md`
- `docs/requirements/` 下的当前活跃需求
- `docs/design/` 或 `docs/architecture/` 下相关 owner doc

如果任务涉及计划或流程决策，也要读 `docs/process/application-development-workflow.md`。

## 任务路由

写代码前先判断任务类型：

1. 需求澄清；
2. 应用层设计变更；
3. 架构变更；
4. 纯实现变更；
5. 缺陷调查；
6. 验证或审计工作。

使用 `docs/index.md` 找到控制该任务的 owner docs。除非需求、owner doc 和验证路径都已经清楚，否则不要从功能请求直接跳到实现。

## 工作规则

1. 优先采用“文件输入、文件输出”的协作方式。
2. 不要把聊天摘要当作长期项目记忆。
3. `docs/design/` 和 `docs/architecture/` 只保存当前支持的稳定基线，不保存谈判过程流水账。
4. `docs/requirements/` 保存可实现的需求范围。
5. `docs/plans/` 保存非平凡执行计划和关闭门槛。
6. `docs/logs/` 保存按日期归档的实现记忆。
7. `docs/audits/` 保存非平凡审查记录。
8. `docs/bugs/` 保存不明显缺陷和回归记录。
9. 只有当经验或方法稳定复用时，才写入 `docs/lessons/` 或 `docs/skills/`。
10. 找不到被引用文件时，先检查 `docs/archive/`，再判断它不存在。

## 保护区

以下区域至少按 `plan-first` 处理：

- 认证与授权；
- 审批、签名和 Runtime 发布；
- SQL Guard 与数据修复执行；
- 审计可靠性与敏感数据脱敏；
- 密钥管理与加解密模块；
- 生产 Runtime 行为；
- 外部 HTTP 集成行为；
- 未来 Arthas 诊断命令和会话控制。

放松这些保护前必须获得人工确认。

## 计划规则

当任务改变 API 契约、数据库/模型形态、认证、权限、集成、部署、发布/签名行为、跨模块架构或保护区时，必须先建立计划。

只有当修改很小、局部、低风险，且目标行为已经被 owner doc 和验证路径覆盖时，才可以跳过正式计划。

## 文档维护

完成重要代码或架构变更后：

1. 更新 `docs/design/` 或 `docs/architecture/` 下相关 owner docs；
2. 更新 `docs/logs/<year>/<month-day>.md` 下的日记；
3. 当验证达到有意义的 known-good 状态时，更新 `docs/testing/known-good-baselines.md`。

当 `docs/context/project-context.md` 中的命令仍是占位符时，不要声称构建或测试验证成功。
