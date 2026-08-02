import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server proxies API + WebSocket to the Spring Boot backend.
export default defineConfig({
  plugins: [react()],
  define: {
    global: 'globalThis', // sockjs-client references `global`, which browsers lack
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
      },
    },
  },
});
