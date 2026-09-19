<div align="center">
  <img src="./magicops-console/src/main/resources/static/favicon.ico" alt="MagicOps" width="72" height="72">
  <h1>MagicOps</h1>
  <p><strong>面向医卫与政务内网的受控动态开发、接口集成、数据修复与 Java 诊断平台</strong></p>
  <p>
    <a href="https://github.com/wuxiy/magic-ops/actions/workflows/ci.yml"><img src="https://github.com/wuxiy/magic-ops/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
    <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21">
    <img src="https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.3.5">
    <a href="./LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="Apache License 2.0"></a>
  </p>
  <p>
    <a href="#核心能力">核心能力</a> ·
    <a href="#快速开始">快速开始</a> ·
    <a href="#部署配置">部署配置</a> ·
    <a href="#项目结构">项目结构</a> ·
    <a href="#验证">验证</a> ·
    <a href="#文档导航">文档导航</a>
  </p>
</div>

MagicOps 基于 `magic-api` 构建，将在线脚本开发与生产执行分离：Console 负责编辑、调试、审批、签名和发布，Runtime 只加载经过验签的发布包，并在执行前应用权限、SQL Guard、HTTP 目标限制和审计策略。

> [!IMPORTANT]
> 当前基线适合在受控内网中开展只读动态查询灰度。达梦数据库真实环境验证仍待完成；在此之前，不建议将数据修复或整个平台直接用于生产日常运维，也不要将 Console、Runtime 或 Arthas Tunnel 暴露到公网。

## 核心能力

- **动态查询接口**：脚本引用执行、多数据源声明路由、表级授权、结果大小限制和全链路审计。
- **受控数据修复**：强制 dry-run、审批凭据、SQL AST 检查、事务执行、影响行数上限和回滚记录。
- **HTTP 接口适配**：注册目标、路径 allowlist、字段转换、trace ID 透传，以及 AES-GCM、SM4、HMAC 和脱敏能力。
- **签名发布链路**：Console 审批后构建并签名发布包，Runtime 校验环境、版本、内容 hash、策略 hash 和 key ID 后原子激活。
- **治理与审计**：Spring Security、7 类角色、资源级权限、提交与审批分离、敏感数据脱敏及 PostgreSQL 持久化审计。
- **可观测与诊断**：健康检查、Prometheus 指标、结构化日志，以及带模板约束、会话控制和输出脱敏的 Arthas 诊断链路。

## 架构概览

```text
管理网 / 运维网
  └── magicops-console :8080
        ├── Portal / magic-editor / 管理控制台
        ├── 编辑、调试、审批、签名
        └── 推送已签名发布包
                    │
                    ▼
生产网 / 业务网
  └── magicops-runtime :8081
        ├── 验签、激活与重启重载
        ├── Query / Repair / HTTP Adapter
        └── SQL Guard、资源权限与执行审计

Console ───────┐
Runtime ───────┴── PostgreSQL（Console 管理 Flyway，Runtime 仅校验 schema）
```

生产执行采用脚本引用模式。调用方只提交 `scriptId` 和必要参数，Runtime 从当前已激活的签名包解析脚本；裸 SQL 和裸 HTTP 目标请求会被拒绝。

## 当前范围

| 能力 | 当前状态 | 边界 |
|---|---|---|
| 只读动态查询 | 可灰度 | PostgreSQL E2E 已有验证记录；业务数据源需显式声明和授权 |
| 数据修复 | 已实现，谨慎启用 | 真实达梦方言、dry-run 与回滚流程仍需目标环境验证 |
| HTTP 适配 | 已实现 | 只能访问已注册目标和 allowlist 路径 |
| Arthas 诊断 | 核心链路与模拟模式可用 | 默认未连接真实 Tunnel Server，生产接入需独立安全验证 |
| `magic-api` fork | 源码已纳入仓库 | `magic-api-source/` 暂不参与 MagicOps 主构建 |

## 快速开始

### 前置要求

- JDK 21
- Maven
- Node.js 与 npm（构建 Console 和 Diagnosis 两个 Vue 前端）
- Docker 与 Docker Compose（仅容器化启动和 PostgreSQL E2E 需要）

### 本地开发模式

本地模式使用 H2 内存数据库和临时签名密钥，适合快速查看界面、API 与开发流程。前端构建尚未接入 Maven，需要先生成两个 SPA 的静态资源：

```bash
cd magicops-console/frontend
npm ci
npm run build

cd ../frontend-diagnosis
npm ci
npm run build

cd ../..
```

```bash
mvn package -DskipTests
```

分别启动 Console 与 Runtime：

```bash
java -jar magicops-console/target/magicops-console-0.1.0-SNAPSHOT.jar
```

```bash
java -jar magicops-runtime/target/magicops-runtime-0.1.0-SNAPSHOT.jar
```

打开 [http://localhost:8080](http://localhost:8080) 进入 Portal。

> [!NOTE]
> 非 `prod` profile 首次启动会创建开发账号：`admin / magicops-admin`、`developer / dev123`、`approver / appr123`。这些账号只用于本地开发；生产 profile 不创建测试账号，且管理员密码必须显式配置。

### Docker Compose 模式

1. 准备部署配置：

   ```bash
   cp .env.example .env
   ```

2. 生成 RSA 密钥对，将命令输出中的私钥、公钥和 key ID 填入 `.env`，并替换其余全部 `change-me-*` 值：

   ```bash
   mvn -q -pl magicops-sign -am compile
   java -cp magicops-sign/target/classes \
     top.cywu.magicops.sign.key.KeyPairGeneratorUtil
   ```

3. 按“本地开发模式”中的命令构建两个前端和后端 JAR。

4. 构建镜像并启动 PostgreSQL、Console 和 Runtime：

   ```bash
   docker compose up --build -d
   docker compose ps
   ```

5. 停止服务：

   ```bash
   docker compose down
   ```

> [!WARNING]
> 不要在共享或生产环境使用 `.env.example` 中的示例密码。`.env` 已被 Git 忽略，真实密码、私钥和 token 不应提交到仓库或写入脚本。

### 服务入口

| 入口 | 地址 | 说明 |
|---|---|---|
| Portal | `http://localhost:8080/` | 统一入口与登录 |
| 脚本编辑器 | `http://localhost:8080/magic/web/` | 开发和受控调试，不承担生产执行 |
| 管理控制台 | `http://localhost:8080/console/` | 审批、用户、密钥、发布和审计 |
| 诊断中心 | `http://localhost:8080/diagnosis/` | Arthas 会话与模板化命令入口 |
| Console 健康检查 | `http://localhost:8080/actuator/health` | Spring Boot Actuator |
| Runtime 健康检查 | `http://localhost:8081/actuator/health` | Spring Boot Actuator |
| Prometheus 指标 | `/actuator/prometheus` | Console 与 Runtime 均提供 |

## 部署配置

根目录 `.env.example` 是 Docker Compose 的配置清单。生产 profile 缺少关键密钥或 Runtime 密码时会拒绝启动或拒绝发布。

| 变量 | 必需 | 说明 |
|---|---:|---|
| `DB_PASSWORD` | 是 | PostgreSQL、Console 与 Runtime 共用的数据库密码 |
| `MAGICOPS_ADMIN_PASSWORD` | 是 | 生产环境首次初始化 `admin` 用户的密码 |
| `MAGICOPS_PRIVATE_KEY` | 是¹ | Base64 PKCS#8 RSA 私钥 |
| `MAGICOPS_PUBLIC_KEY` | 是¹ | Base64 X.509 RSA 公钥，供 Runtime 验签 |
| `MAGICOPS_KEY_ID` | 否 | 签名密钥标识，默认 `magicops-default` |
| `MAGICOPS_RUNTIME_SHARED_SECRET` | 是 | Console 推送与 Runtime 收包使用的共享密钥 |
| `RUNTIME_USERNAME` | 否 | Runtime Basic Auth 用户名，默认 `runtime` |
| `RUNTIME_PASSWORD` | 是 | Runtime Basic Auth 密码 |

¹ 也可以通过 `MAGICOPS_KEYSTORE_PATH`、`MAGICOPS_KEYSTORE_PASSWORD` 和 `MAGICOPS_KEYSTORE_TYPE` 使用 JKS/PKCS12。

## 项目结构

| 路径 | 职责 |
|---|---|
| `magicops-core/` | 脚本执行集成、共享模型和扩展边界 |
| `magicops-console/` | 编辑、审批、签名、发布、配置、用户与审计入口 |
| `magicops-runtime/` | 已签名发布包的验签、激活、执行与审计 |
| `magicops-governance/` | 生命周期、风险、审批和高风险操作控制 |
| `magicops-audit/` | 操作、执行、发布和安全审计 |
| `magicops-sign/` | Canonical JSON、hash、签名、验签和密钥轮换 |
| `magicops-sql-guard/` | SQL 分类、授权、危险语句拦截和修复约束 |
| `magicops-http/` | HTTP 目标注册、allowlist、调用和 trace 透传 |
| `magicops-crypto/` | AES-GCM、SM4、HMAC 与敏感信息脱敏 |
| `magicops-diagnosis/` | Arthas 会话、命令模板、WebSocket 和输出脱敏 |
| `magicops-examples/` | 基于 `magic-api-spring-boot-starter` 的示例应用 |
| `magic-api-source/` | 通过 git subtree 维护的上游 fork 源码，暂不参与主构建 |
| `docs/` | 需求、设计、架构、计划、验证证据和项目日志 |

## 验证

```bash
# 编译全部模块
mvn compile

# 运行完整单元与集成测试
mvn test

# 构建可执行 JAR
mvn package -DskipTests
```

需要 Docker 的 PostgreSQL 闭环验证：

```bash
mvn install -DskipTests
bash docs/testing/e2e/e2e-docker-postgres.sh
```

该脚本会验证发布、执行、审计、Runtime 重启重载和下线链路。它会复用根目录 `magicops` Compose 项目、覆盖本地 `.env`，并在结束时执行 `docker compose down -v`；只能在专用测试环境运行。当前已知良好结果和未验证项以 [`docs/testing/known-good-baselines.md`](./docs/testing/known-good-baselines.md) 为准。

## 安全边界

- Console 只部署在管理网或运维网；Runtime 部署在业务网或生产网。
- 生产 Runtime 禁止在线编辑、草稿保存、裸 SQL 和未签名脚本执行。
- 数据修复必须经过 dry-run、审批、签名、SQL Guard 和关键审计；关键审计失败时阻断执行。
- HTTP 脚本只能访问已注册目标和路径 allowlist，不能访问任意 URL。
- 脚本引用 `keyId`，不得包含原始密钥、密码或 token。
- 回滚是新的签名发布动作；下线由 Console 通过受保护端点通知 Runtime。

更完整的规则见 [`安全与治理`](./docs/architecture/security-and-governance.md) 和 [`发布与 Runtime`](./docs/architecture/release-and-runtime.md)。

## 文档导航

- [`docs/index.md`](./docs/index.md)：项目长期事实源和文档路由入口
- [`docs/design/app-overview.md`](./docs/design/app-overview.md)：产品定位、用户与使用边界
- [`docs/design/feature-inventory.md`](./docs/design/feature-inventory.md)：功能清单与阶段状态
- [`docs/architecture/system-baseline.md`](./docs/architecture/system-baseline.md)：系统架构与技术基线
- [`docs/architecture/module-boundaries.md`](./docs/architecture/module-boundaries.md)：模块职责和禁止事项
- [`docs/testing/known-good-baselines.md`](./docs/testing/known-good-baselines.md)：最近验证证据与待验证项
- [`AGENTS.md`](./AGENTS.md)：AI/Agent 协作与保护区规则

仓库中的需求、设计、架构、计划和验证证据属于长期事实源；聊天记录不替代 `docs/` 中的 owner docs。
