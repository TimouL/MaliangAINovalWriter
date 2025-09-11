FROM eclipse-temurin:21-jre

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=prod \
    JVM_XMS=512m \
    JVM_XMX=512m

# Fix JDK 21 reflective access for BigDecimal in Spring Data Mongo
ENV JAVA_TOOL_OPTIONS="--add-opens=java.base/java.math=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED"

WORKDIR /app

# Copy prebuilt server jar and web assets (paths are relative to build context: deploy)
COPY dist/ainoval-server.jar /app/ainoval-server.jar
COPY dist/web/ /app/web/

EXPOSE 18080

# Serve the prebuilt web from filesystem via Spring static locations
CMD sh -c "java -Xms${JVM_XMS} -Xmx${JVM_XMX} -Dfile.encoding=UTF-8 -Dspring.web.resources.static-locations=file:/app/web/ -jar /app/ainoval-server.jar"


