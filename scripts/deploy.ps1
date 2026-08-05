<#
.SYNOPSIS
    红方文件汇聚平台 - K8s 部署脚本 (Windows PowerShell)
.DESCRIPTION
    完整部署流程: 验证环境 -> 构建/拉取镜像 -> 滚动更新 -> 等待就绪 -> 健康检查
.PARAMETER Service
    要部署的服务名 (auth-service, upload-service, frontend, all)
.PARAMETER Tag
    镜像 tag (默认使用 git short SHA)
.PARAMETER Namespace
    K8s 命名空间 (默认 redteam-platform)
.PARAMETER Registry
    镜像仓库地址
.PARAMETER NoWait
    不等待 rollout 完成
.EXAMPLE
    .\scripts\deploy.ps1 -Service auth-service -Tag v1.0.0
    .\scripts\deploy.ps1 -Service all -Tag latest
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('auth-service','upload-service','parse-service','search-service','analyze-service',
                 'profile-service','task-service','notification-service','report-service','feishu-service',
                 'frontend','all')]
    [string]$Service,

    [string]$Tag = $(git rev-parse --short HEAD 2>$null; if (-not $?) { 'latest' }),

    [string]$Namespace = 'redteam-platform',

    [string]$Registry = 'registry.example.com/redteam',

    [switch]$NoWait,

    [int]$TimeoutSeconds = 600
)

$ErrorActionPreference = 'Stop'
$PSStyle.Progress.View = 'Minimal'

# ============== 工具函数 ==============
function Write-Section($msg) {
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host " $msg" -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan
}

function Write-Step($msg) {
    Write-Host "[STEP] $msg" -ForegroundColor Green
}

function Write-Warn($msg) {
    Write-Host "[WARN] $msg" -ForegroundColor Yellow
}

function Write-Err($msg) {
    Write-Host "[ERROR] $msg" -ForegroundColor Red
}

function Test-Command($cmd) {
    return [bool](Get-Command $cmd -ErrorAction SilentlyContinue)
}

function Invoke-Kubectl($args) {
    $output = & kubectl @args 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Err "kubectl 执行失败: kubectl $args"
        Write-Err $output
        exit 1
    }
    return $output
}

# ============== 前置检查 ==============
Write-Section "红方文件汇聚平台 - K8s 部署"

Write-Step "前置环境检查..."
if (-not (Test-Command 'kubectl')) {
    Write-Err "未找到 kubectl, 请先安装: https://kubernetes.io/docs/tasks/tools/"
    exit 1
}

if (-not (Test-Command 'git')) {
    Write-Warn "未找到 git, 将使用 'latest' 作为默认 tag"
    $Tag = 'latest'
}

Write-Step "验证 K8s 集群连通性..."
$ns = Invoke-Kubectl @('get', 'namespace', $Namespace, '-o', 'name')
if (-not $ns) {
    Write-Err "命名空间 $Namespace 不存在, 请先执行: kubectl apply -f k8s/namespace.yaml"
    exit 1
}
Write-Host "命名空间 $Namespace 已存在" -ForegroundColor DarkGray

Write-Host ""
Write-Host "部署参数:" -ForegroundColor White
Write-Host "  服务:     $Service" -ForegroundColor White
Write-Host "  镜像 Tag: $Tag" -ForegroundColor White
Write-Host "  命名空间: $Namespace" -ForegroundColor White
Write-Host "  仓库:     $Registry" -ForegroundColor White

# ============== 部署函数 ==============
function Deploy-SingleService($svc, $imageTag) {
    Write-Section "部署 $svc"

    $fullImage = "$Registry/${svc}:$imageTag"
    Write-Step "目标镜像: $fullImage"

    # 1. 检查 deployment 是否存在
    Write-Step "检查 Deployment 是否存在..."
    $dep = & kubectl get deployment $svc -n $Namespace -o name 2>$null
    if (-not $dep) {
        Write-Err "Deployment $svc 不存在, 请先 apply 部署清单"
        Write-Host "提示: kubectl apply -f k8s/${svc}.yaml" -ForegroundColor DarkGray
        return $false
    }

    # 2. 记录当前镜像 (用于回滚)
    $oldImage = & kubectl get deployment $svc -n $Namespace `
        -o jsonpath="{.spec.template.spec.containers[0].image}" 2>$null
    Write-Step "当前镜像: $oldImage"

    # 3. 触发滚动更新
    Write-Step "触发滚动更新..."
    Invoke-Kubectl @('set', 'image', "deployment/${svc}", "${svc}=${fullImage}", '-n', $Namespace)

    if ($NoWait) {
        Write-Warn "已设置 -NoWait, 跳过等待 rollout"
        return $true
    }

    # 4. 等待 rollout 完成
    Write-Step "等待 rollout 完成 (超时 ${TimeoutSeconds}s)..."
    $progressArgs = @('rollout', 'status', "deployment/${svc}", '-n', $Namespace, "--timeout=${TimeoutSeconds}s")
    & kubectl @progressArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Err "$svc rollout 失败!"
        Write-Warn "可使用以下命令查看详情:"
        Write-Host "  kubectl rollout status deployment/$svc -n $Namespace" -ForegroundColor DarkGray
        Write-Host "  kubectl describe deployment $svc -n $Namespace" -ForegroundColor DarkGray
        Write-Host "  kubectl logs -l app=$svc -n $Namespace --tail=50" -ForegroundColor DarkGray
        Write-Warn "建议执行回滚: .\scripts\rollback.ps1 -Service $svc -Namespace $Namespace"
        return $false
    }

    # 5. 显示当前状态
    Write-Step "当前 Pod 状态:"
    & kubectl get pods -n $Namespace -l "app.kubernetes.io/name=${svc}" -o wide

    Write-Host ""
    Write-Host "[OK] $svc 部署成功" -ForegroundColor Green
    return $true
}

# ============== 执行部署 ==============
$allServices = @('auth-service','upload-service','parse-service','search-service','analyze-service',
                 'profile-service','task-service','notification-service','report-service','feishu-service','frontend')

$targetServices = if ($Service -eq 'all') { $allServices } else { @($Service) }

$failedServices = @()
foreach ($svc in $targetServices) {
    $success = Deploy-SingleService -svc $svc -imageTag $Tag
    if (-not $success) {
        $failedServices += $svc
    }
}

# ============== 汇总 ==============
Write-Section "部署汇总"

if ($failedServices.Count -eq 0) {
    Write-Host "[OK] 全部服务部署成功!" -ForegroundColor Green
    Write-Host ""
    Write-Host "部署的服务:" -ForegroundColor White
    foreach ($s in $targetServices) { Write-Host "  - $s`:$Tag" }
    Write-Host ""
    Write-Host "下一步:" -ForegroundColor White
    Write-Host "  健康检查: .\scripts\health-check.ps1 -Namespace $Namespace" -ForegroundColor DarkGray
    Write-Host "  如需回滚: .\scripts\rollback.ps1 -Service all -Namespace $Namespace" -ForegroundColor DarkGray
    exit 0
} else {
    Write-Err "以下服务部署失败:"
    foreach ($s in $failedServices) { Write-Host "  - $s" -ForegroundColor Red }
    Write-Host ""
    Write-Warn "建议对失败服务执行回滚:"
    foreach ($s in $failedServices) {
        Write-Host "  .\scripts\rollback.ps1 -Service $s -Namespace $Namespace" -ForegroundColor DarkGray
    }
    exit 1
}
