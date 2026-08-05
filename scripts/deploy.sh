#!/usr/bin/env bash
# =============================================================================
# 红方文件汇聚平台 - K8s 部署脚本 (Linux/macOS Bash)
# 完整流程: 验证环境 -> 滚动更新 -> 等待就绪 -> 健康检查
# 用法:
#   ./scripts/deploy.sh --service auth-service --tag v1.0.0
#   ./scripts/deploy.sh --service all --tag latest
# =============================================================================
set -euo pipefail

# ---- 默认参数 ----
SERVICE=""
TAG=""
NAMESPACE="redteam-platform"
REGISTRY="registry.example.com/redteam"
NO_WAIT=false
TIMEOUT=600

# ---- 颜色 ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# ---- 帮助 ----
usage() {
    cat <<EOF
红方文件汇聚平台 - K8s 部署脚本

用法: $0 --service <svc> [--tag <tag>] [选项]

参数:
  -s, --service <name>     服务名 (auth-service|...|frontend|all)  [必填]
  -t, --tag <tag>          镜像 tag (默认 git short SHA)
  -n, --namespace <ns>     K8s 命名空间 (默认 redteam-platform)
  -r, --registry <reg>     镜像仓库 (默认 registry.example.com/redteam)
  --no-wait                不等待 rollout 完成
  --timeout <seconds>      rollout 超时秒数 (默认 600)
  -h, --help               显示帮助

示例:
  $0 --service auth-service --tag v1.0.0
  $0 --service all --tag latest
EOF
    exit 0
}

# ---- 解析参数 ----
while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--service) SERVICE="$2"; shift 2 ;;
        -t|--tag) TAG="$2"; shift 2 ;;
        -n|--namespace) NAMESPACE="$2"; shift 2 ;;
        -r|--registry) REGISTRY="$2"; shift 2 ;;
        --no-wait) NO_WAIT=true; shift ;;
        --timeout) TIMEOUT="$2"; shift 2 ;;
        -h|--help) usage ;;
        *) echo "未知参数: $1"; usage ;;
    esac
done

# 校验必填
if [[ -z "$SERVICE" ]]; then
    echo -e "${RED}[ERROR] --service 为必填参数${NC}"
    usage
fi

# 默认 tag: git short SHA
if [[ -z "$TAG" ]]; then
    TAG=$(git rev-parse --short HEAD 2>/dev/null || echo "latest")
fi

# ---- 工具函数 ----
section() { echo -e "\n${CYAN}==========================================${NC}"; echo -e "${CYAN} $1${NC}"; echo -e "${CYAN}==========================================${NC}"; }
step()     { echo -e "${GREEN}[STEP]${NC} $1"; }
warn()     { echo -e "${YELLOW}[WARN]${NC} $1"; }
err()      { echo -e "${RED}[ERROR]${NC} $1"; }
ok()       { echo -e "${GREEN}[OK]${NC} $1"; }

# ---- 前置检查 ----
section "红方文件汇聚平台 - K8s 部署"

step "前置环境检查..."
if ! command -v kubectl &>/dev/null; then
    err "未找到 kubectl"
    exit 1
fi

step "验证 K8s 集群连通性..."
if ! kubectl get namespace "$NAMESPACE" &>/dev/null; then
    err "命名空间 $NAMESPACE 不存在, 请先执行: kubectl apply -f k8s/namespace.yaml"
    exit 1
fi

echo ""
echo "部署参数:"
echo "  服务:     $SERVICE"
echo "  镜像 Tag: $TAG"
echo "  命名空间: $NAMESPACE"
echo "  仓库:     $REGISTRY"

# ---- 部署函数 ----
deploy_single() {
    local svc="$1"
    local tag="$2"
    local full_image="${REGISTRY}/${svc}:${tag}"

    section "部署 $svc"
    step "目标镜像: $full_image"

    # 检查 deployment
    step "检查 Deployment 是否存在..."
    if ! kubectl get deployment "$svc" -n "$NAMESPACE" &>/dev/null; then
        err "Deployment $svc 不存在, 请先 apply 部署清单"
        echo "  提示: kubectl apply -f k8s/${svc}.yaml"
        return 1
    fi

    # 记录当前镜像
    local old_image
    old_image=$(kubectl get deployment "$svc" -n "$NAMESPACE" -o jsonpath="{.spec.template.spec.containers[0].image}" 2>/dev/null || echo "unknown")
    step "当前镜像: $old_image"

    # 触发滚动更新
    step "触发滚动更新..."
    if ! kubectl set image "deployment/${svc}" "${svc}=${full_image}" -n "$NAMESPACE"; then
        err "set image 失败"
        return 1
    fi

    if [[ "$NO_WAIT" == "true" ]]; then
        warn "已设置 --no-wait, 跳过等待 rollout"
        return 0
    fi

    # 等待 rollout
    step "等待 rollout 完成 (超时 ${TIMEOUT}s)..."
    if ! kubectl rollout status "deployment/${svc}" -n "$NAMESPACE" --timeout="${TIMEOUT}s"; then
        err "$svc rollout 失败!"
        warn "可使用以下命令查看详情:"
        echo "  kubectl rollout status deployment/$svc -n $NAMESPACE"
        echo "  kubectl describe deployment $svc -n $NAMESPACE"
        echo "  kubectl logs -l app=$svc -n $NAMESPACE --tail=50"
        warn "建议执行回滚: ./scripts/rollback.sh --service $svc --namespace $NAMESPACE"
        return 1
    fi

    step "当前 Pod 状态:"
    kubectl get pods -n "$NAMESPACE" -l "app.kubernetes.io/name=${svc}" -o wide 2>/dev/null || true

    echo ""
    ok "$svc 部署成功"
    return 0
}

# ---- 执行部署 ----
ALL_SERVICES=("auth-service" "upload-service" "parse-service" "search-service" "analyze-service"
              "profile-service" "task-service" "notification-service" "report-service" "feishu-service" "frontend")

if [[ "$SERVICE" == "all" ]]; then
    target_services=("${ALL_SERVICES[@]}")
else
    target_services=("$SERVICE")
fi

failed=()
for svc in "${target_services[@]}"; do
    if ! deploy_single "$svc" "$TAG"; then
        failed+=("$svc")
    fi
done

# ---- 汇总 ----
section "部署汇总"

if [[ ${#failed[@]} -eq 0 ]]; then
    ok "全部服务部署成功!"
    echo ""
    echo "部署的服务:"
    for s in "${target_services[@]}"; do echo "  - $s:$TAG"; done
    echo ""
    echo "下一步:"
    echo "  健康检查: ./scripts/health-check.sh --namespace $NAMESPACE"
    echo "  如需回滚: ./scripts/rollback.sh --service all --namespace $NAMESPACE"
    exit 0
else
    err "以下服务部署失败:"
    for s in "${failed[@]}"; do echo "  - $s"; done
    echo ""
    warn "建议对失败服务执行回滚:"
    for s in "${failed[@]}"; do
        echo "  ./scripts/rollback.sh --service $s --namespace $NAMESPACE"
    done
    exit 1
fi
