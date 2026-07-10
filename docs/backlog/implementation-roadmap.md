# 实施路线图

## 目的

MagicOps 阶段级进度视图。详细执行计划属于 `docs/plans/`。

## 路线图

| 阶段 | 目标 | 状态 | Owner Docs |
|---|---|---|---|
| MVP | 项目脚手架和三个第一阶段受控闭环 | **done** | `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md` |
| V1.0 | 生产可用（用户管理/RBAC/审计持久化/Docker） | **done** | `docs/plans/2026-07-11-0000-second-stage-production-ready.md` |
| V1.2 | 功能完善（资源权限/拦截器/密钥轮换） | **done** | `docs/plans/2026-07-11-0001-third-stage-refinement-and-arthas.md` |
| V1.5 | Arthas 诊断中心 | designed | `docs/requirements/2026-07-11-0002-arthas-diagnosis-center.md` |
| V1.6 | magic-api Fork 整合 | planned | 切片 16 |
| V2.0 | 医卫/政务可复用基础设施 | deferred | 路线图文档 TBD |

## 已完成

### MVP（第一阶段，切片 0-5）

- 项目脚手架（Spring Boot 3.3.5 + JDK 21 + Maven 多模块）；
- 脚本生命周期（12 种状态 + 状态机 + JPA 实体 + Flyway V1）；
- 签名发布（SHA-256 + SHA256withRSA + canonical JSON + 环境/keyId 校验）；
- 动态查询闭环（真实 JDBC + SQL Guard 只读 + 结果大小限制）；
- 受控数据修复闭环（dry-run + SQL Guard 写约束 + 回滚意图 + 关键审计阻断）；
- HTTP 接口适配闭环（目标注册 + allowlist + AES/HMAC + 脱敏）；
- E2E 10 步闭环验证。

### 生产可用（第二阶段，切片 6-11）

- 元数据持久化（Flyway V1-V6 + 19 张表 + 15 个 JPA 实体 + 12 个 Repository）；
- 用户管理与认证（BCrypt + UserDetailsService + DataInitializer）；
- RBAC 权限控制（7 种角色 + 17 种权限 + @PreAuthorize）；
- 审计持久化与脱敏（JPA + AuditMaskingService + 分页查询 API）；
- 密钥管理（JKS + SM4 国密 + 多 keyId）；
- Docker Compose 部署配置。

### 功能完善（第三阶段，切片 12-15）

- Docker 与生产配置（Dockerfile + Runtime prod profile）；
- 资源级权限（@ResourcePermission + AOP + SpEL）；
- Runtime 拦截器流水线（TraceId + 限流 + 响应脱敏）；
- 密钥轮换服务（生成/轮换/共存/移除）。

## 暂缓

- 完整 UI 重写；
- 多租户管理；
- 离线发布包作为主流程；
- Redis / MQ 完整治理；
- 大规模数据交换平台。
