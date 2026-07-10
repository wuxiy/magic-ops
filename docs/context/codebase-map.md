# 代码库地图

## 当前状态

MagicOps 脚手架已完成，Maven 多模块项目已建立。

现有文件：

- `README.md` - 仓库入口。
- `AGENTS.md` - AI/Agent 工作规则。
- `pom.xml` - Maven 多模块父 POM（Spring Boot 3.3.5，JDK 21）。
- `docs/` - 长期项目记忆。
- `magic-api-source/` - magic-api 上游代码（git subtree，暂不参与主构建）。
- `magicops-core/` - 共享核心模块。
- `magicops-governance/` - 治理模块（审批、状态机）。
- `magicops-audit/` - 审计模块。
- `magicops-sign/` - 签名/验签模块。
- `magicops-sql-guard/` - SQL 守卫模块。
- `magicops-http/` - HTTP 目标适配模块。
- `magicops-crypto/` - 加解密模块。
- `magicops-console/` - Console Spring Boot 应用（端口 8080）。
- `magicops-runtime/` - Runtime Spring Boot 应用（端口 8081）。

## 第一阶段目标仓库结构

第一阶段实现建议使用单仓库：

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
├── magicops-examples
└── docs
```

Arthas 诊断模块属于第二阶段，不应阻塞第一阶段脚手架。

## 常见变更路由

| 变更区域 | 先读 | 再读 |
|---|---|---|
| 第一阶段产品范围 | `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md` | `docs/design/app-overview.md` |
| 脚本生命周期 | `docs/design/flow-overview.md` | `docs/architecture/module-boundaries.md` |
| Console 到 Runtime 发布 | `docs/architecture/release-and-runtime.md` | `docs/architecture/security-and-governance.md` |
| SQL Guard 或数据修复 | `docs/design/flow-overview.md` | `docs/architecture/security-and-governance.md` |
| 接口适配 | `docs/design/feature-inventory.md` | `docs/architecture/module-boundaries.md` |
| 角色和审批 | `docs/design/roles-and-permissions.md` | `docs/architecture/security-and-governance.md` |
| Arthas 诊断 | `docs/requirements/2026-07-09-0000-first-stage-platform-foundation.md` | 第二阶段文档创建后再读 |

## 需要保守处理的脆弱区域

- 签名与验签；
- 发布包 manifest 与元数据 hash 规则；
- Runtime 热加载和回滚；
- SQL Guard 写操作规则；
- 审计写入可靠性；
- 敏感数据脱敏；
- key reference 与加解密 provider；
- 外部 HTTP 目标 allowlist。
