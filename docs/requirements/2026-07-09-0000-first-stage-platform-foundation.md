# 第一阶段平台基础需求

## 状态

已接受，作为第一阶段计划基线。

## 目标

建立 MagicOps 项目基础，并跑通第一阶段三个受控业务闭环：

1. 动态查询接口；
2. 受控数据修复；
3. HTTP 接口适配。

第一阶段要证明：MagicOps 能保留 magic-api 的开发灵活性，同时补上生产级治理能力，包括认证、权限、审批、签名、Runtime 只读执行和审计。

## 范围内

### 项目基础

- 使用 `git subtree` 方式引入或维护 `magic-api` 源码，作为第一阶段技术基础。
- 建立 MagicOps 单仓库模块结构。
- Maven `groupId` 使用 `top.cywu`，Java package 前缀默认随 `top.cywu.magicops` 组织。
- 第一版采用多应用形态，至少区分 Console 应用和 Runtime 应用。
- 建立构建、本地运行、编译、测试和打包命令。
- 建立基于 PostgreSQL 的资源存储。
- 第一阶段 UI 复用 `magic-editor`，Console 接受最小页面/API 驱动闭环，不追求漂亮界面。

### 共享治理

- 认证和基于角色的访问控制。
- Console 操作审计。
- Runtime 脚本执行审计。
- 脚本生命周期：draft、debugged、submitted、reviewing、approved、signed、pushing、published、rejected、publish failed、disabled、rolled back。
- Script Version 上的风险等级和资源声明。
- 高风险操作审批。
- 平台私钥签名。
- Runtime 公钥验签。
- Console 在线推送发布包。
- 生产 Runtime 只读执行。

### 动态查询接口

- 在 Console 中编写查询脚本。
- 在非生产或受控测试上下文调试。
- 通过审批和签名发布。
- Runtime 只加载已签名、已发布的查询脚本。
- SQL 默认只读。
- 查询执行记录 trace ID、script ID、版本、调用方、请求 IP、数据源、SQL 摘要、结果大小、耗时、成功/失败和内容 hash。

### 受控数据修复

- 数据修复与动态 API 使用同一套 Script 和 Script Version 生命周期。
- 修复脚本必须声明风险等级和资源。
- 修复必须执行 dry-run。
- 第一版数据修复不支持自由 UPDATE/DELETE，只支持受限 SQL 模板或单表带主键/WHERE 的写操作。
- 第一版 SQL Guard 和 dry-run 只支持达梦数据库业务数据源。
- 修复在生产执行前必须审批。
- 修复必须生成影响范围报告。
- 修复必须定义回滚意图，或明确记录为什么无法回滚。
- SQL Guard 必须在执行前强制写操作约束。
- 高风险修复的关键审计写入失败时必须阻断执行。

### HTTP 接口适配

- 注册 HTTP 目标系统。
- 限制脚本只能访问已注册目标系统和 allowlist 路径。
- 支持字段映射和响应转换。
- 支持第一阶段加解密/签名需求：SM4、AES、RSA、HMAC 和脱敏。
- 记录带 trace ID 透传的外部调用审计。
- 不允许脚本访问任意 URL。

## 第一阶段范围外

- Arthas 诊断中心实现。
- 完整 Redis / MQ 治理。
- 完整 UI 重写。
- 多租户管理。
- 将离线发布包作为主发布路径。
- 报表设计器或表单设计器。
- 完整数据交换平台。
- 替代核心诊疗、计费、医嘱、EMR 或结算流程中的 Java 工程化开发。

## 验收标准

- 仓库具备可运行的 MagicOps 脚手架，真实验证命令记录在 `docs/context/project-context.md`。
- 动态查询 API 可以完成 `创建脚本 -> 调试 -> 提交 -> 审批 -> 签名 -> 推送 -> Runtime 验签加载 -> 执行 -> 审计` 最低闭环。
- 数据修复任务可以使用共享生命周期，并具备 dry-run、审批、签名发布、Runtime 执行、审计和回滚记录。
- HTTP 接口适配脚本只能调用已注册目标，并可使用配置好的签名/加密能力。
- 生产 Runtime 拒绝未签名、未审批或环境不兼容的发布包。
- 审计覆盖 Console 操作和 Runtime 执行的成功与失败路径。
- 保护区行为在实现完成前必须有测试或明确验证计划覆盖。

## 控制性 Owner Docs

- 产品概览：`docs/design/app-overview.md`
- 功能清单：`docs/design/feature-inventory.md`
- 流程和状态基线：`docs/design/flow-overview.md`
- 角色和权限：`docs/design/roles-and-permissions.md`
- 技术基线：`docs/architecture/system-baseline.md`
- 发布与 Runtime：`docs/architecture/release-and-runtime.md`
- 安全与治理：`docs/architecture/security-and-governance.md`
- 模块边界：`docs/architecture/module-boundaries.md`

## 开放问题

- 第一版数据库 schema 迁移机制。
