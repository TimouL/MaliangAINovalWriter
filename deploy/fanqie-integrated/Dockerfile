## Stage 1: build fanqie frontend
FROM node:20-bullseye AS fanqie-frontend
WORKDIR /workspace/frontend
COPY novel-fanqie-reader/frontend/ ./
ENV VITE_BASE=/fq/ \
    VITE_API_BASE=/fq-api \
    VITE_SOCKET_PATH=/fq-api/socket.io
RUN npm install && npm run build-only

## Stage 2: runtime with Java + Python + supervisord
FROM eclipse-temurin:21-jre

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    JVM_XMS=512m \
    JVM_XMX=1024m \
    DB_TYPE=sqlite \
    SQLITE_DB_FILE=/data/fanqie.db \
    DATA_BASE_PATH=/data \
    FANQIE_WORKERS=1 \
    CELERY_BROKER_URL=redis://127.0.0.1:6379/0 \
    CELERY_RESULT_BACKEND=redis://127.0.0.1:6379/1 \
    MAIN_JAR=/app/ainoval-server.jar \
    SPRING_STATIC_LOCATIONS=file:/app/web/,file:/app/admin_web/

# Minimal build deps for Python packages (lxml, wordcloud, matplotlib)
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 python3-pip supervisor nginx redis-server \
    build-essential gcc libxml2-dev libxslt1-dev libffi-dev \
    libjpeg-dev zlib1g-dev libfreetype6-dev libpng-dev \
    curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy main app artifacts
COPY deploy/dist/ainoval-server.jar /app/ainoval-server.jar
COPY deploy/dist/web/ /app/web/
COPY deploy/dist/admin_web/ /app/admin_web/

# Copy fanqie backend
COPY novel-fanqie-reader/backend/ /app/novel-fanqie-reader/backend/
RUN pip3 install --no-cache-dir --break-system-packages -r /app/novel-fanqie-reader/backend/requirements.txt

# Copy built fanqie frontend into web/fq for static serving via Spring
RUN mkdir -p /app/web/fq/
COPY --from=fanqie-frontend /workspace/frontend/dist/ /app/web/fq/

# Entrypoint & supervisord
COPY deploy/fanqie-integrated/entrypoint.sh /app/entrypoint.sh
COPY deploy/fanqie-integrated/supervisord.conf /app/supervisord.conf
COPY deploy/fanqie-integrated/nginx.conf.sample /etc/nginx/nginx.conf
RUN chmod +x /app/entrypoint.sh && mkdir -p /data /tmp /var/log/ainoval && chmod -R 777 /data /tmp /var/log/ainoval

EXPOSE 18080

CMD ["/usr/bin/supervisord", "-c", "/app/supervisord.conf"]
