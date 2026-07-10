# 约定

## 语言

项目文档尽量使用中文。代码标识符、模块名、配置 key、状态枚举、公共 API 名称和通用技术词可以保留英文。

## 命名

- 仓库和配置前缀：`magicops`。
- Java 包前缀：脚手架阶段再确定；最终代码中不要保留 `com.yourorg`。
- 稳定 owner docs 使用稳定文件名。
- 带日期的过程记录使用 `YYYY-MM-DD-HHMM-topic.md`。
- 日志使用 `docs/logs/<year>/<month-day>.md`。

## 文档规则

- 不要在多个 owner docs 中重复同一事实源陈述，除非其中一个明确链接到另一个。
- `docs/design/` 聚焦产品行为。
- `docs/architecture/` 聚焦技术结构和模块边界。
- `docs/input/` 保持贴近原始材料。
- `docs/plans/` 聚焦执行和关闭。

## 安全术语

- Runtime 生产模式只读。
- 第一阶段由平台持有签名私钥。
- Runtime 只持有公钥验签材料。
- 脚本必须引用 key ID，不得包含原始密钥。
- 审计和日志中的敏感 payload 必须先脱敏，再持久化。
