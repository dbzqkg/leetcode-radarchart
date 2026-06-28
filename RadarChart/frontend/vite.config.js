import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:11451',
        changeOrigin: true
      },
      '/imgs': {
        target: 'http://localhost:11451',
        changeOrigin: true
      }
    }
  }
})
