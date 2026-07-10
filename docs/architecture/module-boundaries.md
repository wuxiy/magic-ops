# 模块边界

## 边界原则

尽量保持 magic-api 执行核心稳定。MagicOps 治理能力应放在 resource、interceptor、module、signing 和 Runtime 边界中，而不是硬编码进脚本引擎。

## 模块

### magicops-core

职责：

- 脚本解析和执行集成；
- HTTP API 映射；
- 参数绑定；
- 上下文管理；
- 模块扩展机制；
- 拦截器钩子。

非职责：

- 审批流程；
- 签名；
- 用户管理；
- 环境特定策略存储。

### magicops-spring-boot-starter

职责：

- 自动配置 Runtime；
- 注册动态 API；
- 加载平台配置；
- 注册 security、audit、SQL Guard、Crypto 和 HTTP 模块；
- 强制生产只读模式。

### magicops-console

职责：

- 项目和环境管理；
- 数据源和 HTTP 目标管理；
- 在线编辑和调试；
- 草稿和版本管理；
- 审批流程；
- 签名和在线推送；
- 审计查询；
- 未来诊断中心入口。

### magicops-runtime

职责：

- 接收或加载已签名发布包；
- 校验发布包签名、环境和 Runtime 兼容性；
- 拒绝未签名或不兼容脚本；
- 执行动态 API；
- 根据已签名元数据执行权限和资源策略；
- 应用 SQL Guard 和 HTTP 目标限制；
- 写入执行审计。

禁止：

- 在线编辑；
- 保存草稿；
- 绕过审批；
- 加载未签名脚本；
- 在生产直接执行临时 SQL。

### magicops-governance

职责：

- 审批流程；
- 风险评级；
- 脚本状态机；
- 高风险操作控制；
- 回滚控制。

### magicops-sign

职责：

- 规范化发布包内容；
- 计算 content、metadata 和 policy hash；
- 使用平台私钥签名发布包；
- 向 Runtime 提供验签元数据；
- 记录签名审计。

### magicops-audit

职责：

- 操作审计；
- 执行审计；
- 发布审计；
- 审批审计；
- 未来诊断审计；
- 审计导出和报表。

高风险操作的关键审计写入失败必须阻断执行。

### magicops-crypto

职责：

- 请求/响应加解密 helper；
- 接口签名 helper；
- 防重放 helper；
- 敏感数据脱敏；
- 基于 key ID 的密钥访问。

脚本必须使用 key ID，不能包含原始密钥。

### magicops-sql-guard

职责：

- SQL 解析；
- SQL 类型分类；
- 数据源和表授权；
- 读写策略执行；
- 危险语句拦截；
- 行数和结果大小限制；
- dry-run 支持；
- 修复安全检查。

### magicops-http

职责：

- 已注册目标系统访问；
- 路径 allowlist；
- 超时、重试和熔断策略；
- 请求签名/加密集成；
- 响应解密和映射；
- trace ID 透传；
- 外部调用审计。

脚本不得调用任意 URL。

### 暂缓模块

| 模块 | 阶段 |
|---|---|
| magicops-redis | 后续 |
| magicops-mq | 后续 |
| magicops-diagnosis | 第二阶段 |

## Runtime 拦截器形态

Runtime 执行应设计为受保护的 `try/finally` 流水线：

```text
Request
  -> TraceId
  -> Authentication
  -> Permission
  -> Package/signature verification
  -> Rate limit
  -> Decrypt/mask boundary
  -> Resolve script
  -> Resource permission
  -> SQL Guard / HTTP target guard
  -> Execute
  -> Mask output
  -> Encrypt response
  -> Final audit write
  -> Response
```

具体拦截器实现可以不同，但成功和失败路径都必须显式处理审计与脱敏。
