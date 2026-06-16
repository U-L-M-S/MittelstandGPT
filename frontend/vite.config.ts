import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The backend URL is injected by docker-compose (service name "backend").
// Falls back to localhost for running the dev server outside Docker.
const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    // File watching across the Docker bind mount needs polling.
    watch: { usePolling: true },
    proxy: {
      '/api': {
        target: backendUrl,
        changeOrigin: true,
      },
    },
  },
})
