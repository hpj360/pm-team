# ============================================
# TRAE IDE Local Environment Check Script (PowerShell Version)
# Usage: Run in PowerShell terminal
#   .\env-check.ps1
# Then paste the output to Solo cloud for comparison
# ============================================

Write-Host "============================================"
Write-Host "  TRAE IDE Local Environment Report"
Write-Host "  Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "============================================"
Write-Host ""

# 1. System Information
Write-Host "========== 1. System Information =========="
Write-Host "OS: $((Get-CimInstance Win32_OperatingSystem).Caption)"
Write-Host "OS Version: $((Get-CimInstance Win32_OperatingSystem).Version)"
Write-Host "Arch: $env:PROCESSOR_ARCHITECTURE"
Write-Host "Computer Name: $env:COMPUTERNAME"
Write-Host "User: $env:USERNAME"
Write-Host ""

# 2. PowerShell Information
Write-Host "========== 2. Shell =========="
Write-Host "PowerShell Version: $($PSVersionTable.PSVersion)"
Write-Host "Edition: $($PSVersionTable.PSEdition)"
Write-Host ""

# 3. Environment Variables
Write-Host "========== 3. Key Environment Variables =========="
Write-Host "HOME=$env:USERPROFILE"
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "PYTHONPATH=$env:PYTHONPATH"
Write-Host "NODE_PATH=$env:NODE_PATH"
Write-Host "GOPATH=$env:GOPATH"
Write-Host "HTTP_PROXY=$env:HTTP_PROXY"
Write-Host "HTTPS_PROXY=$env:HTTPS_PROXY"
Write-Host "NO_PROXY=$env:NO_PROXY"
Write-Host "LANG=$env:LANG"
Write-Host "LC_ALL=$env:LC_ALL"
Write-Host ""

# PATH variable (first 15 entries)
Write-Host "PATH (first 15):"
$env:PATH -split ';' | Select-Object -First 15 | ForEach-Object { Write-Host "  $_" }
Write-Host ""

# 4. Development Toolchain
Write-Host "========== 4. Development Toolchain =========="

function Test-Command($cmd) {
    try { Get-Command $cmd -ErrorAction Stop | Out-Null; return $true } catch { return $false }
}

function Get-CommandVersion($cmd, $args = "--version") {
    try {
        if (Test-Command $cmd) {
            $output = & $cmd $args 2>&1
            if ($output) { $output.ToString().Split("`n")[0].Trim() } else { "Installed but no version output" }
        } else { "NOT INSTALLED" }
    } catch { "NOT INSTALLED" }
}

$nodeVer = Get-CommandVersion "node"
Write-Host "Node.js: $nodeVer"

$npmVer = Get-CommandVersion "npm"
Write-Host "npm: $npmVer"

$pythonVer = Get-CommandVersion "python3"
if ($pythonVer -eq "NOT INSTALLED") { $pythonVer = Get-CommandVersion "python" }
Write-Host "Python: $pythonVer"

$pipVer = Get-CommandVersion "pip3"
if ($pipVer -eq "NOT INSTALLED") { $pipVer = Get-CommandVersion "pip" }
Write-Host "pip: $pipVer"

Write-Host "Java:"
try {
    if (Test-Command "java") {
        $javaVersion = & java -version 2>&1
        $javaVersion[0]
    } else { Write-Host "  NOT INSTALLED" }
} catch { Write-Host "  NOT INSTALLED" }

$gitVer = Get-CommandVersion "git"
Write-Host "Git: $gitVer"

$goVer = Get-CommandVersion "go"
Write-Host "Go: $goVer"

Write-Host "Docker:"
try {
    if (Test-Command "docker") {
        $dockerVer = & docker --version 2>&1
        Write-Host "  $dockerVer"
    } else { Write-Host "  NOT INSTALLED" }
} catch { Write-Host "  NOT INSTALLED" }

$bunVer = Get-CommandVersion "bun"
Write-Host "Bun: $bunVer"

$rustVer = Get-CommandVersion "rustc"
Write-Host "Rust: $rustVer"

$cargoVer = Get-CommandVersion "cargo"
Write-Host "Cargo: $cargoVer"

Write-Host "Maven:"
try {
    if (Test-Command "mvn") {
        $mvnVer = & mvn -version 2>&1 | Select-Object -First 1
        Write-Host "  $mvnVer"
    } else { Write-Host "  NOT INSTALLED" }
} catch { Write-Host "  NOT INSTALLED" }

Write-Host "Gradle:"
try {
    if (Test-Command "gradle") {
        $gradleVer = & gradle --version 2>&1 | Select-String "Gradle" | Select-Object -First 1
        Write-Host "  $gradleVer"
    } else { Write-Host "  NOT INSTALLED" }
} catch { Write-Host "  NOT INSTALLED" }

Write-Host ""

# 5. npm Global Packages
Write-Host "========== 5. npm Global Packages =========="
try {
    if (Test-Command "npm") {
        npm list -g --depth=0 2>&1
    } else { Write-Host "npm not available" }
} catch { Write-Host "npm not available" }
Write-Host ""

# 6. pip Packages (first 20)
Write-Host "========== 6. pip Installed Packages (first 20)==========="
try {
    if (Test-Command "pip3") {
        pip3 list 2>&1 | Select-Object -First 20
    } elseif (Test-Command "pip") {
        pip list 2>&1 | Select-Object -First 20
    } else { Write-Host "pip not available" }
} catch { Write-Host "pip not available" }
Write-Host ""

# 7. Git Configuration
Write-Host "========== 7. Git Configuration =========="
try {
    if (Test-Command "git") {
        $gitName = git config --global user.name 2>&1
        $gitEmail = git config --global user.email 2>&1
        $gitBranch = git config --global init.defaultbranch 2>&1
        $gitHelper = git config --global credential.helper 2>&1
        if (-not $gitName) { $gitName = "NOT SET" }
        if (-not $gitEmail) { $gitEmail = "NOT SET" }
        if (-not $gitBranch) { $gitBranch = "NOT SET" }
        if (-not $gitHelper) { $gitHelper = "NOT SET" }
        Write-Host "user.name: $gitName"
        Write-Host "user.email: $gitEmail"
        Write-Host "defaultBranch: $gitBranch"
        Write-Host "credential.helper: $gitHelper"
    } else { Write-Host "Git not available" }
} catch { Write-Host "Git not available" }
Write-Host ""

# 8. SSH Configuration
Write-Host "========== 8. SSH Configuration =========="
$sshDir = "$env:USERPROFILE\.ssh"
if (Test-Path $sshDir) {
    Write-Host "SSH dir exists: YES"
    Write-Host "Files:"
    Get-ChildItem $sshDir | Format-Table Name, Length, LastWriteTime -AutoSize
} else {
    Write-Host "SSH dir exists: NO"
}
Write-Host ""

# 9. Proxy Configuration
Write-Host "========== 9. Proxy Configuration =========="
try {
    if (Test-Command "git") {
        $gitProxy = git config --global http.proxy 2>&1
        if (-not $gitProxy) { $gitProxy = "NOT SET" }
        Write-Host "git proxy: $gitProxy"
    }
} catch { }
try {
    if (Test-Command "npm") {
        $npmProxy = npm config get proxy 2>&1
        if (-not $npmProxy) { $npmProxy = "NOT SET" }
        Write-Host "npm proxy: $npmProxy"
    }
} catch { }
Write-Host ""

# 10. Disk and Memory
Write-Host "========== 10. System Resources =========="
Write-Host "Disk:"
Get-CimInstance Win32_LogicalDisk | Where-Object { $_.DriveType -eq 3 } | Format-Table DeviceID, @{N="Size(GB)";E={[math]::Round($_.Size/1GB,2)}}, @{N="Free(GB)";E={[math]::Round($_.FreeSpace/1GB,2)}} -AutoSize
Write-Host "Memory:"
$mem = Get-CimInstance Win32_OperatingSystem
Write-Host "  Total: $([math]::Round($mem.TotalVisibleMemorySize/1MB, 2)) GB"
Write-Host "  Free: $([math]::Round($mem.FreePhysicalMemory/1MB, 2)) GB"
Write-Host ""

Write-Host "============================================"
Write-Host "  Check Complete! Please paste output to Solo cloud"
Write-Host "============================================"