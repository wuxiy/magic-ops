# 已知良好基线

## 当前基线

截至 2026-07-10 的已知良好状态：

- 文档结构遵循 AGE 风格路由模型；
- 第一阶段范围已文档化；
- 架构发布信任边界已文档化；
- Maven 多模块脚手架已建立（Spring Boot 3.3.5，JDK 21）；
- magic-api 上游代码已通过 git subtree 引入（前缀 `magic-api-source`，暂不参与主构建）；
- 验证命令已替换为真实 Maven 命令；
- 脚本生命周期元模型已建立（Script、Draft、Version、Approval 实体）；
- 脚本状态机已实现（12 种状态，完整迁移路径覆盖）；
- 审计服务最小实现已就位（内存存储，关键审计阻断）；
- PostgreSQL 元数据库 Flyway 迁移脚本 V1 已建立；
- Console REST API 最小闭环已实现（创建、草稿、版本、提交、审批）；
- 发布包签名与验签已实现（SHA-256 + SHA256withRSA，canonical JSON，环境/keyId 校验）；
- Console 可构建并签名发布包，Runtime 可接收并验证发布包；
- Runtime 拒绝规则已测试覆盖（hash 不匹配、环境不匹配、未知 keyId）；
- 动态查询闭环已完成（真实 JDBC 执行、SQL Guard 只读、结果大小限制、执行审计）；
- 受控数据修复闭环已完成（dry-run、SQL Guard 写操作约束、回滚意图、关键审计阻断）；
- HTTP 接口适配闭环已完成（目标注册、allowlist 校验、AES/HMAC 加解密、脱敏、外部调用审计）；
- CryptoService 提供 AES-GCM 加解密、HMAC-SHA256 签名验证、敏感数据脱敏。

## 最近完整验证

2026-07-10：切片 3/4/5 动态查询 + 数据修复 + HTTP 适配闭环

- `mvn test`：通过（100 个测试，0 失败）
  - SQL Guard 测试 29 个（分类、只读策略、修复约束、危险语句拦截）
  - 查询执行测试 6 个（真实 JDBC、非 SELECT 拒绝、审计字段、结果限制）
  - 修复执行测试 8 个（dry-run、WHERE 检查、审计阻断、自由 UPDATE 拒绝）
  - HTTP 适配测试 9 个（目标注册、allowlist、trace ID、加解密、脱敏、关键审计）
  - 前序测试 48 个继续通过

2026-07-10：切片 2 发布包签名和 Runtime 验签

- `mvn test`：通过（48 个测试，0 失败）
  - 签名测试 8 个（签名/验签往返、篡改拒绝、canonical 稳定性）
  - Runtime 验签测试 5 个（合法接受、hash 拒绝、环境拒绝、keyId 拒绝、审计写入）
  - 前序测试 35 个继续通过

2026-07-10：切片 1 脚本生命周期和元数据模型

- `mvn compile`：通过
- `mvn test`：通过（36 个测试，0 失败）
  - 状态机测试 28 个（合法路径 + 非法拒绝 + 参数化覆盖）
  - 生命周期集成测试 4 个（DRAFT 到 APPROVED、拒绝、非法迁移、审批审计）
  - 基础模块测试 4 个

2026-07-10：切片 0 仓库脚手架

- `mvn compile`：通过
- `mvn test`：通过（3 个测试，0 失败）
- Console 启动验证：通过（HTTP 401/404 符合预期）
- Runtime 启动验证：通过（HTTP 401/404 符合预期）

## 待验证

- magic-api fork 代码整合（javax 到 jakarta 迁移）；
- Runtime API 契约；
- 发布包签名和验签（切片 2）；
- 审计服务持久化接入。
