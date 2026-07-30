# Design

MagicOps Console(`magicops-console/frontend`)的视觉系统。Register:product(见 `PRODUCT.md`)。

## 技术基座

- Vue 3 + Element Plus 2.9(zh-CN locale)+ Vite + Pinia + vue-router。
- **按需引入**:模板组件(含 `v-loading` 指令)由 `unplugin-vue-components` 自动解析;`ElMessage` / `ElMessageBox` 的样式在 `main.ts` 手动按需引入;图标一律在使用的视图里显式 import,不做全局注册。locale 由 `App.vue` 的 `el-config-provider` 提供。
- 样式入口:`src/styles/index.css`(token 覆盖 + 共享布局类),组件内用 scoped style。
- 主题:Element Plus 默认浅色主题,克制用色(Restrained)。无暗色模式。

## 颜色

以 Element Plus CSS 变量为准,不硬编码 hex(深色侧边栏除外):

- 内容表面:`var(--el-bg-color)`(白);页面底:`var(--el-bg-color-page)`。
- 文本:`--el-text-color-primary` / `--el-text-color-regular` / `--el-text-color-secondary`。
- placeholder:**覆盖为 `#6f7379`**(`:root` 上 `--el-text-color-placeholder`),默认 `#a8abb2` 仅 ~2.3:1,不满足 WCAG AA。
- 主色:`--el-color-primary`(默认 #409eff),仅用于主操作、当前选中、状态指示。
- 深色侧边栏(仅 `LayoutView`,有意保留的深色区域):底 `#304156`,logo 区 `#263445`,文字 `#bfcbd9`,**激活 `#79bbff`**(在侧边栏底色上 ≥4.5:1;原 `#409eff` 仅 ~3.8:1)。
- 语义状态:统一走 `el-tag` type 映射 —— 审批(已通过 success / 已拒绝 danger / 待审批 warning)、发布(production=danger、staging=warning;成功 success / 失败 danger / 已回滚 warning)、密钥(活跃 success / 已停用 warning)、用户(启用 success / 禁用 info)。状态一律"颜色 + 中文文字"双通道,不靠纯颜色。

## 字体排版

- 单一系统字体栈(Element Plus 默认),无展示字体。
- 页面标题:`h1.page-title`,16px / 600;顶栏标题 16px / 600;登录卡标题 20px。
- 表格允许高密度(product register 许可)。

## 布局

- 应用壳:220px 深色侧边栏 + 60px 顶栏 + 内容区(`LayoutView`)。
- 列表页统一模式:`el-card` → `.card-header`(左 `h1.page-title`,右操作区)→ `el-table`(stripe border)→ 右下 `.pagination-wrap`。
- 共享类只在 `src/styles/index.css` 定义一份:`.page-title`、`.card-header`、`.pagination-wrap`;各视图不得再复制。
- 登录页:居中卡片,宽 `min(400px, calc(100vw - 32px))`。

## 组件约定

- 表格:`stripe border`,长文本列 `show-overflow-tooltip`,操作列 `fixed="right"`。
- 弹窗:新建/角色管理用 `el-dialog`;危险/状态变更操作用 `ElMessageBox` 确认 —— 禁用、轮换用 `warning`,删除用 `error`。
- 加载:`v-loading`(骨架屏未采用,后续可评估)。
- 错误处理:**统一在 `src/api/client.ts` 响应拦截器**弹 `ElMessage.error`,优先取后端 `{ error }` 字段;视图层 catch 只保留数据、不再各自 toast;调用方可传 `skipErrorMessage: true` 自行处理(如登录、会话检查)。401 统一跳登录页并带 `redirect` 回跳参数。

## 动效

仅 Element Plus 默认过渡,无自定义动画。

## 已确认的取舍

- 桌面优先的内网控制台:窄视口下表格横向滚动兜底,不做移动断点。
- 表格内操作按钮保持 `size="small"`(密度优先,鼠标场景);对话框、分页等为默认尺寸。
- Element Plus 已按需引入(2026-07-30):入口 chunk 从 ~1.2 MB 降至 ~227 kB(gzip 403 → 87 kB),组件 CSS 按 chunk 拆分。新增组件无需手动注册,直接在模板使用即可。
