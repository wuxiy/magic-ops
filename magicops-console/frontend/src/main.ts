import { createApp } from 'vue'
import { createPinia } from 'pinia'

// ElMessage / ElMessageBox 通过 JS API 调用,按需引入其样式
// (模板组件由 unplugin-vue-components 自动按需引入,locale 由 App.vue 的 el-config-provider 提供)
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

import App from './App.vue'
import router from './router'
import './styles/index.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
