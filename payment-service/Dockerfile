FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

COPY payment-service/build/libs/payment-service-*.jar app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java -javaagent:/app/opentelemetry-javaagent.jar $JAVA_OPTS -jar app.jar"]
