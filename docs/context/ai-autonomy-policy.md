# AI 自主性策略

## 目的

本文件定义 AI Agent 什么时候可以继续推进，什么时候必须停下来等待人工输入。

AI 可以让本策略更严格。没有明确人工确认时，AI 不得放松保护区，不得把 `ask-first` 或 `plan-first` 改成 `implement`，也不得移除 blocker。

## 自主性等级

在 `docs/backlog/` 和活跃计划中使用这些标签：

- `implement` - AI 读取列出的需求、owner doc 和验证命令后可以实现。
- `plan-first` - AI 可以起草或更新计划；实现必须等待计划审查和必要证据。
- `ask-first` - 修改代码、用户可见行为、契约或保护区前必须先问。
- `research-only` - AI 可以检查、总结和提出选项，但不得修改产品行为。
- `blocked` - blocker 解决前 AI 不得推进。

## 评审可用性

- 评审可用性：`human`

如果保护区没有人工评审可用，实现状态保持 `plan-first` 或 `blocked`。

## AI 可以不询问直接推进的条件

- 工作项标记为 `implement`；
- 存在明确需求和 owner doc；
- 被触及代码区域已有真实验证命令；
- 不触及保护区；
- 开放问题已明确为非阻塞。

## 保护区

| 区域 | 默认规则 | 所需证据 |
|---|---|---|
| 认证与授权 | plan-first | owner doc + 测试 |
| 审批、签名与发布 | ask-first | owner doc + 测试 + 人工确认 |
| Runtime 生产执行 | ask-first | owner doc + 测试 + 人工确认 |
| SQL Guard 与数据修复 | ask-first | owner doc + 测试 + 回滚证明 |
| 审计可靠性与敏感信息脱敏 | plan-first | owner doc + 测试 |
| 密钥管理与加解密 | ask-first | owner doc + 安全审查 |
| 外部 HTTP 集成行为 | plan-first | 集成 owner doc + 契约测试 |
| Arthas 诊断命令 | ask-first | 诊断 owner doc + 安全审查 |
| 部署与环境行为 | plan-first | 部署文档 + 回滚路径 |

## Backlog 选择规则

如果用户要求 AI 继续但没有指定任务，选择 `docs/backlog/README.md` 中优先级最高、状态为 `ready`、自主性为 `implement` 且 blocker 为 `none` 的工作项。

如果没有安全的 `implement` 项，概述最高优先级的 `blocked`、`plan-first` 或 `ask-first` 项，并请求决策。
