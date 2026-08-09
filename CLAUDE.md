# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

MagicOps 是基于 `magic-api` fork 的医卫/政务内网动态开发与运维平台。核心能力：动态查询接口、受控数据修复、HTTP 接口适配、Arthas 线上 Java 诊断。

它是一个 Maven 多模块项目（Java 21、Spring Boot 3.3.5，`groupId=top.cywu`，Java 包前缀 `top.cywu.magicops`），由两个 Spring Boot 应用 + 一组共享库模块组成。项目文档以中文为主（见 `conventions.md`），代码标识符、配置 key、状态枚举保留英文。

## 构建与验证命令

| 目的 | 命令 |
|---|---|
| 编译 | `mvn compile` |
| 安装到本地仓库（跳过测试） | `mvn install -DskipTests` |
| 打包 | `mvn package -DskipTests` |
| 全量单元/集成测试 | `mvn test` |
| 单模块测试 | `mvn test -pl magicops-sign` |
| 单个测试类 | `mvn test -pl magicops-sign -Dtest=KeyProviderTest` |
| 单个测试方法 | `mvn test -pl magicops-sign -Dtest=KeyProviderTest#rejectsEphemeralKeysInProd` |
| E2E 闭环测试 | `bash docs/testing/e2e/e2e-closed-loop-test.sh`（前置：`mvn install -DskipTests`，Java 21） |
| Console 本地启动 | `java -jar magicops-console/target/magicops-console-0.1.0-SNAPSHOT.jar`（端口 8080） |
| Runtime 本地启动 | `java -jar magicops-runtime/target/magicops-runtime-0.1.0-SNAPSHOT.jar`（端口 8081） |
| Docker 一键启动 | `docker-compose up`（凭据经 `.env` 注入，复制 `.env.example`） |

Console 默认端口 8080，Runtime 默认端口 8081。默认管理员：`admin / magicops-admin`（仅非 prod profile）。E2E 脚本里审批用 `approver / appr123`。

> 重要：只有在 `docs/context/project-context.md` 中的真实验证命令真实执行通过后，才能声称构建/测试验证成功。不要凭空声称成功。

## 架构总览

理解 MagicOps 的关键是 **Console / Runtime 信任边界**（详见 `docs/architecture/release-and-runtime.md`、`security-and-governance.md`）：

- **magicops-console**（管理/运维网）：在线编辑调试、审批、签名、**在线推送发布包到 Runtime**、审计查询。持有签名私钥。
- **magicops-runtime**（生产/业务网）：接收已签名发布包 → 验签 → 原子加载 → 只读执行动态 API → SQL Guard / HTTP 目标守卫 → 写执行审计。**只持有公钥验签材料，绝不持有私钥，绝不暴露编辑端点。**

发布链路（Console 在线推送，非离线包）：Console 用平台私钥签名发布包（覆盖脚本内容、路由、数据源/HTTP/key 权限元数据、风险策略、环境、版本、发布人/时间），经 REST 推送到 Runtime；Runtime 按拒绝规则校验（未签名、未知 keyId、环境/版本不兼容、hash 不匹配、状态非法、缺权限元数据、未授权 Console 身份 → 拒绝）。签名输入是规范化的字节序列（UTF-8、JSON 字典序、LF 换行、SHA-256），不是 zip 二进制。回滚必须表现为一次已签名发布动作。

Runtime 执行是受保护的 `try/finally` 流水线（`docs/architecture/module-boundaries.md`）：TraceId → Auth → Permission → 包/签名校验 → 限流 → 解密/脱敏边界 → 解析脚本 → 资源权限 → SQL Guard / HTTP 守卫 → 执行 → 脱敏输出 → 加密响应 → 最终审计写入。成功和失败路径都必须显式处理审计与脱敏。

### 模块依赖层

共享库模块均依赖 `magicops-core`（脚本解析执行集成、HTTP API 映射、参数绑定、模块扩展机制、拦截器钩子）：

- `magicops-governance` — 审批流程、风险评级、脚本状态机、回滚控制
- `magicops-audit` — 操作/执行/发布/审批审计；**高风险操作的关键审计写入失败必须阻断执行**
- `magicops-sign` — 发布包规范化、content/metadata/policy hash、私钥签名、Runtime 验签元数据、签名审计
- `magicops-sql-guard` — 基于 **JSQLParser 解析树**做 SQL 分类与表提取（**禁止字符串前缀匹配**）、表级白名单、dry-run、修复安全检查
- `magicops-http` — 已注册目标访问、路径 allowlist、超时/重试/熔断、trace ID 透传；**脚本不得调用任意 URL**
- `magicops-crypto` — 请求/响应加解密、接口签名、防重放、敏感数据脱敏、基于 keyId 的密钥访问（BouncyCastle，SM4 国密）
- `magicops-diagnosis` — Arthas 诊断中心（隧道客户端，`enabled=false` 时模拟降级）

两个应用聚合这些库：
- `magicops-console` 依赖 core/governance/audit/sign/sql-guard/crypto/diagnosis + magic-api-spring-boot-starter
- `magicops-runtime` 依赖 core/audit/sign/sql-guard/http/crypto（**不含 governance、不含 diagnosis**）

`magicops-examples` 是用法示例。`magic-api-source/` 是 magic-api 上游的 **git subtree，不参与主构建**（主构建通过依赖消费 magic-api 制品），改它不影响 `mvn` 构建。

## 技术栈与配置

- **后端**：Java 21、Spring Boot 3.3.5、Spring Security、Spring Data JPA、Flyway、JSQLParser 4.9、BouncyCastle
- **数据库**：PostgreSQL（prod）/ H2 in-memory（默认 dev，`MODE=PostgreSQL`）。Schema 由 Flyway `V1`-`V9` 迁移管理，`ddl-auto=none`（dev）/ `validate`（prod）。迁移脚本在 `magicops-console/src/main/resources/db/migration/`
- **认证/授权**：数据库用户 + BCrypt + UserDetailsService；RBAC 7 角色 17 权限；`@PreAuthorize` 方法级 + `@ResourcePermission` 资源级 + AOP
- **前端**：Vue 3 + Element Plus 2.9（按需引入）+ Vite + Pinia + vue-router，位于 `magicops-console/frontend/`。`base: '/console/'`，`npm run build` 产物输出到 Console 的 `src/main/resources/static/console`（由 Console 静态托管）。`npm run dev` 时 Vite 把 `/api` 代理到 `localhost:8080`

### Spring Profile

- **默认（dev）**：H2 内存库、`allow-ephemeral-keys` 允许临时签名密钥、push-secret 为空时收包端点放行告警
- **prod**：PostgreSQL、`ddl-auto=validate`、`allow-ephemeral-keys: false`（**缺持久化密钥时启动 fail-fast**）、push-secret 缺失时收包 **fail-closed 拒绝所有推送**。签名密钥装配优先级：KeyStore（JKS/PKCS12，`magicops.sign.keystore.*`）→ 环境变量（`MAGICOPS_PRIVATE_KEY/PUBLIC_KEY`）→ 临时密钥（仅非 prod）

签名密钥装配由 `magicops-sign` 的 `KeyProvider`/`KeyStoreKeyProvider` 实现；prod fail-fast 行为有 `KeyProviderTest` 覆盖。

## 文档系统与工作规则（必读）

MagicOps 采用轻量 **Attractor-Guided Engineering (AGE)** 工作流。**仓库是长期事实源，聊天只是临时工作界面。** 重要需求、设计决策、架构决策、计划、验证证据必须落到 `docs/` 下。开始非平凡工作前先读 `docs/index.md`、`AGENTS.md`、`docs/context/`（尤其 `project-context.md`、`codebase-map.md`、`ai-autonomy-policy.md`、`source-of-truth-and-precedence.md`）。

### 事实源优先级

| 问题 | 主事实源 |
|---|---|
| 现在该构建什么 | `docs/requirements/` |
| 当前支持的产品行为 | `docs/design/` |
| 当前支持的技术结构 | `docs/architecture/` |
| 数据库 schema | schema/model 产物（Flyway 迁移、Entity）优先于文档意图 |
| API 契约 | 可执行/schema 级契约（OpenAPI、路由定义、契约测试）优先于文本文档 |
| 这个切片如何执行关闭 | `docs/plans/` |
| 实际发生了什么 | `docs/logs/`（按 `<year>/<month-day>.md` 归档） |

冲突处理：现有代码与 owner docs 不一致时视为实现漂移或文档漂移，**不要静默选择其一**；解决冲突若会改变用户可见行为、数据/模型、API、认证/权限、发布或外部集成行为，**停下来请求确认**。

### 保护区（plan-first / ask-first，不得擅自放松）

`docs/context/ai-autonomy-policy.md` 定义自主性等级 `implement` / `plan-first` / `ask-first` / `research-only` / `blocked`。以下区域默认需先建计划或先询问（附所需证据）：

- 认证与授权（plan-first）
- 审批、签名与发布（ask-first，需 owner doc + 测试 + 人工确认）
- Runtime 生产执行（ask-first）
- SQL Guard 与数据修复（ask-first，需回滚证明）
- 审计可靠性与敏感数据脱敏（plan-first）
- 密钥管理与加解密（ask-first，需安全审查）
- 外部 HTTP 集成行为（plan-first，需契约测试）
- Arthas 诊断命令（ask-first）
- 部署与环境行为（plan-first）

### 计划触发条件

当工作改变 API 契约、数据库/模型形态、认证/权限、审批/签名/Runtime 发布/部署、外部集成、数据修复或 SQL Guard 行为、审计可靠性或脱敏、跨模块架构时，**必须先在 `docs/plans/` 建计划**。只有小、局部、低风险、且被 owner doc 和验证路径覆盖的改动才可跳过正式计划。

### 文档维护（完成非平凡变更后）

1. 更新 `docs/design/` 或 `docs/architecture/` 下相关 owner docs；
2. 写 `docs/logs/<year>/<month-day>.md` 日记；
3. 验证达到有意义的 known-good 状态时，更新 `docs/testing/known-good-baselines.md`。

找不到被引用的文档时，先查 `docs/archive/`，再判断它不存在。

## 当前状态与遗留事项

参考 `docs/context/project-context.md` 获取最新状态。截至第五阶段切片 32：`mvn test` 通过（277 个测试），E2E 闭环 12 步通过。仍不完整/未验证：magic-api fork 的 javax→jakarta 整合（切片 16）、达梦数据库真实环境验证（当前用 H2）、Docker Compose 端到端验证（Dockerfile 已建，未跑 `docker-compose up` + E2E on PostgreSQL）。第五阶段计划见 `docs/plans/2026-08-07-0000-fifth-stage-production-gate.md`。
