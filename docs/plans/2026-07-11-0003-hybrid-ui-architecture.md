# 混合 UI 架构实施计划

## 状态

Draft，等待实现前确认。

## 目标

为 MagicOps 构建三层混合 UI 架构，覆盖脚本开发、平台管理和诊断中心三个场景。

关闭标准：用户可以通过浏览器完成脚本编辑/调试、审批/审计/用户管理、Arthas 诊断会话创建和实时输出查看。

## 架构概览

```text
magicops-console (端口 8080)
├── /magic/web/*     → magic-editor（脚本编辑/调试/数据源，闭源 Vue 3 SPA）
├── /console/*       → Console SPA（审批/审计/用户/密钥/发布，Vue 3 + Element Plus）
├── /diagnosis/*     → Diagnosis SPA（Arthas 终端/会话，Vue 3 + xterm.js）
└── /api/*           → REST API（所有 UI 层共享的后端接口）
```

## 已确认关键决策

- magic-editor 承担脚本开发核心，通过插件扩展 HTTP 目标管理和审批状态面板。
- Console SPA 和 Diagnosis SPA 为独立 Vue 3 工程，构建产物部署到 Spring Boot 静态资源目录。
- 认证采用双模式：HTTP Basic（API 客户端）+ Cookie Session（浏览器用户）。
- 前端构建通过 `frontend-maven-plugin` 集成到 Maven 构建流程。
- WebSocket 诊断终端使用 xterm.js + 原生 WebSocket。

## 后端 API 补充（前置依赖）

UI 层实现前需补充以下后端接口：

| 端点 | 控制器 | 用途 |
|---|---|---|
| `POST /api/login` | `LoginController`（新建） | 浏览器表单登录，创建 HTTP Session |
| `GET /api/me` | `LoginController`（新建） | 返回当前用户信息 + 权限列表 |
| `GET /api/approvals`（分页） | `ApprovalController`（新建） | 审批列表，支持状态过滤 |
| `GET /api/publish`（分页） | `PublishController`（新建） | 发布包历史列表 |
| `WS /api/diagnosis/ws/sessions/{id}` | `DiagnosisWebSocketHandler`（新建） | Arthas 实时输出流 |

SecurityConfig 重构为双链模式：
- `/api/**` 链：HTTP Basic + Session，支持 API 客户端和浏览器共用
- `/console/**`、`/diagnosis/**` 链：静态资源放行，SPA 路由转发

## 实施切片

### 切片 23：后端 API 补充 + SecurityConfig 重构

目标：

- 新建 `LoginController`（POST /api/login + GET /api/me）；
- 新建 `ApprovalController`（GET /api/approvals 分页）；
- 新建 `PublishController`（GET /api/publish 分页）；
- SecurityConfig 重构为双链（HTTP Basic + Session Cookie）；
- 登录/认证集成测试。

产物：

- `LoginController` + `FormLoginSecurityConfig`；
- `ApprovalController` + `PublishController`；
- `V8__approval_publish_indexes.sql`；
- 认证测试。

关闭标准：

- `POST /api/login` 返回 Session Cookie + 用户信息；
- `GET /api/me` 返回当前用户权限列表；
- `GET /api/approvals` 分页查询成功；
- HTTP Basic 客户端仍可用（向后兼容）；
- 至少 6 个认证/授权测试通过。

### 切片 24：Console SPA 脚手架 + 登录 + 布局

目标：

- 初始化 Vue 3 + TypeScript + Vite + Element Plus 工程；
- 实现登录页面、认证 Store、API Client；
- 实现侧边栏布局 + 路由守卫；
- Vite 构建产物集成到 Spring Boot 静态资源；
- `ConsoleSpaController` SPA 路由转发。

产物：

- `magicops-console/frontend/` 完整 Vue 3 工程；
- `LoginView.vue` + `LayoutView.vue`；
- `stores/auth.ts` + `api/client.ts`；
- `ConsoleSpaController.java`；
- `frontend-maven-plugin` 集成。

关闭标准：

- `npm run build` 产物在 `/console/` 路径可访问；
- 登录页面可正常登录/登出；
- 侧边栏导航 + 路由切换正常；
- Maven `mvn package` 包含前端构建步骤。

### 切片 25：Console SPA 审批 + 审计页面

目标：

- 审批列表页（待审批/已通过/已拒绝过滤）；
- 审批详情页（通过/拒绝 + 评论）；
- 审计查询页（分页 + 按实体/事件/时间过滤）；
- 审计详情页。

产物：

- `ApprovalListView.vue` + `ApprovalDetailView.vue`；
- `AuditListView.vue` + `AuditDetailView.vue`；
- 审批和审计的 API 接口封装。

关闭标准：

- 审批列表可过滤和分页；
- 可通过/拒绝审批并查看结果；
- 审计记录可查询并展示脱敏后的详情。

### 切片 26：Console SPA 用户 + 密钥 + 发布页面

目标：

- 用户管理页（列表/创建/角色分配/启用禁用）；
- 角色权限查看页；
- 密钥管理页（列表/轮换/移除）；
- 发布历史页（列表/详情）。

产物：

- `UserListView.vue` + `RoleManageView.vue`；
- `KeyListView.vue`；
- `PublishHistoryView.vue`；
- 权限控制菜单可见性。

关闭标准：

- 用户 CRUD 操作正常；
- 密钥轮换操作正常；
- 不同角色看到不同菜单；
- 发布历史可查看。

### 切片 27：magic-editor 插件扩展

目标：

- HTTP 目标管理插件（新资源类型 + CRUD 面板）；
- 审批状态面板插件（脚本工具栏显示审批状态）。

产物：

- `magic-api-plugin-httptarget/` 插件工程；
- `magic-api-plugin-approval/` 插件工程；
- 后端 `MagicPluginConfiguration` 实现；
- 前端 IIFE JS bundle。

关闭标准：

- magic-editor 左侧资源树出现"HTTP 目标"分类；
- 打开脚本时底部面板显示审批状态和时间线。

### 切片 28：诊断中心 WebSocket 后端

目标：

- `DiagnosisWebSocketHandler` 实现；
- `DiagnosisWebSocketConfig` 注册；
- WebSocket 握手认证拦截器；
- `ArthasTunnelClient` 桩实现（模拟输出，后续接入真实 Tunnel）。

产物：

- `DiagnosisWebSocketHandler.java`；
- `DiagnosisWebSocketConfig.java`；
- `DiagnosisHandshakeInterceptor.java`；
- WebSocket 认证测试。

关闭标准：

- WebSocket 连接可建立；
- 未认证连接被拒绝；
- 命令下发返回模拟输出；
- 至少 4 个 WebSocket 测试通过。

### 切片 29：诊断中心前端页面

目标：

- 初始化 Diagnosis Vue 3 工程；
- Arthas 终端组件（xterm.js）；
- 会话列表 + 创建对话框；
- 命令模板选择器 + 参数表单；
- WebSocket 连接管理。

产物：

- `magicops-console/frontend-diagnosis/` Vue 3 工程；
- `ArthasTerminal.vue`（xterm.js 终端）；
- `SessionList.vue` + `SessionCreateDialog.vue`；
- `TemplatePicker.vue` + `CommandForm.vue`；
- `DiagnosisSpaController.java`。

关闭标准：

- 可创建诊断会话；
- 终端组件可接收并显示 WebSocket 输出；
- 可选择命令模板并下发执行；
- 会话超时后终端显示断开提示。

## 验证策略

- 切片 23 结束时执行认证集成测试。
- 切片 24-26 每个切片结束时进行浏览器手工验收。
- 切片 28-29 结束时执行 WebSocket 集成测试。
- 全部切片完成后执行端到端浏览器验收。

## 风险和约束

- magic-editor 前端闭源，插件开发依赖 `magic-api-plugin-component` 作为参考规范。
- SecurityConfig 双模式改造可能影响现有 API 客户端，需要充分回归测试。
- 前端构建集成到 Maven 会增加构建时间，开发阶段建议独立运行 Vite dev server。
- Arthas Tunnel Server 是外部基础设施依赖，切片 28 先用模拟实现。
- WebSocket 认证需要仔细设计，避免未授权的诊断命令执行。

## 暂不做

- 完整 UI 重写（magic-editor 核心不变）；
- 报表/表单设计器；
- 移动端适配；
- 多租户 UI。
