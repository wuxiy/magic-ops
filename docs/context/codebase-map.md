# 代码库地图

## 当前状态

MagicOps 当前是文档先行仓库，产品代码尚未脚手架化。

现有文件：

- `README.md` - 仓库入口。
- `docs/` - 长期项目记忆。
- `magicops-project-doc.md` - 指向 AGE 文档的旧入口。
- `magicops-architecture.md` - 指向 AGE 文档的旧入口。

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
