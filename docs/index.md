# 文档索引

## 目的

`docs/` 是 MagicOps 的长期记忆和任务路由入口。

在修改流程、需求、设计、架构或实现前，先使用这里的文档。长期结论必须写入文件，而不是只留在聊天里。

该结构参考 Attractor-Guided Engineering 模板，并按 MagicOps 项目做了裁剪。

## 路由职责

- `docs/index.md` 负责导航和目录职责说明。
- `AGENTS.md` 负责 AI/Agent 工作规则和执行预期。
- `docs/context/` 负责必读上下文和事实源优先级。
- `docs/design/` 与 `docs/architecture/` 负责稳定项目吸引子。

## 优先阅读

| 如果你需要... | 先读 | 再读 |
|---|---|---|
| 理解 AI 必读上下文和当前项目状态 | `docs/context/README.md` | `docs/context/project-context.md`, `docs/context/ai-autonomy-policy.md`, `docs/context/codebase-map.md` |
| 理解事实源优先级 | `docs/context/source-of-truth-and-precedence.md` | 相关需求、设计或架构 owner doc |
| 理解默认工作流 | `docs/process/application-development-workflow.md` | `AGENTS.md` |
| 选择下一项工作 | `docs/backlog/README.md` | 当前需求和 owner docs |
| 理解项目目标和产品形态 | `docs/architecture/project-vision.md` | `docs/design/app-overview.md` |
| 理解第一阶段范围 | `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md` | `docs/architecture/decisions/0001-first-stage-scope-and-runtime-release.md` |
| 理解第二阶段生产就绪计划 | `docs/plans/2026-07-11-0000-second-stage-production-ready.md` | `docs/design/roles-and-permissions.md` |
| 理解第三阶段完善与 Arthas 计划 | `docs/plans/2026-07-11-0001-third-stage-refinement-and-arthas.md` | `docs/architecture/module-boundaries.md` |
| 理解第四阶段加固与诊断实现 | `docs/plans/2026-07-11-0002-fourth-stage-hardening-and-diagnosis.md` | `docs/architecture/diagnosis-design.md` |
| 理解混合 UI 架构计划 | `docs/plans/2026-07-11-0003-hybrid-ui-architecture.md` | `docs/architecture/system-baseline.md` |
| 理解产品行为和用户角色 | `docs/design/app-overview.md` | `docs/design/roles-and-permissions.md`, `docs/design/flow-overview.md` |
| 理解术语和领域语言 | `docs/design/domain-glossary.md` | 相关设计或架构文档 |
| 理解技术基线 | `docs/architecture/system-baseline.md` | `docs/architecture/module-boundaries.md` |
| 理解发布与 Runtime 信任边界 | `docs/architecture/release-and-runtime.md` | `docs/architecture/security-and-governance.md` |
| 理解模块职责 | `docs/architecture/module-boundaries.md` | `docs/architecture/system-baseline.md` |
| 查看历史架构决策 | `docs/architecture/decisions/` | 相关 owner doc |
| 查看初始文档审查 | `docs/audits/2026-07-09-0000-project-doc-architecture-review.md` | 审查中列出的 owner docs |
| 查看近期实现历史 | `docs/logs/index.md` | 最新日期日志 |
| 检查 known-good 验证状态 | `docs/testing/known-good-baselines.md` | 最新测试或日志记录 |

## 目录职责

- `docs/context/` - AI 必读上下文、事实源优先级和项目约定。
- `docs/backlog/` - 候选工作、AI 可接任务和路线图进度。
- `docs/process/` - 轻量开发工作流。
- `docs/input/` - 原始材料和初始决策。
- `docs/discussions/` - 需求澄清和未决问题记录。
- `docs/requirements/` - 可实现需求文档。
- `docs/design/` - 稳定产品、流程、角色和领域 owner docs。
- `docs/architecture/` - 稳定技术基线、模块边界、发布模型和架构决策。
- `docs/plans/` - 带关闭标准的执行计划。
- `docs/audits/` - 审查记录和审查方法。
- `docs/logs/` - 按日期归档的实现记忆。
- `docs/testing/` - 验证基线、手工测试和探索性测试记录。
- `docs/bugs/` - 复杂缺陷、回归和根因记录。
- `docs/analysis/` - 研究、比较和取舍记录。
- `docs/lessons/` - 从重复问题和恢复中沉淀的长期经验。
- `docs/skills/` - 可复用提示词、审查清单和方法。
- `docs/retrospectives/` - 交付后的差距复盘。
- `docs/references/` - 稳定查阅材料和维护辅助。
- `docs/archive/` - 经人工决定归档的非活跃文档。

## 核心原则

用文件保存长期事实。

- Input 记录需求从哪里来。
- 上下文记录项目强约束和事实源优先级。
- Requirements 记录要构建什么。
- Design 记录产品应该如何工作。
- Architecture 记录系统如何组织。
- Plans 记录非平凡切片如何关闭。
- Audits 用来挑战完成声明。
- Logs、testing、bugs、lessons 和 retrospectives 保存过程记忆。

## 命名规则

- 稳定 owner docs 使用稳定文件名。
- 时效性记录使用日期命名：`YYYY-MM-DD-HHMM-topic.md`。
- 日志使用 `docs/logs/<year>/<month-day>.md`。
