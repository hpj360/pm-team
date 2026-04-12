@echo off
echo Starting Feishu Bot Service...

cd /d %~dp0
mvn spring-boot:run -Dspring-boot.run.profiles=local

pause
