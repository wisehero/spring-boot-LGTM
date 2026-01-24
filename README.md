# Spring Boot LGTM Observability

A production-ready Spring Boot multi-module project demonstrating complete observability with the LGTM stack: **L**oki (Logs), **G**rafana (Visualization), **T**empo (Distributed Tracing), and Prometheus-compatible **M**etrics.

This project provides a reusable observability module that can be added to any Spring Boot application with a single dependency.

## Features

- **Reusable Observability Module**: Add comprehensive observability to any Spring Boot app with a single dependency import
- **LGTM Stack Integration**: Complete monitoring pipeline with logs, metrics, and traces
  - **Loki**: Log aggregation and querying
  - **Grafana**: Unified visualization dashboard
  - **Tempo**: Distributed tracing backend
  - **Prometheus**: Metrics collection and storage
- **Automatic Trace Correlation**: TraceId automatically appears in logs via Micrometer Tracing MDC integration
- **Bidirectional Correlation**: Click traceId in logs to view traces in Tempo, and vice versa
- **Pre-configured Dashboards**: JVM and Application metrics dashboards with auto-provisioning
- **Production-Ready Configuration**: Health checks, sampling controls, and resource management
- **Multi-module Architecture**: Observability logic cleanly separated in reusable module

## Quick Start

### Prerequisites

- **Java 21+** (OpenJDK, Eclipse Adoptium, or similar)
- **Docker & Docker Compose** (for LGTM infrastructure)
- **Gradle 8.12+** (wrapper included)
- **curl** (for testing endpoints)

### Option 1: Run Everything with Docker Compose

Start the complete stack (LGTM infrastructure + sample application) with a single command:

```bash
# Clone repository (if not already done)
cd /Users/wisehero/Documents/GitHub/spring-boot-LGTM

# Build the application
./gradlew build

# Start LGTM stack + sample app
cd docker
docker-compose up -d

# Verify all services are healthy
docker-compose ps
```

All services will be healthy within 1-2 minutes. Access:
- **Application**: http://localhost:8080
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Tempo**: http://localhost:3200
- **Loki**: http://localhost:3100

### Option 2: Local Development (App in IDE, Infrastructure in Docker)

For development with hot-reload:

```bash
# Start only LGTM infrastructure (no sample app)
cd docker
docker-compose -f docker-compose.infra.yml up -d

# In another terminal, run application from IDE or command line
./gradlew :sample-app:bootRun

# Application runs on http://localhost:8080
```

## Architecture

### Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Spring Boot Application                        │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐              │
│  │   Logback    │    │  Micrometer  │    │  Micrometer  │              │
│  │  + Loki4j    │    │   Metrics    │    │   Tracing    │              │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘              │
│         │                   │                   │                       │
│         │ HTTP Push         │ Scrape            │ OTLP HTTP             │
└─────────┼───────────────────┼───────────────────┼───────────────────────┘
          │                   │                   │
          ▼                   ▼                   ▼
    ┌───────────┐       ┌───────────┐       ┌───────────┐
    │   Loki    │       │Prometheus │       │   Tempo   │
    │  :3100    │       │  :9090    │       │  :3200    │
    └─────┬─────┘       └─────┬─────┘       └─────┬─────┘
          │                   │                   │
          │ derivedFields     │                   │ tracesToLogsV2
          │ (click traceId→  │                   │ (click "Logs"→
          │  opens Tempo)    │                   │  opens Loki)
          └───────────────────┴───────────────────┘
                              │
                              ▼
                        ┌───────────┐
                        │  Grafana  │
                        │  :3000    │
                        └───────────┘
```

### Correlation in Action

1. **Logs → Traces**: View application logs in Loki, click the `traceId` link to jump to the corresponding trace in Tempo
2. **Traces → Logs**: View trace details in Tempo, click "Logs for this span" to see all logs from that request in Loki

This bidirectional correlation is automatically configured via Grafana datasource settings.

## Project Structure

```
spring-boot-LGTM/
├── observability-core/           # Reusable observability module
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/example/observability/
│       │   ├── config/
│       │   │   ├── ObservabilityAutoConfiguration.kt
│       │   │   ├── TracingConfiguration.kt
│       │   │   ├── MetricsConfiguration.kt
│       │   │   └── LoggingConfiguration.kt
│       │   ├── filter/
│       │   │   └── RequestLoggingFilter.kt
│       │   └── aspect/
│       │       └── ObservabilityAspect.kt
│       └── resources/
│           ├── META-INF/spring/
│           │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│           └── logback-spring-observability.xml
│
├── sample-app/                   # Demo application showcasing observability
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── kotlin/com/example/sample/
│       │   │   ├── SampleApplication.kt
│       │   │   ├── controller/SampleController.kt
│       │   │   └── service/SampleService.kt
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-docker.yml
│       │       └── logback-spring.xml
│       └── test/kotlin/com/example/sample/
│           └── controller/SampleControllerTest.kt
│
├── docker/                       # LGTM stack infrastructure
│   ├── docker-compose.yml        # Complete stack with sample app
│   ├── docker-compose.infra.yml  # Infrastructure only (for local dev)
│   ├── prometheus/
│   │   └── prometheus.yml
│   ├── loki/
│   │   └── loki-config.yml
│   ├── tempo/
│   │   └── tempo-config.yml
│   ├── grafana/
│   │   ├── provisioning/
│   │   │   ├── datasources/datasources.yml
│   │   │   └── dashboards/
│   │   │       ├── dashboards.yml
│   │   │       ├── jvm-dashboard.json
│   │   │       └── application-dashboard.json
│   │   └── grafana.ini
│   └── Dockerfile
│
├── docs/
│   └── USAGE.md                  # Detailed usage guide
│
├── build.gradle.kts              # Root build configuration
├── settings.gradle.kts           # Multi-module configuration
├── gradle/libs.versions.toml      # Version catalog
├── gradlew                        # Gradle wrapper
├── gradlew.bat
└── README.md
```

## API Endpoints

The sample application provides several endpoints for testing observability:

| Endpoint | Method | Description | Use Case |
|----------|--------|-------------|----------|
| `/api/hello` | GET | Simple synchronous response | Basic logging test |
| `/api/slow` | GET | Simulated 2-second delay | Trace duration visibility |
| `/api/error` | GET | Throws RuntimeException | Error tracing and logging |
| `/api/chain` | GET | Calls internal service | Span hierarchy visualization |
| `/actuator/health` | GET | Health check endpoint | Liveness probe |
| `/actuator/metrics` | GET | List available metrics | Metrics discovery |
| `/actuator/prometheus` | GET | Prometheus metrics export | Metrics scraping |

### Testing the Endpoints

```bash
# Simple request
curl http://localhost:8080/api/hello

# Request with tracing
curl http://localhost:8080/api/chain

# Error handling
curl http://localhost:8080/api/error

# Metrics export
curl http://localhost:8080/actuator/prometheus
```

## Using observability-core in Your Project

### Step 1: Add as a Dependency

In your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":observability-core"))
    // or, when published to Maven Central:
    // implementation("com.example:observability-core:0.0.1-SNAPSHOT")
}
```

### Step 2: Configure Observability

In your `application.yml`:

```yaml
spring:
  application:
    name: my-service

management:
  # Expose actuator endpoints
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics

  # Enable 100% trace sampling for development
  tracing:
    sampling:
      probability: 1.0

  # Point to Tempo OTLP endpoint
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces

# Configure logging pattern with traceId
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

### Step 3: (Optional) Add Custom Metrics

```kotlin
import io.micrometer.observation.annotation.Observed
import org.springframework.stereotype.Service

@Service
class MyService(private val meterRegistry: MeterRegistry) {

    // Automatic metric via @Observed annotation
    @Observed(name = "my.operation", contextualName = "process-order")
    fun processOrder(orderId: String): Order {
        // Automatically creates trace span named "process-order"
        // Logs include traceId automatically
    }

    // Manual metric registration
    fun init() {
        meterRegistry.counter(
            "my.custom.counter",
            "type", "order",
            "status", "created"
        ).increment()
    }
}
```

## Configuration Reference

### Environment Variables

Used by sample application and Docker Compose:

| Variable | Default | Description | Set In |
|----------|---------|-------------|---------|
| `LOKI_URL` | `http://localhost:3100` | Loki HTTP endpoint | `.env` or `-e` flag |
| `APP_NAME` | `sample-app` | Application name for log labels | Docker Compose |
| `ENV` | `local` | Environment tag (local/staging/prod) | Docker Compose |
| `MANAGEMENT_OTLP_TRACING_ENDPOINT` | `http://localhost:4318/v1/traces` | Tempo OTLP endpoint | Docker Compose |
| `SPRING_PROFILES_ACTIVE` | (none) | Spring profile (use `docker` for Docker Compose) | Docker Compose |

### Management Configuration Keys

Key Spring Boot observability settings:

```yaml
management:
  # Expose metrics and health endpoints
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics

  # Tracing configuration
  tracing:
    sampling:
      probability: 1.0      # 0.1 = 10%, 1.0 = 100% (for development only)

  # OTLP exporter configuration
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
      # timeout: 10s           # Optional
      # compression: gzip      # Optional

  # Metrics tags applied to all metrics
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${ENV:local}
```

## Grafana Dashboards

Two pre-configured dashboards are automatically provisioned:

### 1. JVM Metrics Dashboard

Monitors Java Virtual Machine health:
- Heap memory usage and GC activity
- Thread count and states
- CPU usage
- Class loading

Access: Dashboards > JVM Metrics

### 2. Application Metrics Dashboard

Monitors application behavior:
- HTTP request rate and latency
- Error rates by endpoint
- Service response times
- Custom application metrics

Access: Dashboards > Application Metrics

### Creating Custom Dashboards

1. Open Grafana (http://localhost:3000)
2. Go to Dashboards > Create Dashboard
3. Select data source (Prometheus, Loki, or Tempo)
4. Build your dashboard with queries

Example queries:

**Prometheus** (Metrics):
```
rate(http_server_requests_seconds_sum[5m])
```

**Loki** (Logs):
```
{app="sample-app"} | json | level="ERROR"
```

**Tempo** (Traces):
- Use the Trace Search interface
- Filter by service, duration, or status

## Troubleshooting

### Issue: Logs not appearing in Loki

**Symptoms**: Logs only appear in stdout, not in Grafana Loki explorer

**Solutions**:

1. Verify Loki is running and healthy:
   ```bash
   curl http://localhost:3100/ready
   ```
   Expected: 204 No Content response

2. Check Loki URL configuration:
   ```bash
   # Check environment variable
   echo $LOKI_URL
   # Should output: http://localhost:3100
   ```

3. Verify logback configuration is loaded:
   ```bash
   # Check application logs for Loki4j appender initialization
   ./gradlew :sample-app:bootRun 2>&1 | grep -i loki
   ```

4. Check Loki logs:
   ```bash
   docker logs loki
   ```

### Issue: Traces not appearing in Tempo

**Symptoms**: No spans visible in Grafana Tempo explorer

**Solutions**:

1. Verify Tempo is running and healthy:
   ```bash
   curl http://localhost:3200/ready
   ```
   Expected: 204 No Content response

2. Check OTLP endpoint configuration:
   ```bash
   grep -r "otlp.tracing.endpoint" .
   # Should show: http://localhost:4318/v1/traces (or http://tempo:4318 in Docker)
   ```

3. Verify sampling is enabled:
   ```yaml
   # In application.yml
   management:
     tracing:
       sampling:
         probability: 1.0  # Must be > 0
   ```

4. Make a request and check Tempo for traces:
   ```bash
   # Generate a trace
   curl http://localhost:8080/api/chain

   # Check Tempo received it
   curl http://localhost:3200/api/traces \
     -H "Accept: application/json" | jq .
   ```

### Issue: Metrics not in Prometheus

**Symptoms**: Empty dashboards, no metrics data

**Solutions**:

1. Verify application exposes metrics endpoint:
   ```bash
   curl http://localhost:8080/actuator/prometheus | head -30
   ```
   Should show metric lines like: `jvm_memory_used_bytes{...}`

2. Check Prometheus scrape targets:
   - Open http://localhost:9090/targets
   - Find `spring-boot-app` target
   - Status should be **UP** with recent scrape time

3. Verify Prometheus configuration:
   ```bash
   cat docker/prometheus/prometheus.yml
   # Check metrics_path: '/actuator/prometheus'
   # Check targets: ['sample-app:8080']
   ```

4. Check Docker network connectivity:
   ```bash
   docker exec prometheus wget -O- http://sample-app:8080/actuator/prometheus
   ```

### Issue: Grafana Datasource Connection Failed

**Symptoms**: Red warning badge on datasource configuration, "connection failed"

**Solutions**:

1. Verify all LGTM services are healthy:
   ```bash
   docker-compose ps
   # All Status should show "healthy"
   ```

2. Check datasource URLs:
   - Prometheus: `http://prometheus:9090` (internal Docker network)
   - Loki: `http://loki:3100`
   - Tempo: `http://tempo:3200`

3. Test connectivity from Grafana container:
   ```bash
   docker exec grafana curl -v http://prometheus:9090/-/healthy
   ```

4. Restart services:
   ```bash
   docker-compose restart grafana
   ```

## Tech Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Spring Boot | 3.4.1 | Application framework |
| Kotlin | 1.9.22 | Application language |
| Micrometer | 1.14.2 | Metrics and tracing abstraction |
| Micrometer Tracing | 1.4.1 | Distributed tracing bridge |
| OpenTelemetry | 1.45.0 | Tracing instrumentation |
| Loki4j | 1.5.2 | Log shipping to Loki |
| Grafana | 11.4.0 | Visualization platform |
| Prometheus | 2.55.1 | Metrics backend |
| Loki | 3.3.2 | Log aggregation |
| Tempo | 2.6.1 | Distributed tracing backend |

## Production Considerations

### Trace Sampling

For production, reduce trace sampling to avoid performance impact:

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 10% sampling
      # or use probabilistic sampling based on URL patterns
```

### Log Shipping

For production, configure log shipping with retry logic:

```yaml
# In application.yml with observability-core
logging:
  level:
    root: WARN  # Reduce log volume
    com.myapp: INFO
```

### Resource Limits

Set appropriate resource limits in Docker Compose:

```yaml
services:
  loki:
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 256M
```

### Metrics Retention

Configure Prometheus retention:

```bash
# In docker-compose.yml
prometheus:
  command:
    - '--storage.tsdb.retention.time=30d'
```

## Contributing

Contributions welcome! Please ensure:

1. Code follows Kotlin style conventions
2. New features include tests
3. Documentation is updated
4. Build passes: `./gradlew clean build`

## License

MIT

## Additional Resources

- [Spring Boot Observability Documentation](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [Micrometer Tracing Guide](https://micrometer.io/docs/tracing)
- [Loki4j Documentation](https://loki4j.github.io/loki-logback-appender/)
- [Grafana Documentation](https://grafana.com/docs/grafana/)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
