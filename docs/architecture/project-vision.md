# 项目愿景

## 愿景

MagicOps 应成为面向医院、卫健委、政务云和区域医疗内网项目的受控、可审计、轻量级动态开发与运维平台。

它应让技术项目团队快速响应小型集成、数据核查和修复需求，同时不绕过治理，也不把生产环境变成失控脚本执行面。

## 定位

MagicOps 是：

- 动态查询与集成运维工作台；
- 受控数据修复工具；
- 接口适配和内网网关工具；
- 第二阶段 Java 线上诊断平台。

MagicOps 不是：

- 面向普通业务用户的低代码平台；
- 标准 Java 工程化研发的替代品；
- 诊疗、结算、费用、医嘱或 EMR 流程的核心交易引擎；
- 不受控制的生产脚本控制台。

## 长期吸引子

MagicOps 应持续向以下形态收敛：

```text
Console owns edit, debug, approval, signing, and release.
Runtime owns read-only verified execution.
Governance owns permission, risk, audit, and approval.
Modules expose controlled SQL, HTTP, crypto, Redis, MQ, and diagnosis capabilities.
Every production action is signed, authorized, masked, and auditable.
```

## 阶段

| 阶段 | 目标 |
|---|---|
| MVP | 项目脚手架和第一阶段受控闭环：动态查询、数据修复、接口适配。 |
| V1.0 | 可试点内网部署，具备更强权限、回滚和日志能力。 |
| V1.5 | 作为核心差异化的 Arthas 诊断中心。 |
| V2.0 | 可复用的医卫/政务集成与运维基础设施。 |
