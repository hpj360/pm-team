# =============================================================================
# auth-service Dockerfile - 多阶段构建
# Stage 1: Maven build (JDK 17)
# Stage 2: JRE 17 运行
# 构建命令:
#   docker build -t redteam/auth-service:latest -f backend/auth-service/Dockerfile backend/auth-service
# =============================================================================
# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
LABEL stage=builder

# 设置工作目录
WORKDIR /build

# 先复制 pom.xml 加速依赖缓存
COPY pom.xml ./
COPY src ./src

# 如有父级 pom (common 库), 取消下面注释
# COPY ../common ./common

# Maven 构建: 跳过测试, 产出可执行 fat-jar
# -Dmaven.test.skip=true 跳过测试, CI 流水线中已单独执行测试
RUN mvn -B clean package -Dmaven.test.skip=true \
    -Dmaven.javadoc.skip=true \
    -Dspring-boot.run.profiles=prod

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre

LABEL org.opencontainers.image.title="redteam-auth-service" \
      org.opencontainers.image.description="红方文件汇聚平台 - 认证服务" \
      org.opencontainers.image.source="https://github.com/redteam/pm-team" \
      org.opencontainers.image.licenses="MIT"

# 安装最小化运维工具: curl(健康检查) tzdata(时区) dumb-init(信号转发)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata dumb-init \
    && rm -rf /var/lib/apt/lists/* \
    && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 创建非 root 运行用户
RUN groupadd -r app && useradd -r -g app -d /home/app -m app

WORKDIR /app

# 从 builder 阶段复制构建产物
COPY --from=builder /build/target/*.jar /app/app.jar

# 创建日志目录
RUN mkdir -p /app/logs && chown -R app:app /app

USER app

EXPOSE 8080

# JVM 参数: 容器感知内存, GC 日志, 健康检查
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=Asia/Shanghai" \
    SPRING_PROFILES_ACTIVE=prod

# 健康检查 (Spring Boot Actuator)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
    CMD curl -fs http://localhost:8080/actuator/health || exit 1

# 使用 dumb-init 转发信号, 避免僵尸进程
ENTRYPOINT ["dumb-init", "--"]
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
