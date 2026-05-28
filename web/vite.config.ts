import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5174,
    proxy: {
      '/api/reviews': {
        target: 'http://localhost:9090',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, req) => {
            proxyRes.headers['cache-control'] = 'no-cache, no-transform'
            proxyRes.headers['x-accel-buffering'] = 'no'
          })
        }
      },
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/element-plus')) {
            return 'element-plus'
          }
          if (id.includes('node_modules/vue') || id.includes('node_modules/vue-router') || id.includes('node_modules/pinia')) {
            return 'vue-vendor'
          }
        }
      }
    }
  }
})
