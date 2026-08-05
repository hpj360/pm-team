<#
.SYNOPSIS
    红方文件汇聚平台 - K8s 回滚脚本 (Windows PowerShell)
.DESCRIPTION
    回滚一个或多个服务到上一个 Revision; 支持指定 Revision
.PARAMETER Service
    服务名 (auth-service, ..., frontend, all)
.PARAMETER Revision
    回滚到指定 revision (默认上一个 revision)
.PARAMETER Namespace
    K8s 命名空间
.EXAMPLE
    .\scripts\rollback.ps1 -Service auth-service
    .\scripts\rollback.ps1 -Service all
    .\scripts\rollback.ps1 -Service frontend -Revision 3
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('auth-service','upload-service','parse-service','search-service','analyze-service',
                 'profile-service','task-service','notification-service','report-service','feishu-service',
                 'frontend','all')]
    [string]$Service,

    [int]$Revision = 0,

    [string]$Namespace = 'redteam-platform',

    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'

function Write-Section($msg) {
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Magenta
    Write-Host " $msg" -ForegroundColor Magenta
    Write-Host "==========================================" -ForegroundColor Magenta
}
function Write-Step($msg) { Write-Host "[STEP] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "[ERROR] $msg" -ForegroundColor Red }
function Write-Ok($msg)   { Write-Host "[OK] $msg" -ForegroundColor Green }

# ============== 前置检查 ==============
Write-Section "红方文件汇聚平台 - K8s 回滚"

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    Write-Err "未找到 kubectl"
    exit 1
}

Write-Step "验证命名空间..."
if (-not (& kubectl get namespace $Namespace 2>$null)) {
    Write-Err "命名空间 $Namespace 不存在"
    exit 1
}

Write-Host ""
Write-Host "回滚参数:" -ForegroundColor White
Write-Host "  服务:     $Service" -ForegroundColor White
Write-Host "  Revision: $(if ($Revision -gt 0) { $Revision } else { '上一个 (auto)' })" -ForegroundColor White
Write-Host "  命名空间: $Namespace" -ForegroundColor White

# ============== 回滚函数 ==============
function Rollback-SingleService($svc) {
    Write-Section "回滚 $svc"

    # 检查 deployment
    if (-not (& kubectl get deployment $svc -n $Namespace 2>$null)) {
        Write-Err "Deployment $svc 不存在"
        return $false
    }

    # 显示历史 revision
    Write-Step "Deployment 历史:"
    & kubectl rollout history deployment $svc -n $Namespace

    # 记录当前镜像
    $currentImage = & kubectl get deployment $svc -n $Namespace `
        -o jsonpath="{.spec.template.spec.containers[0].image}" 2>$null
    Write-Step "当前镜像: $currentImage"

    # 执行回滚
    if ($Revision -gt 0) {
        Write-Step "回滚到 Revision $Revision..."
        $result = & kubectl rollout undo "deployment/$svc" -n $Namespace "--to-revision=$Revision" 2>&1
    } else {
        Write-Step "回滚到上一个 Revision..."
        $result = & kubectl rollout undo "deployment/$svc" -n $Namespace 2>&1
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Err "回滚命令失败:"
        Write-Err $result
        return $false
    }
    Write-Host $result

    # 等待 rollout 完成
    Write-Step "等待 rollout 完成 (超时 ${TimeoutSeconds}s)..."
    & kubectl rollout status "deployment/$svc" -n $Namespace "--timeout=${TimeoutSeconds}s"
    if ($LASTEXITCODE -ne 0) {
        Write-Err "$svc 回滚 rollout 失败!"
        return $false
    }

    # 显示新镜像
    $newImage = & kubectl get deployment $svc -n $Namespace `
        -o jsonpath="{.spec.template.spec.containers[0].image}" 2>$null
    Write-Step "回滚后镜像: $newImage"

    Write-Step "当前 Pod 状态:"
    & kubectl get pods -n $Namespace -l "app.kubernetes.io/name=${svc}" -o wide

    Write-Ok "$svc 回滚成功"
    return $true
}

# ============== 执行 ==============
$allServices = @('auth-service','upload-service','parse-service','search-service','analyze-service',
                 'profile-service','task-service','notification-service','report-service','feishu-service','frontend')

$targetServices = if ($Service -eq 'all') { $allServices } else { @($Service) }

$failedServices = @()
foreach ($svc in $targetServices) {
    $success = Rollback-SingleService -svc $svc
    if (-not $success) {
        $failedServices += $svc
    }
}

# ============== 汇总 ==============
Write-Section "回滚汇总"

if ($failedServices.Count -eq 0) {
    Write-Ok "全部服务回滚成功!"
    Write-Host ""
    Write-Host "回滚的服务:"
    foreach ($s in $targetServices) { Write-Host "  - $s" }
    Write-Host ""
    Write-Host "下一步:"
    Write-Host "  健康检查: .\scripts\health-check.ps1 -Namespace $Namespace" -ForegroundColor DarkGray
    exit 0
} else {
    Write-Err "以下服务回滚失败:"
    foreach ($s in $failedServices) { Write-Host "  - $s" -ForegroundColor Red }
    exit 1
}
