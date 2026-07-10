# 功能清单

## 第一阶段功能

| 功能 | 状态 | 归属 | 说明 |
|---|---|---|---|
| 项目脚手架 | planned | architecture | 基于 magic-api fork 的 MagicOps 单仓库结构。 |
| 动态查询 API | planned | design + architecture | 查询优先的动态 HTTP API，SQL 默认只读。 |
| 受控数据修复 | planned | design + architecture | 共用脚本生命周期；写路径要求 dry-run、审批、审计和回滚记录。 |
| HTTP 接口适配 | planned | design + architecture | 注册目标系统、路径 allowlist、字段映射、加解密/签名和审计。 |
| 脚本生命周期 | planned | design + architecture | 从 draft 到 published/rollback 的状态流转。 |
| 审批流 | planned | design + architecture | 高风险脚本和写操作必须审批。 |
| 操作审计 | planned | architecture | Console 操作。 |
| 执行审计 | planned | architecture | Runtime 执行和外部操作。 |
| 签名发布 | planned | architecture | 平台私钥签名，Runtime 公钥验签。 |
| Runtime 只读模式 | planned | architecture | 生产 Runtime 不可编辑，也不可加载未签名脚本。 |
| SQL Guard | planned | architecture | 查询控制和写风险防护。 |
| CryptoModule 最小集 | planned | architecture | 第一阶段接口适配所需 SM4、AES、RSA、HMAC 和脱敏。 |

## 暂缓功能

| 功能 | 目标阶段 | 说明 |
|---|---|---|
| Arthas 诊断中心 | 第二阶段 | 核心差异化能力，需要独立安全模型。 |
| Redis 完整治理 | 后续 | 第一阶段不需要完整 Redis 操作治理。 |
| MQ 完整治理 | 后续 | 第一阶段不需要完整 MQ 操作治理。 |
| 多租户管理 | 后续 | 等项目级隔离需求被验证后再做。 |
| 离线发布包作为主路径 | 后续 | 第一阶段使用在线推送。 |
| 完整 UI 重写 | 后续 | 第一阶段复用或轻量扩展 magic-editor。 |
| 报表/表单设计器 | 第一阶段范围外 | 不属于 MagicOps 核心定位。 |

## 功能边界

动态查询 API 默认只读，不应成为隐藏的数据修复路径。

数据修复与动态 API 使用相同脚本生命周期，但必须通过风险等级、资源、审批策略、SQL Guard 模式和执行审计区分。

接口适配可以做转换、签名、加密并调用已注册 HTTP 目标，但不得变成任意外部网络访问。

Arthas 诊断不得混入第一阶段 Runtime 执行路径。它需要独立的会话、命令、输出和安全模型。
