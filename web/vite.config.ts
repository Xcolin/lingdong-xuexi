import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    clearMocks: true,
    restoreMocks: true,
    // Ant Design 模态框在 jsdom 中完成焦点和动画相关状态同步需要超过默认 5 秒。
    testTimeout: 15_000
  }
});
