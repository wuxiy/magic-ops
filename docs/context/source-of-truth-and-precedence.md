# 事实源与优先级

## 目的

本文件定义“什么问题由哪个文档回答”。用它避免把稳定事实、执行记录和历史上下文混在一起。

## 按问题划分优先级

### 现在应该构建什么？

主事实源：

- `docs/requirements/`

辅助来源：

- `docs/input/`
- `docs/discussions/`
- `docs/backlog/`

规则：

- `docs/input/` 保存原始材料和决策来源。
- `docs/requirements/` 是可实现解释。
- 如果 input 和 requirements 不一致，必须显式更新需求文件。

### 当前支持的产品行为是什么？

主事实源：

- `docs/design/`

规则：

- `docs/design/` 负责产品形态、功能、流程、角色和领域语言。

### 当前支持的技术结构是什么？

主事实源：

- `docs/architecture/`

规则：

- `docs/architecture/` 负责技术边界、模块职责、发布信任边界和跨切面实现规则。

### 数据库事实源是什么？

主事实源：

- 实现开始后的 schema/model 产物。

规则：

- 文档可以解释意图，但 schema/model 文件创建后成为数据库事实源。

### API 契约事实源是什么？

主事实源：

- 未来 OpenAPI、路由定义或后端契约测试。

规则：

- 文本文档可以总结 API 意图，但可执行或 schema 级 API 契约创建后优先。

### 外部集成事实源是什么？

主事实源：

- 已提交的集成文档；
- 已提交的适配器配置；
- 集成测试。

规则：

- 不要只根据产品描述臆造外部系统行为。

### 这个切片应如何执行和关闭？

主事实源：

- `docs/plans/`

规则：

- 计划是执行契约，不是长期 owner docs。

### 实际发生了什么？

主事实源：

- `docs/logs/`

辅助来源：

- `docs/testing/`
- `docs/bugs/`
- `docs/audits/`
- `docs/retrospectives/`

## 冲突处理

- 如果 requirements 和 owner docs 不一致，先判断需求是否改变了支持基线，再更新 `docs/design/` 或 `docs/architecture/`。
- 如果现有代码和 owner docs 不一致，将其视为实现漂移或文档漂移，不要静默选择其中一个。
- 如果解决冲突会改变用户可见行为、数据/模型形态、API 行为、认证/权限行为、发布行为或外部集成行为，停下来请求确认。

## 简单判断规则

- 稳定行为和结构属于 owner docs；
- 执行属于 plans 和 logs；
- 历史和诊断属于 audits、testing notes、bugs、retrospectives 和 lessons。
