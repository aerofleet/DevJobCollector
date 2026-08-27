import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

import { cloudflare } from "@cloudflare/vite-plugin";

// vite.config.js
export default defineConfig(({ mode }) => ({
  plugins: [react(), ...(mode === 'e2e' ? [] : [cloudflare()])],
  server: {
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
}))
