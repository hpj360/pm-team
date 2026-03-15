/**
 * 项目入口文件
 */

import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

// 创建React根节点并渲染应用
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
