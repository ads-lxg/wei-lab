# ============================================================
# LabOA 前端 — 使用预构建 dist（本地构建后直接部署）
# ============================================================

FROM nginx:1.27-alpine

# 时区
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 自定义 Nginx 配置
COPY nginx.conf /etc/nginx/nginx.conf

# 直接复制预构建产物
COPY dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
