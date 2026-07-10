# 系统基线

## 架构目标

MagicOps 必须：

1. 保留 magic-api 在线开发、在线调试和动态 API 映射能力；
2. 支持在医院/政务内网项目中嵌入部署或网关部署；
3. 分离 Console 和 Runtime；
4. 强制审批、审计、签名和 Runtime 验签；
5. 生产 Runtime 以只读模式运行；
6. 支持受控 SQL、HTTP、加解密能力，后续支持 Redis/MQ；
7. 第二阶段支持 Arthas Java 线上诊断；
8. 后续支持多项目、离线交付和医卫模板；
9. 满足安全、审计和可追溯要求。

## 逻辑架构

```text
Management / Operations Network
  ├── magicops-console
  │     ├── edit/debug
  │     ├── approval
  │     ├── signing
  │     ├── online push release
  │     └── audit query
  └── magicops-diagnosis (second stage)
        └── Arthas session control

Production / Business Network
  ├── magicops-runtime
  │     ├── package verification
  │     ├── read-only script execution
  │     ├── SQL Guard
  │     ├── CryptoModule
  │     ├── HTTP integration module
  │     └── execution audit
  └── Business systems / DB / HIS / EMR / LIS / PACS / Insurance / Regional platforms
```

## 分层

| 层 | 组件 |
|---|---|
| 展示层 | `magicops-console`、增强 magic-editor、未来 diagnosis console |
| 治理层 | Auth/RBAC、Approval、Audit、Signature Publish、SQL Guard、Risk Control |
| 执行层 | magic-api core fork、Runtime、Script Engine、Dynamic API Dispatcher、Interceptors |
| 集成层 | JDBC/多数据源、HTTP client、Crypto provider、未来 Redis/MQ、未来 Arthas Tunnel client |
| 基础设施层 | PostgreSQL、Redis、日志存储、对象/文件存储、key store、未来 Arthas Tunnel Server |

## 部署形态

| 形态 | 第一阶段角色 |
|---|---|
| Embedded Runtime | 嵌入业务 Spring Boot 系统，用于受控动态 API 和修复脚本。 |
| Gateway Runtime | 作为独立内网集成网关。 |
| Console + 多 Runtime | 后续集中管理形态。 |
| Diagnosis Center | 第二阶段 Arthas 系统。 |

## 技术选型

| 领域 | 基线 |
|---|---|
| JDK | JDK 17 or 21 |
| 后端框架 | Spring Boot |
| 基础项目 | 通过 `git subtree` 维护的 magic-api fork |
| 安全 | Spring Security |
| 平台元数据库 | PostgreSQL |
| 第一版业务数据源 | 达梦数据库，优先服务 SQL Guard、dry-run 和数据修复闭环 |
| SQL 解析 | JSQLParser 或等价 parser；核心 Guard 行为不能依赖临时字符串检查 |
| 加解密 | BouncyCastle 或兼容 provider |
| 日志 | Logback，按条件接入 Loki/ELK/OpenSearch |
| 构建 | Maven，除非脚手架证明需要其他选择 |
| 部署 | Docker、内网制品库，后续支持离线包 |

## 第一阶段仓库方向

架构稳定前使用单仓库：

```text
magicops
├── magic-api-core
├── magic-api-spring-boot-starter
├── magicops-core
├── magicops-console
├── magicops-runtime
├── magicops-governance
├── magicops-audit
├── magicops-sign
├── magicops-crypto
├── magicops-sql-guard
├── magicops-http
└── docs
```

Redis、MQ、diagnosis、examples 和 plugin 模块可在第一阶段基础稳定后加入。

第一版采用多应用形态：

- `magicops-console` 独立启动，负责编辑、调试、审批、签名、发布推送和审计查询；
- `magicops-runtime` 独立启动，负责接收发布包、验签、加载、执行和审计；
- 共享能力沉淀到 `magicops-core`、`magicops-governance`、`magicops-audit`、`magicops-sign`、`magicops-sql-guard`、`magicops-http` 等模块。

Maven `groupId` 使用 `top.cywu`。Java package 默认使用 `top.cywu.magicops` 前缀，除非脚手架阶段发现必须兼容 magic-api 原包名。

`magic-api` 源码使用 `git subtree` 方式引入或维护。该方式保留上游来源，同时符合第一阶段单仓库推进方式。后续如需独立维护上游 fork，可在模块边界稳定后再拆分。
