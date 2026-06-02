import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 开发态把 /api、/v1、/anthropic 反向代理到本地 Spring Boot(默认 8321),
// 避免跨域,也让前端代码统一用相对路径请求。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8321', changeOrigin: true },
      '/v1': { target: 'http://localhost:8321', changeOrigin: true },
      '/anthropic': { target: 'http://localhost:8321', changeOrigin: true },
    },
  },
})
