# ============================================================
# LabOA 后端 — 多阶段构建（无需本地 Maven/Java）
# ============================================================

# ---- 阶段1：Maven 构建 ----
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 1. 先复制 pom.xml 全量下载依赖（利用 Docker 缓存层）
COPY pom.xml ./
COPY lab-oa-common/pom.xml ./lab-oa-common/
COPY lab-oa-security/pom.xml ./lab-oa-security/
COPY lab-oa-system/pom.xml ./lab-oa-system/
COPY lab-oa-file/pom.xml ./lab-oa-file/
COPY lab-oa-doc/pom.xml ./lab-oa-doc/
COPY lab-oa-literature/pom.xml ./lab-oa-literature/
COPY lab-oa-search/pom.xml ./lab-oa-search/
COPY lab-oa-notification/pom.xml ./lab-oa-notification/
COPY lab-oa-rag/pom.xml ./lab-oa-rag/
COPY lab-oa-web/pom.xml ./lab-oa-web/

RUN mvn dependency:go-offline -B -q || true

# 2. 复制源码并编译打包
COPY lab-oa-common/src ./lab-oa-common/src
COPY lab-oa-security/src ./lab-oa-security/src
COPY lab-oa-system/src ./lab-oa-system/src
COPY lab-oa-file/src ./lab-oa-file/src
COPY lab-oa-doc/src ./lab-oa-doc/src
COPY lab-oa-literature/src ./lab-oa-literature/src
COPY lab-oa-search/src ./lab-oa-search/src
COPY lab-oa-notification/src ./lab-oa-notification/src
COPY lab-oa-rag/src ./lab-oa-rag/src
COPY lab-oa-web/src ./lab-oa-web/src

RUN mvn clean package -DskipTests -B -q

# ---- 阶段2：运行环境 ----
FROM eclipse-temurin:17-jre-alpine

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

RUN mkdir -p /app/logs
WORKDIR /app

COPY --from=builder /build/lab-oa-web/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
