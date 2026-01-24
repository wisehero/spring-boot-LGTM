FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add curl for health checks
RUN apk add --no-cache curl

# Copy the built JAR file (context is project root, not sample-app/)
COPY sample-app/build/libs/sample-app-*.jar app.jar

# Environment variables
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=docker

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
