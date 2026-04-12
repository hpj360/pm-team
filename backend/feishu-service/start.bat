@echo off
echo Starting Feishu Bot Service...

set FEISHU_APP_ID=cli_a9514eb7a2f8dbcd
set FEISHU_APP_SECRET=OG6uRrZhAngOtvGoqLjnpfxJ6ePQWmTM
set AGENT_SERVICE_URL=http://localhost:8080

cd /d %~dp0
mvn spring-boot:run

pause
