/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  return {
    // GitHub Pages serves the demo under /<repo>/; the real portal is served at /.
    base: env.VITE_BASE ?? '/',
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: { '/api': 'http://localhost:8080' },
    },
    test: {
      environment: 'jsdom',
    },
  }
})
