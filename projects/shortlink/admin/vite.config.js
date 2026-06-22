import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置
// 教学点：
// 1. server.proxy：开发时把 /api 转发到后端 8080，避免 CORS
// 2. 前端代码里只用相对路径 /api/v1/...，部署时通过 nginx/反代改 base 也方便
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 后端就是 /api/v1/...，无需 rewrite
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1500, // Element Plus + ECharts 会超 500KB 警告，调高
  },
})