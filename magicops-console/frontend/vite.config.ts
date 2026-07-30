import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 模板组件按需引入(含 v-loading 指令与对应样式)
    Components({
      resolvers: [ElementPlusResolver({ directives: true })],
      dts: 'src/components.d.ts',
    }),
  ],
  base: '/console/',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: path.resolve(__dirname, '../src/main/resources/static/console'),
    emptyOutDir: true,
  },
})
