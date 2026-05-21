#!/bin/bash 
 # ============================================ 
 # TRAE IDE 本地环境检查脚本 
 # 使用方法：在 TRAE IDE 终端中运行 
 #   bash env-check.sh 
 # 然后将输出结果贴给 Solo 云端进行对比 
 # ============================================ 
 
 echo "============================================" 
 echo "  TRAE IDE 本地环境检查报告" 
 echo "  生成时间: $(date '+%Y-%m-%d %H:%M:%S')" 
 echo "============================================" 
 echo "" 
 
 # 1. 系统信息 
 echo "========== 1. 系统信息 ==========" 
 echo "OS: $(uname -s) $(uname -r)" 
 echo "Arch: $(uname -m)" 
 if [ -f /etc/os-release ]; then 
     echo "Distro: $(grep PRETTY_NAME /etc/os-release | cut -d'"' -f2)" 
 fi 
 if [[ "$OSTYPE" == "darwin"* ]]; then 
     echo "macOS: $(sw_vers -productVersion 2>/dev/null || echo 'unknown')" 
 fi 
 echo "" 
 
 # 2. Shell 信息 
 echo "========== 2. Shell ==========" 
 echo "Current Shell: $SHELL" 
 echo "Bash Version: $(bash --version 2>&1 | head -1)" 
 echo "" 
 
 # 3. 环境变量 
 echo "========== 3. 关键环境变量 ==========" 
 echo "HOME=$HOME" 
 echo "PATH=$(echo $PATH | tr ':' '\n' | head -15)" 
 echo "JAVA_HOME=${JAVA_HOME:-NOT SET}" 
 echo "PYTHONPATH=${PYTHONPATH:-NOT SET}" 
 echo "NODE_PATH=${NODE_PATH:-NOT SET}" 
 echo "GOPATH=${GOPATH:-NOT SET}" 
 echo "HTTP_PROXY=${HTTP_PROXY:-NOT SET}" 
 echo "HTTPS_PROXY=${HTTPS_PROXY:-NOT SET}" 
 echo "NO_PROXY=${NO_PROXY:-NOT SET}" 
 echo "LANG=${LANG:-NOT SET}" 
 echo "LC_ALL=${LC_ALL:-NOT SET}" 
 echo "" 
 
 # 4. 开发工具链 
 echo "========== 4. 开发工具链 ==========" 
 echo -n "Node.js: " && (node --version 2>&1 || echo "NOT INSTALLED") 
 echo -n "npm: " && (npm --version 2>&1 || echo "NOT INSTALLED") 
 echo -n "Python: " && (python3 --version 2>&1 || echo "NOT INSTALLED") 
 echo -n "pip: " && (pip3 --version 2>&1 || echo "NOT INSTALLED") 
 echo -n "Java: " && (java -version 2>&1 | head -1 || echo "NOT INSTALLED") 
 echo -n "Git: " && (git --version 2>&1 || echo "NOT INSTALLED") 
 echo -n "Go: " && (go version 2>&1 || echo "NOT INSTALLED") 
 echo -n "Docker: " && (docker --version 2>&1 || echo "NOT INSTALLED") 
 echo -n "Bun: " && (bun --version 2>&1 || echo "NOT INSTALLED") 
 echo -n "Rust: " && (rustc --version 2>&1 || echo "NOT INSTALLED") 
 echo -n "Cargo: " && (cargo --version 2>&1 || echo "NOT INSTALLED") 
 echo -n "Maven: " && (mvn --version 2>&1 | head -1 || echo "NOT INSTALLED") 
 echo -n "Gradle: " && (gradle --version 2>&1 | grep "Gradle" || echo "NOT INSTALLED") 
 echo "" 
 
 # 5. npm 全局包 
 echo "========== 5. npm 全局包 ==========" 
 npm list -g --depth=0 2>&1 
 echo "" 
 
 # 6. pip 包（前 20 个） 
 echo "========== 6. pip 已安装包（前 20）==========" 
 pip3 list 2>/dev/null | head -20 || echo "pip3 not available" 
 echo "" 
 
 # 7. Git 配置 
 echo "========== 7. Git 配置 ==========" 
 echo "user.name: $(git config --global user.name 2>&1 || echo 'NOT SET')" 
 echo "user.email: $(git config --global user.email 2>&1 || echo 'NOT SET')" 
 echo "defaultBranch: $(git config --global init.defaultbranch 2>&1 || echo 'NOT SET')" 
 echo "credential.helper: $(git config --global credential.helper 2>&1 || echo 'NOT SET')" 
 echo "" 
 
 # 8. SSH 配置 
 echo "========== 8. SSH 配置 ==========" 
 if [ -d ~/.ssh ]; then 
     echo "SSH dir exists: YES" 
     echo "Files:" 
     ls -la ~/.ssh/ 2>&1 
 else 
     echo "SSH dir exists: NO" 
 fi 
 echo "" 
 
 # 9. 代理配置 
 echo "========== 9. 代理配置 ==========" 
 echo "git proxy: $(git config --global http.proxy 2>&1 || echo 'NOT SET')" 
 echo "npm proxy: $(npm config get proxy 2>&1 || echo 'NOT SET')" 
 echo "" 
 
 # 10. 磁盘与内存 
 echo "========== 10. 系统资源 ==========" 
 if [[ "$OSTYPE" == "darwin"* ]]; then 
     echo "Disk:" && df -h / | tail -1 
     echo "Memory:" && vm_stat | head -5 
 else 
     echo "Disk:" && df -h / | tail -1 
     echo "Memory:" && free -h | head -2 
 fi 
 echo "" 
 
 echo "============================================" 
 echo "  检查完成！请将以上输出结果贴给 Solo 云端" 
 echo "============================================"