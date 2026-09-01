import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Get backend URL from environment variable or default to localhost:8080
// This allows developers to point to different backend instances
const BACKEND_URL = process.env.VITE_BACKEND_URL || 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],

  server: {
    // Proxy API requests to the backend during development
    // Allows frontend on port 5173 to call backend on port 8080
    // without CORS issues and with proper cookie forwarding
    proxy: {
      '/api': {
        target: BACKEND_URL,
        changeOrigin: true,
      },
      '/oauth2': {
        target: BACKEND_URL,
        changeOrigin: true,
      },
      '/login': {
        target: BACKEND_URL,
        changeOrigin: true,
      },
      '/logout': {
        target: BACKEND_URL,
        changeOrigin: true,
      },
    },
  },
})

