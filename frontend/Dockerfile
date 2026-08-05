# =============================================================================
# 红方文件汇聚平台 - 前端 Dockerfile
# 多阶段: Node 20 build -> Nginx 运行
# 构建命令:
#   docker build -t redteam/frontend:latest -f frontend/Dockerfile frontend
# =============================================================================

# ---------- Stage 1: Build ----------
FROM node:20-alpine AS builder
LABEL stage=builder

WORKDIR /build

# 复制依赖清单, 利用 Docker 层缓存
COPY package.json package-lock.json* ./

# 安装依赖 (CI 环境: npm ci; 兼容无 lock 文件: npm install)
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi

# 复制源码
COPY . .

# 构建参数 (可注入 API 地址等)
ARG VITE_API_BASE_URL=/api
ARG VITE_APP_ENV=production
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL} \
    VITE_APP_ENV=${VITE_APP_ENV} \
    NODE_ENV=production

# Vite 构建
RUN npm run build

# ---------- Stage 2: Runtime ----------
FROM nginx:1.25-alpine

LABEL org.opencontainers.image.title="redteam-frontend" \
      org.opencontainers.image.description="红方文件汇聚平台 - 前端" \
      org.opencontainers.image.source="https://github.com/redteam/pm-team"

# 安装 curl 用于健康检查
RUN apk add --no-cache curl tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 复制构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

# 替换模板变量 (运行时注入)
COPY docker-entrypoint.sh /docker-entrypoint.d/40-envsubst.sh
RUN chmod +x /docker-entrypoint.d/40-envsubst.sh

EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=5 \
    CMD curl -fs http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
