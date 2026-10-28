# ============================================================
# AI 辅助说明：本文件由 AI 辅助生成/修改
# 生成内容：Dockerfile（Day 3 部署预演用，提前排 R3 风险；Day 7 正式上线复用）
# 人工修改：无
# 审查状态：待人工审查
# ============================================================
# 单阶段构建：jar 由本地 mvn package 产出，镜像只负责运行。
# （进阶做法是多阶段构建在镜像内编译，MVP 不需要——本地构建更快更省）
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/shopsphere-1.0.0.jar app.jar
EXPOSE 8080
# 数据库地址等环境差异通过 -e 环境变量注入（application.yml 里的 ${...} 占位符）
ENTRYPOINT ["java", "-jar", "app.jar"]
