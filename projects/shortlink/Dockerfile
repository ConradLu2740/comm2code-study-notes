# ============================================================
# 多阶段构建 Dockerfile —— Spring Boot 短链接服务
# ------------------------------------------------------------
# 用法（在项目根目录）：
#   docker build -t shortlink:0.2.0 .
#   docker run -p 8080:8080 shortlink:0.2.0
#
# 教学点：
#   - 多阶段构建：第一阶段用 JDK + Maven 编译出 jar，
#     第二阶段只用 JRE 运行，最终镜像 ~250MB（全 JDK 镜像 ~500MB+）
#   - 先复制 pom.xml 再下载依赖：利用 Docker 缓存层，
#     只要 pom.xml 没变，依赖层就不会重新下载（依赖下载是国内最慢的一步）
#   - 用阿里云 Maven 镜像源 settings.xml 加速依赖下载
# ============================================================

# ===================== 阶段 1：构建 =====================
# 选具体版本（不用 latest），构建结果可复现
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 阿里云 Maven 镜像源：把官方仓库替换成国内镜像，依赖下载速度提升 5-10 倍
# 用 RUN 直接写到 ~/.m2/settings.xml，避免再 COPY 一个配置文件
RUN mkdir -p /root/.m2 && \
    cat > /root/.m2/settings.xml <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <name>Aliyun Public Mirror</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
    <mirror>
      <id>aliyun-spring</id>
      <name>Aliyun Spring Mirror</name>
      <url>https://maven.aliyun.com/repository/spring</url>
      <mirrorOf>spring-milestones,spring-snapshots</mirrorOf>
    </mirror>
  </mirrors>
</settings>
EOF

# 先复制 pom.xml 并下载依赖 —— 这一层会被 Docker 缓存
# 只要 pom.xml 没变，下次构建会直接复用依赖层（不重新跑 mvn dependency）
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再复制源码并打包
# -DskipTests：镜像里不跑单测（CI 阶段跑过了）
# -B：批处理模式，日志更干净
COPY src ./src
RUN mvn -B -DskipTests package

# ===================== 阶段 2：运行 =====================
# 用最小的 JRE 镜像（Alpine 体积小），不再带 Maven / JDK
FROM eclipse-temurin:17-jre-alpine

# 元数据
LABEL maintainer="conrad" \
      description="Spring Boot 短链接服务（多阶段构建示例）" \
      version="0.2.0"

WORKDIR /app

# 从 builder 阶段只拷出 jar 文件，不带任何构建工具
# jar 名字来自 pom.xml：<artifactId>shortlink</artifactId> <version>0.2.0</version>
COPY --from=builder /build/target/shortlink-0.2.0.jar app.jar

# 暴露 8080 端口（与 application.yml server.port 一致）
EXPOSE 8080

# JVM 容器友好参数：
#   -XX:+UseContainerSupport    识别 cgroup 内存限制
#   -XX:MaxRAMPercentage=75.0   堆内存上限为容器内存的 75%
#   -Djava.security.egd         加速 Tomcat 启动（避开 /dev/random 阻塞）
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]