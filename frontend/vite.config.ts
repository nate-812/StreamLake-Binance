import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const API_TARGET = process.env.VITE_API_TARGET || 'http://data1:8080'
const WS_TARGET  = API_TARGET.replace(/^http/, 'ws')

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target:       API_TARGET,
        changeOrigin: true,
      },
      '/ws': {
        target:       WS_TARGET,
        ws:           true,
        // WS 未连通时 Vite 代理会打 EPIPE，前端已有重连退避逻辑，此处静默即可
        configure: (proxy) => {
          proxy.on('error', () => { /* suppress proxy-level WS errors */ })
        },
      },
    },
  },
})
