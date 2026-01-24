FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 헬스 체크를 위한 curl 추가
RUN apk add --no-cache curl

# OpenTelemetry Java Agent 다운로드
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# 빌드된 JAR 파일 복사 (컨텍스트는 프로젝트 루트, sample-app/ 아님)
COPY sample-app/build/libs/sample-app-*.jar app.jar

# 환경 변수
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=docker

# 포트 노출
EXPOSE 8080

# 헬스 체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# OpenTelemetry Java Agent와 함께 실행
ENTRYPOINT ["sh", "-c", "java -javaagent:/app/opentelemetry-javaagent.jar $JAVA_OPTS -jar app.jar"]
