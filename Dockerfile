# ============================================================
# AI 辅助说明：本文件由 AI 辅助生成/修改
# 生成内容：多阶段 Dockerfile —— 阶段一用 Maven 构建 jar，阶段二用精简 JRE 运行
# 人工修改：无
# 审查状态：待人工审查
# 设计要点：
#   1. 多阶段构建：构建产物只有 jar，运行镜像不含 Maven 和源码，体积从 ~800MB 降到 ~300MB
#   2. 依赖层缓存：先 COPY pom.xml 单独下载依赖，源码变动时依赖层直接命中缓存，重建秒级
#   3. -Xmx256m：对应计划书风险 R3（2G 内存服务器 OOM），限制 JVM 堆上限
# ============================================================

# ---------- 阶段一：构建 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 只拷贝 pom.xml，先下载全部依赖（pom 没变时这层走缓存）
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 拷贝源码并打包（跳过测试：课程实验无测试用例，且测试需要数据库）
COPY src ./src
RUN mvn -B package -DskipTests

# ---------- 阶段二：运行 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 从构建阶段拷出可执行 jar
COPY --from=build /app/target/shopsphere-1.0.0.jar app.jar

# 容器时区与 JVM 堆上限（2G 内存服务器安全值）
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xmx256m -Xms128m"

EXPOSE 8080

# 数据库连接信息全部通过环境变量注入（DB_URL / DB_USERNAME / DB_PASSWORD），见 docker-compose.yml
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
