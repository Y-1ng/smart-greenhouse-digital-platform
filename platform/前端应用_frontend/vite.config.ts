import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5199,
    strictPort: true,
    // 固定公网域名（ngrok 免费版 dev domain，绑定账号不随重启变化）。
    // 不放行的话 Vite 会以 "Blocked request" 403 拒绝来自该域名的访问。
    allowedHosts: ['sage-ship-scribing.ngrok-free.dev'],
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8090',
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      input: {
        main: fileURLToPath(new URL('./index.html', import.meta.url)),
        cover: fileURLToPath(new URL('./cover.html', import.meta.url)),
      },
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/three')) return 'vendor-three'
          if (id.includes('node_modules/element-plus')) return 'vendor-element-plus'
          if (id.includes('node_modules/echarts')) return 'vendor-echarts'
          if (id.includes('node_modules/vue') || id.includes('node_modules/pinia')) return 'vendor-vue'
          return undefined
        },
      },
    },
  },
})
