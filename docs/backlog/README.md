# 工作队列

## 目的

本文件列出 AI 可检查或执行的候选工作。Backlog 不替代 requirements、owner docs 或 plans，只用于选择下一项切片。

## 工作项

| 优先级 | 工作项 | 需求 | Owner Doc | 计划 | 状态 | AI 自主性 | 阻塞 | 最近检查 |
|---|---|---|---|---|---|---|---|---|
| P0 | 搭建 MagicOps 单仓库 | `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md` | `docs/architecture/system-baseline.md` | `docs/plans/2026-07-09-0000-first-stage-implementation-plan.md` | needs-implementation | plan-first | 构建和验证命令尚未建立 | 2026-07-09 |
| P0 | 定义脚本生命周期模型 | `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md` | `docs/design/flow-overview.md` | `docs/plans/2026-07-09-0000-first-stage-implementation-plan.md` | needs-implementation | plan-first | 依赖脚手架 | 2026-07-09 |
| P0 | 定义发布包与 Runtime 验签契约 | `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md` | `docs/architecture/release-and-runtime.md` | `docs/plans/2026-07-09-0000-first-stage-implementation-plan.md` | needs-implementation | ask-first | 发布/签名属于保护区 | 2026-07-09 |
| P1 | 动态查询 API 第一切片 | `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md` | `docs/design/feature-inventory.md` | `docs/plans/2026-07-09-0000-first-stage-implementation-plan.md` | idea | plan-first | 依赖脚手架和生命周期 | 2026-07-09 |
| P1 | 受控数据修复第一切片 | `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md` | `docs/design/flow-overview.md` | `docs/plans/2026-07-09-0000-first-stage-implementation-plan.md` | idea | ask-first | 依赖 SQL Guard 与审计 | 2026-07-09 |
| P1 | HTTP 接口适配第一切片 | `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md` | `docs/architecture/module-boundaries.md` | `docs/plans/2026-07-09-0000-first-stage-implementation-plan.md` | idea | plan-first | 依赖 HTTP target 模型 | 2026-07-09 |
| P2 | Arthas 诊断中心调研 | 第二阶段需求 TBD | `docs/architecture/system-baseline.md` | optional | idea | research-only | 第二阶段范围 | 2026-07-09 |

## 就绪不变量

`ready` 表示以下条件全部成立：

- 需求路径存在，且有可测试验收标准；
- owner doc 路径存在，且对该切片不是已知过期；
- `docs/context/project-context.md` 中的验证命令是真实命令；
- 没有阻塞性开放问题，或开放问题已明确非阻塞；
- 保护区已在 `docs/context/ai-autonomy-policy.md` 配置；
- 已检查计划触发条件。

Agent 可以基于证据把过期行从 `ready` 降级为 `needs-*` 或 `blocked`。没有人工确认或人工认可的 owner-doc 证据时，Agent 不得把行升级为 `ready`、把自主性改为 `implement` 或清除 blocker。

## 状态值

- `idea` - 尚未准备实现。
- `needs-requirement` - 有原始输入，但没有可实现需求。
- `needs-design` - 有需求，但 owner doc 缺失或过期。
- `needs-plan` - owner docs 存在，但实现前需要计划。
- `needs-implementation` - 计划已存在，等待进入实现。
- `ready` - AI 可按自主性标签推进。
- `in-progress` - 正在实现或计划中。
- `blocked` - blocker 解决前不能推进。
- `done` - 已完成并验证。
