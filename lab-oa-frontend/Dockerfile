# ============================================================
# LabOA 前端 — 多阶段构建（无需本地 Node.js）
# ============================================================

# ---- 阶段1：Node.js 构建 ----
FROM node:20-alpine AS builder

WORKDIR /build

# 1. 复制依赖描述文件
COPY package.json package-lock.json ./

# 2. 安装依赖
RUN npm ci

# 3. 复制源码并构建
COPY . .
RUN npm run build

# ---- 阶段2：Nginx 运行 ----
FROM nginx:1.27-alpine

# 时区
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 自定义 Nginx 配置
COPY nginx.conf /etc/nginx/nginx.conf

# 复制构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
