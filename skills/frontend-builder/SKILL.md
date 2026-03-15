# Skill: Frontend Builder

## 描述

前端构建工具，帮助前端工程师进行项目构建、打包和优化。

## 功能

### 1. 项目初始化

快速初始化前端项目。

**支持框架**:
- React (Vite/CRA)
- Vue (Vite/Vue CLI)
- Angular
- Next.js
- Nuxt.js

### 2. 构建配置

生成优化的构建配置。

**配置内容**:
- 入口配置
- 输出配置
- 模块解析
- 插件配置
- 环境变量

### 3. 打包优化

代码分割和资源优化。

**优化项**:
- 代码分割 (Code Splitting)
- Tree Shaking
- 压缩优化
- 资源哈希
- 懒加载配置

### 4. 性能分析

分析打包结果和性能。

**分析内容**:
- 包体积分析
- 依赖分析
- 构建时间分析
- 性能指标

## 使用示例

### 初始化项目

```json
{
  "action": "init",
  "framework": "react",
  "template": "typescript",
  "features": ["router", "state", "ui-lib"]
}
```

### 构建项目

```json
{
  "action": "build",
  "mode": "production",
  "analyze": true,
  "optimization": {
    "splitChunks": true,
    "minify": true,
    "sourceMap": false
  }
}
```

### 性能分析

```json
{
  "action": "analyze",
  "type": "bundle",
  "output": "report.html"
}
```

## 输出格式

```json
{
  "buildResult": {
    "success": true,
    "outputPath": "./dist",
    "assets": [
      {
        "name": "main.abc123.js",
        "size": "150KB",
        "gzip": "50KB"
      },
      {
        "name": "vendor.def456.js",
        "size": "200KB",
        "gzip": "70KB"
      }
    ],
    "warnings": [],
    "errors": []
  },
  "analysis": {
    "totalSize": "350KB",
    "gzipSize": "120KB",
    "chunks": 5,
    "modules": 150
  }
}
```

## 配置

```json
{
  "framework": "react",
  "bundler": "vite",
  "typescript": true,
  "build": {
    "outDir": "./dist",
    "sourcemap": false,
    "minify": "terser",
    "target": "es2015"
  },
  "optimization": {
    "splitChunks": {
      "strategy": "auto",
      "minSize": 20000
    },
    "lazy": true,
    "preload": true
  }
}
```

## 构建模板

### Vite配置模板

```javascript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    sourcemap: false,
    minify: 'terser',
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom'],
          utils: ['lodash', 'axios']
        }
      }
    }
  }
});
```
