import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // 兼容 sockjs-client 等老库对 Node.js global 的引用
  define: {
    global: 'globalThis',
  },
  resolve: {
    alias: {
      // 配置路径别名
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
  server: {
    port: 3000,
    open: true,
    // 配置代理，解决跨域问题
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
      },
    },
    rollupOptions: {
      output: {
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/[name]-[hash].js',
        assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
        manualChunks(id) {
          // React 核心
          if (
            id.includes('node_modules/react/') ||
            id.includes('node_modules/react-dom/') ||
            id.includes('node_modules/react-router-dom/')
          ) {
            return 'react-core';
          }
          // Ant Design 核心（icons 与 antd 内部强耦合，合并避免循环依赖）
          if (
            id.includes('node_modules/antd/') ||
            id.includes('node_modules/rc-') ||
            id.includes('node_modules/@ant-design/icons')
          ) {
            return 'antd-core';
          }
          // Ant Design Pro Components（按子包拆分，避免单 chunk 过大）
          // pro-table 独立拆分
          if (id.includes('node_modules/@ant-design/pro-table')) {
            return 'antd-pro-table';
          }
          // pro-form 及其共享底层依赖（pro-provider/field/card/utils、
          // 元包 pro-components）合并为同一 chunk，避免与 pro-misc 产生循环依赖
          if (
            id.includes('node_modules/@ant-design/pro-components') ||
            id.includes('node_modules/@ant-design/pro-form') ||
            id.includes('node_modules/@ant-design/pro-provider') ||
            id.includes('node_modules/@ant-design/pro-field') ||
            id.includes('node_modules/@ant-design/pro-card') ||
            id.includes('node_modules/@ant-design/pro-utils')
          ) {
            return 'antd-pro-form';
          }
          if (id.includes('node_modules/@ant-design/pro-descriptions')) {
            return 'antd-pro-desc';
          }
          // 其余 pro 组件（pro-layout / pro-list / pro-skeleton 等）
          if (id.includes('node_modules/@ant-design/pro-')) {
            return 'antd-pro-misc';
          }
          // ECharts 核心（含 zrender 渲染底层，合并避免循环依赖）
          if (
            id.includes('node_modules/echarts/') ||
            id.includes('node_modules/zrender/')
          ) {
            return 'echarts-core';
          }
          // ECharts React 封装
          if (id.includes('node_modules/echarts-for-react')) {
            return 'echarts-charts';
          }
          // TanStack Query
          if (id.includes('node_modules/@tanstack/')) {
            return 'query';
          }
          // 工具库
          if (
            id.includes('node_modules/lodash') ||
            id.includes('node_modules/dayjs') ||
            id.includes('node_modules/hutool')
          ) {
            return 'utils-vendor';
          }
          return undefined;
        },
      },
    },
  },
  css: {
    modules: {
      // CSS Modules 配置：类名格式 [name]__[local]__[hash]
      generateScopedName: '[name]__[local]__[hash:5]',
      localsConvention: 'camelCaseOnly',
    },
    preprocessorOptions: {
      less: {
        javascriptEnabled: true,
      },
    },
  },
});
