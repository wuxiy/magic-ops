# 领域词汇表

## 核心对象

| 术语 | 定义 |
|---|---|
| Script | 可编辑、调试、审批、发布和执行的平台对象。动态 API、动态查询、数据修复和接口适配都以 Script 为基础。 |
| Draft | Script 的可编辑草稿，只能在 Console 中创建和修改。 |
| Script Version | 从 Draft 固化出的不可变版本，包含内容、路由、风险等级、资源声明和 hash。 |
| Approval | 针对 Script Version 或高风险执行行为的审批记录。 |
| Publish Package | Console 生成、签名并推送到 Runtime 的发布单元。 |
| Runtime | 生产执行端，只加载已签名发布包，不提供编辑能力。 |
| Console | 管理端，负责编辑、调试、审批、签名、发布和审计查询。 |
| Gateway | 独立 Runtime 形态，用于集中接口适配和系统集成。 |

## 第一阶段场景

| 术语 | 定义 |
|---|---|
| Dynamic Query API | 面向临时统计、数据核查和内部查询的动态 HTTP API，默认只读。 |
| Controlled Data Repair | 基于 Script 的受控写操作，要求 dry-run、审批、签名、Runtime 验签、审计和回滚记录。 |
| Interface Adaptation | 基于 Script 调用已注册 HTTP 目标系统，包含字段映射、报文转换、加密、签名和响应转换。 |

## 治理与安全

| 术语 | 定义 |
|---|---|
| Online Push | Console 将已签名 Publish Package 直接推送到生产 Runtime。 |
| Platform Private Key | 平台侧持有、用于签名发布包的私钥。Runtime 永不持有。 |
| Runtime Public Key | Runtime 用于验证发布包的公钥验签材料。 |
| SQL Guard | 对脚本发起的 SQL 进行解析、分类、授权、限制、dry-run 和审计的模块。 |
| Resource Permission | 资源级授权，覆盖数据源、表、HTTP 目标、key reference 和未来 Redis/MQ 资源。 |
| Risk Level | Script Version 或执行行为的风险分类，用于决定审批、复核和执行控制。 |
| Execution Audit | Runtime 针对 Script 执行产生的审计记录。 |

## 第二阶段

| 术语 | 定义 |
|---|---|
| Diagnosis Center | 第二阶段 Java 线上诊断中心。 |
| Arthas Session | 由 Diagnosis Center 管理的诊断会话。 |
| Diagnosis Template | 预定义、可审计、带参数约束的诊断命令模板。 |
