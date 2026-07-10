# 项目与架构文档审查

## 日期

2026-07-09

## 范围

审查 AGE 重构前的 MagicOps 初始项目和架构文档：

- `magicops-project-doc.md`
- `magicops-architecture.md`

## 发现

### P1：MVP 范围过宽

第一阶段范围混合了项目脚手架、认证、审计、审批、签名、Runtime、Crypto、SQL Guard、HTTP、Redis/MQ 和诊断相关关注点。

处理：

- 第一阶段现在聚焦动态查询 API、受控数据修复和 HTTP 接口适配；
- Arthas 诊断属于第二阶段；
- Redis/MQ 完整治理后移。

Owner docs：

- `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md`
- `docs/architecture/decisions/0001-first-stage-scope-and-runtime-release.md`

### P1：发布包信任边界需要更强定义

初始架构描述了发布包签名，但没有显式把权限元数据、目标权限、策略、环境和 Runtime 兼容性绑定到签名范围。

处理：

- 发布包签名范围现在包括脚本、路由映射、权限、HTTP 目标、key refs、策略、环境、Runtime 兼容性和发布人身份。

Owner doc：

- `docs/architecture/release-and-runtime.md`

### P1：Runtime 审计与脱敏顺序需要明确归属

初始拦截器链可能被理解为执行后再审计、审计后再脱敏。

处理：

- Runtime 执行现在被描述为受保护流水线，成功和失败路径都被审计，脱敏被视为持久化存储边界。

Owner docs：

- `docs/architecture/module-boundaries.md`
- `docs/architecture/security-and-governance.md`

### P1：SQL Guard 对生产数据修复来说过于高层

原 SQL Guard 章节列出了默认 SQL 规则，但没有把 dry-run、影响报告、行数限制、回滚记录和审计阻断定义为第一阶段需求。

处理：

- 数据修复现在有明确第一阶段验收标准；
- SQL Guard 安全规则由架构文档负责。

Owner docs：

- `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md`
- `docs/architecture/security-and-governance.md`

### P2：领域术语和生命周期需要稳定 owner

初始文档使用了 Script、Runtime、Console、数据修复和诊断等术语，但没有给它们单一路由点。

处理：

- 领域语言移到 `docs/design/domain-glossary.md`；
- 脚本生命周期移到 `docs/design/flow-overview.md`。

## 后续

- 脚手架仓库前先创建实现计划。
- 脚手架存在后尽快建立真实构建和验证命令。
- 实现 Arthas 前先编写第二阶段诊断需求。
