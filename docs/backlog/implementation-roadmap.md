# 实施路线图

## 目的

MagicOps 阶段级进度视图。详细执行计划属于 `docs/plans/`。

## 路线图

| 阶段 | 目标 | 状态 | Owner Docs |
|---|---|---|---|
| MVP | 项目脚手架和三个第一阶段受控闭环 | not started | `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md`, `docs/architecture/system-baseline.md` |
| V1.0 | 可试点内网平台 | not started | `docs/design/app-overview.md`, `docs/architecture/release-and-runtime.md` |
| V1.5 | Arthas 诊断中心 | deferred | 第二阶段文档 TBD |
| V2.0 | 医卫/政务可复用基础设施 | deferred | 路线图文档 TBD |

## MVP 范围

MVP 包括：

- magic-api fork 和 MagicOps 单仓库结构；
- Spring Security 集成；
- PostgreSQL 资源存储；
- 操作审计；
- 执行审计；
- 草稿/版本/审批/发布生命周期；
- 平台私钥签名；
- Console 在线推送发布包；
- Runtime 公钥验签和只读执行；
- 第一版 SQL Guard；
- 动态查询 API；
- 受控数据修复闭环；
- 接口适配所需最小 CryptoModule。

MVP 暂缓：

- 完整 UI 重写；
- 多租户管理；
- 离线发布包作为主流程；
- Redis / MQ 完整治理；
- 完整 Arthas 诊断中心；
- 大规模数据交换平台。
