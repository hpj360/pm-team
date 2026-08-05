import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

// https://vitest.dev/config/
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
    setupFiles: ['./src/test/setup.ts'],
    css: false, // 测试中不处理 CSS Modules
    // 单元测试配置仅运行 unit 目录，E2E 测试通过 test:e2e 脚本独立运行
    include: ['src/test/unit/**/*.{test,spec}.{ts,tsx}'],
    exclude: ['node_modules', 'dist', 'src/test/e2e/**'],
    // Ant Design Pro 组件渲染较重，并行执行时需要更长超时
    testTimeout: 20000,
    hookTimeout: 20000,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.d.ts',
        'src/test/**',
        'src/main.tsx',
        'src/vite-env.d.ts',
        'src/types/**',
        'src/mock/**',
        'src/**/__tests__/**',
        'src/**/index.ts',
        // 未在本次单测范围内的模块
        'src/services/**',
        'src/hooks/**',
        'src/components/**',
        'src/router/**',
        'src/styles/**',
        'src/utils/request.ts',
        'src/utils/hash.ts',
        'src/utils/fileType.ts',
        'src/App.tsx',
        'src/stores/search.ts',
        'src/stores/analyze.ts',
        'src/stores/ioc.ts',
        // 未测试的页面（不在本次任务范围内）
        'src/pages/FileAnalyze/**',
        'src/pages/IocCenter/**',
        'src/pages/Monitor/**',
        'src/pages/NotFound/**',
        'src/pages/Settings/**',
        'src/pages/FileList/Detail/**',
        'src/pages/FileList/components/**',
        'src/pages/FileSearch/Advanced/**',
        'src/pages/FileUpload/Batch/**',
        'src/pages/redteam/Arsenal/**',
        'src/pages/redteam/Collaboration/**',
        'src/pages/redteam/TaskManage/**',
        'src/pages/redteam/Vulnerability/**',
        'src/pages/redteam/TargetProfile/Detail/**',
        'src/pages/redteam/ThreatIntel/IocDetail/**',
        'src/pages/redteam/AttackChain/Detail/**',
        'src/pages/admin/AuditLog/**',
        'src/pages/admin/DataSource/**',
        'src/pages/admin/HealthCheck/**',
        'src/pages/admin/ModelManage/**',
        'src/pages/admin/PermissionManage/**',
        'src/pages/admin/RoleManage/**',
        'src/pages/admin/SystemConfig/**',
        'src/pages/admin/YaraRuleManage/**',
        'src/pages/admin/UserManage/Detail/**',
        'src/pages/admin/ReportCenter/Preview/**',
      ],
      thresholds: {
        statements: 80,
        branches: 70,
        functions: 45,
        lines: 80,
      },
    },
  },
});
