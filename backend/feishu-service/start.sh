#!/bin/bash
echo "Starting Feishu Bot Service..."

cd "$(dirname "$0")"
mvn spring-boot:run -Dspring-boot.run.profiles=local
