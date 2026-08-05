#!/bin/sh
# =============================================================================
# Nginx envsubst 入口脚本
# 在 Nginx 启动前用环境变量替换 nginx.conf 中的占位符
# =============================================================================
set -e

# 需要替换的变量列表
ENV_VARS='$BACKEND_GATEWAY_HOST $BACKEND_GATEWAY_PORT'

# 备份原始模板
TEMPLATE=/etc/nginx/conf.d/default.conf.template
if [ ! -f "$TEMPLATE" ]; then
    cp /etc/nginx/conf.d/default.conf "$TEMPLATE"
fi

# 执行 envsubst 并写回配置文件
envsubst "$ENV_VARS" < "$TEMPLATE" > /etc/nginx/conf.d/default.conf

echo "[nginx] Config rendered with env vars: $ENV_VARS"
exec "$@"
