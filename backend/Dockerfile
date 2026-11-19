# ---------- 阶段 1：Maven 构建 Spring Boot 可执行 jar ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先复制 POM，复用依赖定义层。
COPY pom.xml .

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- 阶段 2：仅保留 JRE 运行 ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 只复制 fat jar，源码、Maven、构建缓存都不进入运行镜像
COPY --from=build /build/target/secops-copilot-0.1.0.jar /app/app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080

# exec 保证 SIGTERM 能传递给 JVM，容器可优雅停止
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
