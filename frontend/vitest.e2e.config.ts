import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

// E2E 测试配置：独立于单元测试，不 mock ECharts
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@components': path.resolve(__dirname, './src/components'),
      '@pages': path.resolve(__dirname, './src/pages'),
      '@services': path.resolve(__dirname, './src/services'),
      '@stores': path.resolve(__dirname, './src/stores'),
      '@hooks': path.resolve(__dirname, './src/hooks'),
      '@utils': path.resolve(__dirname, './src/utils'),
      '@types': path.resolve(__dirname, './src/types'),
      '@assets': path.resolve(__dirname, './src/assets'),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup-e2e.ts'],
    css: false,
    include: ['src/test/e2e/**/*.{test,spec}.{ts,tsx}'],
    exclude: ['node_modules', 'dist', 'src/test/unit/**'],
    // E2E 测试串行执行，避免并行导致的时序问题
    poolOptions: {
      threads: {
        singleThread: true,
      },
    },
  },
});
