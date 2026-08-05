<#
.SYNOPSIS
    红方文件汇聚平台 - K8s 健康检查脚本 (Windows PowerShell)
.DESCRIPTION
    检查所有服务的 Pod 状态、Deployment 健康度、就绪探针、最近事件
    检查 HPA、PVC、Endpoint 状态
.PARAMETER Namespace
    K8s 命名空间 (默认 redteam-platform)
.PARAMETER Service
    只检查指定服务 (默认全部)
.PARAMETER Watch
    持续监控模式 (每 10s 刷新)
.PARAMETER ExitOnUnhealthy
    发现不健康 Pod 时退出码 1 (用于 CI/CD)
.EXAMPLE
    .\scripts\health-check.ps1
    .\scripts\health-check.ps1 -Service auth-service
    .\scripts\health-check.ps1 -ExitOnUnhealthy
#>
[CmdletBinding()]
param(
    [string]$Namespace = 'redteam-platform',

    [string]$Service = '',

    [switch]$Watch,

    [switch]$ExitOnUnhealthy,

    [int]$WatchInterval = 10
)

$ErrorActionPreference = 'Continue'

function Write-Section($msg) {
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host " $msg" -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan
}
function Write-Step($msg) { Write-Host "[STEP] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "[ERROR] $msg" -ForegroundColor Red }

function Get-ClusterHealth {
    $unhealthyCount = 0

    # ============== 1. Pod 状态 ==============
    Write-Section "1. Pod 状态"
    $labelSelector = if ($Service) { "app.kubernetes.io/name=${Service}" } else { "" }
    $podsArgs = @('get', 'pods', '-n', $Namespace, '-o', 'wide')
    if ($labelSelector) { $podsArgs += @('-l', $labelSelector) }

    $podsYaml = & kubectl @podsArgs -o json 2>$null | ConvertFrom-Json
    if (-not $podsYaml.items) {
        Write-Warn "未找到 Pod"
        return 1
    }

    $unhealthyPods = @()
    Write-Host ("{0,-40} {1,-12} {2,-10} {3,-15} {4}" -f "NAME","READY","STATUS","RESTARTS","AGE") -ForegroundColor White
    foreach ($pod in $podsYaml.items) {
        $ready = ($pod.status.containerStatuses | Where-Object { $_.ready } | Measure-Object).Count
        $total = $pod.status.containerStatuses.Count
        $readyStr = "${ready}/${total}"
        $status = $pod.status.phase
        $restarts = ($pod.status.containerStatuses | Measure-Object -Property restartCount -Sum).Sum
        $age = $pod.metadata.creationTimestamp

        $color = 'White'
        if ($status -ne 'Running' -or $ready -lt $total) {
            $color = 'Red'
            $unhealthyPods += $pod
            $script:unhealthyCount++
        } elseif ($restarts -gt 3) {
            $color = 'Yellow'
        }
        Write-Host ("{0,-40} {1,-12} {2,-10} {3,-15} {4}" -f $pod.metadata.name, $readyStr, $status, $restarts, $age) -ForegroundColor $color
    }

    if ($unhealthyPods.Count -gt 0) {
        Write-Warn "发现 $($unhealthyPods.Count) 个不健康 Pod"
    } else {
        Write-Host "[OK] 所有 Pod 健康" -ForegroundColor Green
    }

    # ============== 2. Deployment 状态 ==============
    Write-Section "2. Deployment 状态"
    $depArgs = @('get', 'deployments', '-n', $Namespace)
    if ($Service) { $depArgs += @('-l', "app.kubernetes.io/name=${Service}") }

    $depsYaml = & kubectl @depArgs -o json 2>$null | ConvertFrom-Json
    if ($depsYaml.items) {
        Write-Host ("{0,-30} {1,-10} {2,-10} {3,-10} {4}" -f "NAME","DESIRED","CURRENT","UP-TO-DATE","AVAILABLE") -ForegroundColor White
        foreach ($dep in $depsYaml.items) {
            $name = $dep.metadata.name
            $desired = $dep.spec.replicas
            $current = $dep.status.replicas
            $uptodate = $dep.status.updatedReplicas
            $available = $dep.status.availableReplicas
            $color = if ($available -eq $desired) { 'Green' } else { 'Red'; $script:unhealthyCount++ }
            Write-Host ("{0,-30} {1,-10} {2,-10} {3,-10} {4}" -f $name, $desired, $current, $uptodate, $available) -ForegroundColor $color
        }
    }

    # ============== 3. HPA 状态 ==============
    Write-Section "3. HPA 状态"
    $hpaArgs = @('get', 'hpa', '-n', $Namespace)
    if ($Service) { $hpaArgs += @('-l', "app.kubernetes.io/name=${Service}") }
    $hpaOut = & kubectl @hpaArgs 2>$null
    if ($hpaOut) { Write-Host $hpaOut } else { Write-Warn "无 HPA" }

    # ============== 4. Service / Endpoint ==============
    Write-Section "4. Service 与 Endpoint"
    $svcArgs = @('get', 'svc', '-n', $Namespace)
    if ($Service) { $svcArgs += @('-l', "app.kubernetes.io/name=${Service}") }
    $svcOut = & kubectl @svcArgs 2>$null
    if ($svcOut) { Write-Host $svcOut }

    Write-Host ""
    Write-Step "Endpoint 就绪检查..."
    $epsYaml = & kubectl get endpoints -n $Namespace -o json 2>$null | ConvertFrom-Json
    if ($epsYaml.items) {
        foreach ($ep in $epsYaml.items) {
            $addrCount = 0
            if ($ep.subsets) {
                foreach ($subset in $ep.subsets) {
                    if ($subset.addresses) { $addrCount += $subset.addresses.Count }
                }
            }
            $color = if ($addrCount -gt 0) { 'Green' } else { 'Red'; $script:unhealthyCount++ }
            Write-Host ("  {0,-30} Endpoints: {1}" -f $ep.metadata.name, $addrCount) -ForegroundColor $color
        }
    }

    # ============== 5. 最近事件 (问题 Pod) ==============
    if ($unhealthyPods.Count -gt 0) {
        Write-Section "5. 不健康 Pod 事件"
        foreach ($pod in $unhealthyPods) {
            Write-Warn "Pod: $($pod.metadata.name)"
            & kubectl describe pod $pod.metadata.name -n $Namespace 2>$null | Select-String -Pattern "Events:" -Context 0,15
            Write-Host ""
        }
    }

    # ============== 6. 节点资源 ==============
    Write-Section "6. 节点资源"
    & kubectl top nodes 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "metrics-server 未安装, 跳过节点资源统计"
    }

    return $script:unhealthyCount
}

# ============== 主流程 ==============
Write-Section "红方文件汇聚平台 - K8s 健康检查"
Write-Host "命名空间: $Namespace"
if ($Service) { Write-Host "服务: $Service" }
Write-Host "时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"

if ($Watch) {
    Write-Warn "持续监控模式 (每 ${WatchInterval}s 刷新, Ctrl+C 退出)"
    while ($true) {
        Clear-Host
        $script:unhealthyCount = 0
        Get-ClusterHealth | Out-Null
        Start-Sleep -Seconds $WatchInterval
    }
} else {
    $script:unhealthyCount = 0
    $result = Get-ClusterHealth
    Write-Section "汇总"
    if ($script:unhealthyCount -eq 0) {
        Write-Host "[OK] 集群健康" -ForegroundColor Green
        if ($ExitOnUnhealthy) { exit 0 }
    } else {
        Write-Err "发现 $script:unhealthyCount 处不健康"
        if ($ExitOnUnhealthy) { exit 1 }
    }
}
