# 初始项目决策

## 来源

本文件记录 2026-07-09 关于 MagicOps 的初始项目和架构讨论。

仓库最初包含：

- `magicops-project-doc.md` - 宽泛的产品、范围、路线图、风险和使用文档；
- `magicops-architecture.md` - 宽泛的架构、模块、数据模型、发布和迭代文档；
- 第一阶段范围决策的原始 ADR 位置；
- 原始领域词汇表位置。

这些事实已经路由到 `docs/requirements/`、`docs/design/` 和 `docs/architecture/` 下的 AGE owner docs。

## 人工确认决策

- 第一版先搭建项目，并建设第一阶段能力。
- 第一阶段包括动态查询 API、受控数据修复和 HTTP 接口适配。
- Arthas 线上诊断属于第二阶段。
- Console 到生产 Runtime 的发布方式是在线推送。
- 平台持有签名私钥。
- Runtime 只持有公钥验签材料。
- 数据修复和动态 API 共用脚本生命周期。
- Arthas 是 MagicOps 的核心差异化能力，不是边缘功能。

## 路由

- 第一阶段可实现范围：`docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md`
- 产品和用户行为基线：`docs/design/app-overview.md`
- 技术基线：`docs/architecture/system-baseline.md`
- 发布和 Runtime 信任边界：`docs/architecture/release-and-runtime.md`
- 架构决策：`docs/architecture/decisions/0001-first-stage-scope-and-runtime-release.md`
