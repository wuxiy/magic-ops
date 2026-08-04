FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="magicops"
LABEL description="MagicOps Console - 动态开发与运维平台"

RUN addgroup -S magicops && adduser -S magicops -G magicops

WORKDIR /app

COPY magicops-console-*.jar app.jar

RUN chown -R magicops:magicops /app
USER magicops

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=6 \
  CMD wget -qO- http://127.0.0.1:8080/ > /dev/null 2>&1 || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
