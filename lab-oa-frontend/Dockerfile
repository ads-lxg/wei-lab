# ============================================================
# LabOA 前端 — 多阶段构建
# Stage 1: 构建 Vue 应用
# Stage 2: Nginx 运行
# ============================================================

# ----- Stage 1: Build -----
FROM node:20-alpine AS builder

WORKDIR /app

# 安装依赖
COPY package.json package-lock.json* ./
RUN npm ci --registry=https://registry.npmmirror.com

# 复制源码构建
COPY . .
RUN npm run build

# ----- Stage 2: Runtime -----
FROM nginx:1.27-alpine

# 时区
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 自定义 Nginx 配置
COPY nginx.conf /etc/nginx/nginx.conf

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
