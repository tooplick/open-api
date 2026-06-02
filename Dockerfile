# ---- 前端构建:Node 编译 Vue,产出静态资源 ----
FROM node:20-alpine AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# ---- 后端构建:Maven + JDK17,把前端产物一起打进可执行 jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷 pom 预解析依赖,利用层缓存(改代码时不必重新下载依赖)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 拷源码,并把前端构建产物放进 Spring Boot 静态目录(随 jar 一起打包)
COPY src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -B -q -Dmaven.test.skip=true clean package

# ---- 运行阶段:仅 JRE,单镜像同时托管前端与 API ----
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /build/target/ai-open-platform-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8321
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
