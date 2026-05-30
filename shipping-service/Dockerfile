FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ARG JAR_FILE=shipping-service/target/shipping-service-*.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY ${JAR_FILE} app.jar

USER appuser

ENV JAVA_OPTS="\
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:+UseContainerSupport \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider \
      http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

EXPOSE ${SERVER_PORT:-8080}

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
