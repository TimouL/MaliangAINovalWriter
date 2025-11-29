## Simplified Dockerfile - Direct third-party API mode (no Python/Redis)
FROM eclipse-temurin:21-jre

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    JVM_XMS=512m \
    JVM_XMX=1024m \
    MAIN_JAR=/app/ainoval-server.jar \
    SPRING_STATIC_LOCATIONS=file:/app/web/,file:/app/admin_web/

RUN apt-get update && apt-get install -y --no-install-recommends \
    supervisor nginx curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy main app artifacts
COPY deploy/dist/ainoval-server.jar /app/ainoval-server.jar
COPY deploy/dist/web/ /app/web/
COPY deploy/dist/admin_web/ /app/admin_web/

# Entrypoint & supervisord
COPY deploy/fanqie-integrated/entrypoint.sh /app/entrypoint.sh
COPY deploy/fanqie-integrated/supervisord.conf /app/supervisord.conf
COPY deploy/fanqie-integrated/nginx.conf.sample /etc/nginx/nginx.conf
RUN chmod +x /app/entrypoint.sh && mkdir -p /data /tmp /var/log/ainoval && chmod -R 777 /data /tmp /var/log/ainoval

EXPOSE 18080

CMD ["/usr/bin/supervisord", "-c", "/app/supervisord.conf"]
