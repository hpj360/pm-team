#!/bin/bash
echo "Starting Feishu Bot Service..."

export FEISHU_APP_ID=cli_a9514eb7a2f8dbcd
export FEISHU_APP_SECRET=OG6uRrZhAngOtvGoqLjnpfxJ6ePQWmTM
export AGENT_SERVICE_URL=http://localhost:8080

cd "$(dirname "$0")"
mvn spring-boot:run
