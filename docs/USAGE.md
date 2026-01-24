# Spring Boot LGTM Observability - Usage Guide

This guide covers how to use the `observability-core` module in your Spring Boot applications, configure it for different environments, create custom metrics and traces, and customize Grafana dashboards.

## Table of Contents

1. [Adding observability-core to Existing Projects](#adding-observability-core-to-existing-projects)
2. [Configuration Options](#configuration-options)
3. [Custom Metrics](#custom-metrics)
4. [Custom Traces](#custom-traces)
5. [Logging Integration](#logging-integration)
6. [Grafana Dashboard Customization](#grafana-dashboard-customization)
7. [Environment-Specific Configuration](#environment-specific-configuration)
8. [Production Considerations](#production-considerations)
9. [Debugging and Observability Validation](#debugging-and-observability-validation)

## Adding observability-core to Existing Projects

### Step 1: Add Dependency

In your project's `build.gradle.kts`, add the observability-core dependency:

```kotlin
dependencies {
    // Option A: Local module (during development)
    implementation(project(":observability-core"))

    // Option B: Published library (when released to Maven Central)
    // implementation("com.example:observability-core:0.0.1-SNAPSHOT")
}
```

### Step 2: Enable Spring Boot Auto-Configuration

The observability module uses Spring Boot's auto-configuration mechanism. No additional configuration is needed - just having the dependency on the classpath automatically enables:

- OTLP trace exporter configuration
- Micrometer metrics setup
- Logback Loki4j appender
- Request logging filter

Verify auto-configuration is loaded by running with debug output:

```bash
./gradlew bootRun --args='--debug' 2>&1 | grep -i observability
```

You should see output like:
```
ObservabilityAutoConfiguration matched (condition)
TracingConfiguration matched (condition)
MetricsConfiguration matched (condition)
LoggingConfiguration matched (condition)
```

### Step 3: Add Minimal Configuration

In your `application.yml`, add:

```yaml
spring:
  application:
    name: my-service

management:
  # Required: Enable observability endpoints
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics

  # Configure tracing
  tracing:
    sampling:
      probability: 1.0  # 100% for development

  # Point to Tempo
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces

# Include traceId in logs
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

### Step 4: Configure Logback

Create or update `logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Include observability-core logback configuration -->
    <include resource="logback-spring-observability.xml"/>

    <!-- Add console appender for local development -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="LOKI"/>  <!-- From observability-core -->
    </root>

    <logger name="com.mycompany" level="DEBUG"/>
</configuration>
```

### Step 5: Verify Integration

```bash
# Start your application
./gradlew bootRun

# In another terminal, check if metrics are exposed
curl http://localhost:8080/actuator/prometheus | head -20
```

Expected output shows JVM metrics:
```
# HELP jvm_memory_usage_bytes
# TYPE jvm_memory_usage_bytes gauge
jvm_memory_usage_bytes{area="heap",id="G1 Heap"} ...
```

## Configuration Options

### Tracing Configuration

#### Sampling Strategy

Control how many traces are sent to Tempo:

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 10% sampling
      # probability: 0.5  # 50% sampling
      # probability: 1.0  # 100% sampling (dev only)
```

**Recommendations by Environment**:
- **Development**: `1.0` (100%) - see all traces
- **Staging**: `0.1` (10%) - balance detail and cost
- **Production**: `0.01` (1%) - minimize performance impact and cost

#### OTLP Exporter Configuration

```yaml
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
      timeout: 10s           # Default: 10s
      compression: gzip      # Default: gzip (optional: none)
```

**Timeout Considerations**:
- Default `10s` is usually sufficient for local/internal networks
- Increase to `30s` for high-latency or congested networks
- Decrease to `5s` for latency-sensitive applications

### Metrics Configuration

#### Add Application Tags

Tags help identify metrics across environments:

```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${ENV:local}
      version: ${app.version}
      region: ${APP_REGION:us-east-1}
```

These tags appear in all metrics:
```
http_server_requests_seconds_count{application="my-service",environment="prod",...}
```

#### Disable Specific Metrics

Reduce overhead by excluding unnecessary metrics:

```yaml
management:
  metrics:
    enable:
      jvm: true         # Keep JVM metrics
      process: true     # Keep process metrics
      logback: false    # Disable logging metrics (if not needed)
      tomcat: true      # Keep Tomcat metrics
```

### Logging Configuration

#### Log Pattern with TraceId

The logging pattern includes traceId automatically via Micrometer:

```yaml
logging:
  pattern:
    # Micrometer auto-populates %X{traceId} and %X{spanId} in MDC
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
    # Output: INFO [my-service,4bf92f3577b649a2b123456789abcdef,5af7183fb1d3cc01]
```

#### Log Levels

```yaml
logging:
  level:
    root: WARN              # Keep noise down
    com.example: DEBUG      # Your package in debug
    org.springframework: WARN
    io.micrometer: INFO     # Metrics framework
```

#### Loki4j Configuration (Advanced)

The observability module configures Loki4j automatically, but you can override settings via environment variables:

```bash
# Environment variables (set in docker-compose or shell)
LOKI_URL=http://loki:3100
APP_NAME=my-service
ENV=production
```

These are injected into the Loki4j appender configuration:

```xml
<!-- In logback-spring-observability.xml -->
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
        <url>${LOKI_URL:-http://localhost:3100}/loki/api/v1/push</url>
    </http>
    <format>
        <label>
            <pattern>app=${APP_NAME:-unknown},env=${ENV:-local},level=%level</pattern>
        </label>
    </format>
</appender>
```

## Custom Metrics

### 1. Counter Metrics

Count occurrences of events:

```kotlin
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service

@Service
class OrderService(private val meterRegistry: MeterRegistry) {

    fun placeOrder(order: Order) {
        // ... business logic ...

        // Increment counter
        meterRegistry.counter(
            "orders.placed",
            "status", order.status,
            "region", order.region
        ).increment()
    }

    fun cancelOrder(orderId: String) {
        meterRegistry.counter(
            "orders.cancelled",
            "reason", "user_request"
        ).increment()
    }
}
```

**Grafana Query**:
```
rate(orders_placed_total[5m])
```

### 2. Gauge Metrics

Measure current value (e.g., queue depth):

```kotlin
@Service
class QueueService(private val meterRegistry: MeterRegistry) {

    private val queue = ConcurrentLinkedQueue<Task>()

    fun init() {
        // Register gauge that measures queue size
        meterRegistry.gauge(
            "queue.size",
            queue,
            { it.size }
        )
    }

    fun addTask(task: Task) {
        queue.add(task)
        // Gauge automatically updated
    }
}
```

**Grafana Query**:
```
queue_size
```

### 3. Timer Metrics

Measure execution time:

```kotlin
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service

@Service
class ReportService(private val meterRegistry: MeterRegistry) {

    fun generateReport(type: String): Report {
        return meterRegistry.timer(
            "report.generation.duration",
            "type", type
        ).recordCallable {
            // Business logic here
            Thread.sleep(1000)  // Simulated work
            Report(...)
        }
    }
}
```

**Grafana Query - P95 Latency**:
```
histogram_quantile(0.95, rate(report_generation_duration_seconds_bucket[5m]))
```

### 4. Distribution Summary

Measure distribution of values:

```kotlin
@Service
class PaymentService(private val meterRegistry: MeterRegistry) {

    fun processPayment(amount: BigDecimal) {
        meterRegistry.timer(
            "payment.amount",
            "currency", "USD"
        ).record(amount.toLong()) { value ->
            // Process payment
        }
    }
}
```

### Custom Metrics via Annotation

Spring provides `@Timed` for method-level metrics:

```kotlin
import io.micrometer.core.annotation.Timed
import org.springframework.stereotype.Service

@Service
class UserService {

    @Timed(value = "user.fetch", description = "Time to fetch user")
    fun fetchUser(userId: String): User {
        // Logic here
    }
}
```

## Custom Traces

### 1. @Observed Annotation

The easiest way to create spans - fully automatic trace propagation:

```kotlin
import io.micrometer.observation.annotation.Observed
import org.springframework.stereotype.Service

@Service
class OrderProcessingService {

    @Observed(
        name = "order.processing",
        contextualName = "process-order"  // Shows in Tempo
    )
    fun processOrder(order: Order): ProcessResult {
        // Automatically creates span
        // Logs within this method include traceId
        // Duration automatically recorded as metric

        val validation = validateOrder(order)
        val payment = capturePayment(order)
        return ProcessResult(validation, payment)
    }

    @Observed(contextualName = "validate-order")
    private fun validateOrder(order: Order): ValidationResult {
        // Child span automatically created
    }

    @Observed(contextualName = "capture-payment")
    private fun capturePayment(order: Order): PaymentResult {
        // Another child span
    }
}
```

**In Grafana Tempo**, you'll see:
```
process-order
├── validate-order
└── capture-payment
```

### 2. Manual Span Creation

For more control, use Tracer directly:

```kotlin
import io.micrometer.tracing.Tracer
import org.springframework.stereotype.Service

@Service
class DataProcessingService(private val tracer: Tracer) {

    fun processLargeDataset() {
        tracer.currentSpan()?.tag("dataset.size", "1000000")

        for (batch in batches) {
            val span = tracer.nextSpan().name("process-batch")
            try {
                span.start().use {
                    processBatch(batch)
                }
            } finally {
                span.end()
            }
        }
    }
}
```

### 3. Span Tags and Events

Add metadata to spans:

```kotlin
import io.micrometer.observation.Observation
import org.springframework.stereotype.Service

@Service
class TransactionService(private val tracer: Tracer) {

    fun executeTransaction(transId: String, amount: BigDecimal) {
        val observation = Observation.createNotStarted(
            "transaction.execution",
            Observation.Context()
        )

        observation.observe {
            val span = tracer.currentSpan()

            // Add tags
            span?.tag("transaction.id", transId)
            span?.tag("transaction.amount", amount.toString())
            span?.tag("transaction.currency", "USD")

            try {
                val result = processTransaction(transId, amount)
                span?.tag("transaction.status", "success")
            } catch (e: Exception) {
                span?.tag("transaction.status", "failed")
                span?.tag("transaction.error", e.message)
                throw e
            }
        }
    }
}
```

**In Tempo**, you can search by tags:
- `transaction.status=success`
- `transaction.amount=100.00`

### 4. Baggage (Cross-Boundary Context)

Propagate context across service boundaries:

```kotlin
import io.micrometer.tracing.Baggage
import org.springframework.stereotype.Service

@Service
class AuthService(private val tracer: Tracer) {

    fun authenticateRequest(token: String): User {
        val user = validateToken(token)

        // Set baggage - automatically propagated to all spans
        tracer.createBaggageInCurrentContext("user.id", user.id)
        tracer.createBaggageInCurrentContext("user.role", user.role)

        return user
    }
}

@Service
class DataService(private val tracer: Tracer) {

    fun fetchData(): Data {
        // Baggage automatically available
        val userId = Baggage.fromCurrentContext().get("user.id")
        val userRole = Baggage.fromCurrentContext().get("user.role")

        // These values appear in logs for this span automatically
        return fetchDataForUser(userId)
    }
}
```

## Logging Integration

### Automatic TraceId in Logs

Micrometer Tracing automatically adds traceId and spanId to Logback's MDC (Mapped Diagnostic Context):

```kotlin
@RestController
class ExampleController {

    private val log = LoggerFactory.getLogger(ExampleController::class.java)

    @GetMapping("/example")
    fun example() {
        // traceId is AUTOMATICALLY in MDC, no manual work needed
        log.info("Processing request")  // traceId included automatically

        // Access MDC directly (optional)
        val traceId = MDC.get("traceId")
        val spanId = MDC.get("spanId")
    }
}
```

### Structured Logging with JSON

For better log parsing, use JSON format:

```xml
<!-- In logback-spring.xml -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

```xml
<appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"environment":"${ENV:-local}","application":"${APP_NAME:-unknown}"}</customFields>
    </encoder>
</appender>
```

### Filtering Logs in Loki

Query logs by traceId:

```promql
# Find all logs for a specific trace
{app="my-service"} | json | traceId="4bf92f3577b649a2b123456789abcdef"

# Find error logs with duration > 1s
{level="ERROR"} | json | duration_ms > 1000

# Find logs by service chain
{app=~"my-service|payment-service"} | json | traceId=~".*"
```

## Grafana Dashboard Customization

### Creating a Custom Dashboard

1. **Open Grafana**: http://localhost:3000
2. **Create Dashboard**: Dashboards → New Dashboard
3. **Add Panel**: Add new panel → Select data source (Prometheus, Loki, or Tempo)

### Example 1: Custom Metrics Panel

Create a panel showing custom application metrics:

```
Panel Title: Order Processing Latency (P95)

Data Source: Prometheus
Query: histogram_quantile(0.95, rate(order_processing_duration_seconds_bucket{job="spring-boot-app"}[5m]))

Type: Time series
Unit: Seconds
```

### Example 2: Log Pattern Panel

Display application logs with errors highlighted:

```
Panel Title: Application Error Logs

Data Source: Loki
Query: {app="my-service",level="ERROR"} | json | __error__=""

Type: Logs
```

### Example 3: Trace Analysis Panel

Show trace distribution:

```
Panel Title: Trace Duration Distribution

Data Source: Tempo
Search Criteria:
  - Service: my-service
  - Duration: 100ms to 5000ms
  - Status: OK

Type: Statistics
```

### Example 4: Correlation Panel

Create a panel that jumps from metrics to traces:

```
Panel Title: Slow Transactions

Data Source: Prometheus
Query: order_processing_duration_seconds_bucket{job="spring-boot-app"}

Add override:
  - Field: spanID (custom field)
  - Provide links to Tempo traces
```

### Importing Pre-built Dashboards

Grafana has community dashboards you can import:

1. Dashboards → Browse → Import
2. Search "JVM" or "Micrometer"
3. Select dashboard
4. Choose data source
5. Import

### Dashboard JSON Examples

Save custom dashboards as JSON for version control:

```bash
# Export current dashboard
curl -H "Authorization: Bearer $GRAFANA_API_KEY" \
  http://localhost:3000/api/dashboards/uid/my-dashboard > my-dashboard.json

# Import dashboard
curl -X POST \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $GRAFANA_API_KEY" \
  -d @my-dashboard.json \
  http://localhost:3000/api/dashboards/db
```

## Environment-Specific Configuration

### Using Profiles

Create separate configuration files for each environment:

**application.yml** (default, local development):
```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% in dev
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

**application-staging.yml**:
```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 10% in staging
  otlp:
    tracing:
      endpoint: http://tempo.staging.svc.cluster.local:4318/v1/traces
```

**application-prod.yml**:
```yaml
management:
  tracing:
    sampling:
      probability: 0.01  # 1% in production
  otlp:
    tracing:
      endpoint: http://tempo.prod.svc.cluster.local:4318/v1/traces
```

**Activate Profile**:
```bash
# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=staging'

# Or set environment variable
export SPRING_PROFILES_ACTIVE=prod
./gradlew bootRun
```

### Docker Environment Variables

Override configuration via environment variables:

```bash
docker run \
  -e SPRING_APPLICATION_NAME=my-service \
  -e MANAGEMENT_OTLP_TRACING_ENDPOINT=http://tempo:4318/v1/traces \
  -e MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.1 \
  my-service:latest
```

### Using @ConditionalOnProperty

Create environment-specific beans:

```kotlin
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EnvironmentSpecificConfig {

    @Bean
    @ConditionalOnProperty(
        name = "observability.enhanced",
        havingValue = "true"
    )
    fun enhancedMetricsCollector(): EnhancedMetricsCollector {
        return EnhancedMetricsCollector()  // Only in staging/prod
    }

    @Bean
    @ConditionalOnProperty(
        name = "observability.sampling.rate",
        havingValue = "high"
    )
    fun highResolutionTracing(): TracingConfig {
        return TracingConfig(probability = 1.0)  // Development only
    }
}
```

## Production Considerations

### 1. Trace Sampling Strategy

**The Problem**: 100% tracing in production generates massive overhead and cost.

**Solution**: Implement adaptive sampling:

```yaml
management:
  tracing:
    sampling:
      probability: 0.01  # Start at 1%
```

**Advanced: Probabilistic Sampling by Error Status**:

Create a custom sampler (requires extending Spring Boot):

```kotlin
import io.micrometer.tracing.SampledTraceContext
import io.micrometer.tracing.TraceContext
import org.springframework.boot.actuate.autoconfigure.tracing.TracingProperties

@Configuration
class ProductionTracingConfig(
    private val tracingProperties: TracingProperties
) {

    @Bean
    fun customSampler(): Sampler = Sampler { samplingRequest ->
        // 100% sample errors, 1% sample success
        if (samplingRequest.spanName.contains("error")) {
            Decision.RECORD_ONLY  // 100% for errors
        } else {
            Decision.drop()  // 1% for normal (via parent config)
        }
    }
}
```

### 2. Log Volume Management

**Reduce logs in production**:

```yaml
logging:
  level:
    root: WARN              # Only warnings and errors
    com.example: INFO       # Application logs only
    org.springframework: WARN
    io.micrometer: WARN
```

**Batch logs for efficiency**:

```xml
<!-- In logback-spring.xml -->
<appender name="LOKI_BATCH" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
        <url>${LOKI_URL}/loki/api/v1/push</url>
        <batchMaxSize>500</batchMaxSize>  <!-- Batch 500 logs -->
        <batchTimeoutMs>5000</batchTimeoutMs>  <!-- Or 5 seconds -->
    </http>
</appender>
```

### 3. Resource Limits

**Set memory limits for LGTM stack**:

```yaml
# docker-compose.yml
services:
  prometheus:
    deploy:
      resources:
        limits:
          memory: 1G
        reservations:
          memory: 512M

  loki:
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 256M

  tempo:
    deploy:
      resources:
        limits:
          memory: 2G
        reservations:
          memory: 1G
```

### 4. Metrics Retention

**Configure Prometheus data retention**:

```yaml
# docker-compose.yml
prometheus:
  command:
    - '--storage.tsdb.retention.time=30d'  # Keep 30 days of metrics
    - '--storage.tsdb.retention.size=50GB'  # Or cap at 50GB
```

**Configure Loki log retention**:

```yaml
# loki-config.yml
table_manager:
  retention_deletes_enabled: true
  retention_period: 720h  # 30 days
```

### 5. High Availability Setup

**For production**, use multiple replicas:

```yaml
# docker-compose.yml
services:
  prometheus-1:
    # Primary Prometheus

  prometheus-2:
    # Secondary/replica for redundancy

  loki-1:
    # Primary Loki with distributed backend

  loki-2:
    # Secondary replica
```

### 6. Security Considerations

**Restrict Grafana access**:

```yaml
# grafana.ini in docker-compose
[security]
admin_user = admin
admin_password = ${GRAFANA_PASSWORD}  # Strong password!
allow_sign_up = false

[auth]
disable_login_form = false

[auth.anonymous]
enabled = false
```

**Secure OTLP endpoint** (if exposed):

```yaml
# Use authentication or VPN
management:
  otlp:
    tracing:
      endpoint: https://tempo.prod.internal:4318/v1/traces
      headers:
        Authorization: "Bearer ${OTLP_TOKEN}"
```

## Debugging and Observability Validation

### 1. Verify Auto-Configuration

Check which auto-configurations were loaded:

```bash
./gradlew bootRun --args='--debug' 2>&1 | grep -A 1 "Matched"
```

Look for:
```
ObservabilityAutoConfiguration matched (Autoconfig)
TracingConfiguration matched (condition)
MetricsConfiguration matched (condition)
```

### 2. Check Exposed Endpoints

Verify all observability endpoints are available:

```bash
# List all actuator endpoints
curl http://localhost:8080/actuator | jq '.links[] | .href'

# Expected endpoints
# /actuator/health
# /actuator/metrics
# /actuator/prometheus
# /actuator/tracing
```

### 3. Verify TraceId in Logs

Make a request and check if traceId appears:

```bash
# Generate a trace
curl http://localhost:8080/api/chain

# Check application logs (if running locally)
./gradlew bootRun 2>&1 | grep -i traceid
```

Expected output:
```
INFO [sample-app,4bf92f3577b649a2b123456789abcdef,5af7183fb1d3cc01] ...
```

### 4. Verify Traces in Tempo

```bash
# Generate a trace
curl http://localhost:8080/api/slow

# Query Tempo API
curl 'http://localhost:3200/api/traces' \
  -H 'Accept: application/json' | jq .

# Or search by trace ID
curl 'http://localhost:3200/api/traces/{traceId}' \
  -H 'Accept: application/json' | jq .
```

### 5. Verify Metrics Export

```bash
# Check Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep -E "^(http_|jvm_)"

# Expected metrics
# http_server_requests_seconds_bucket
# jvm_memory_used_bytes
# jvm_gc_pause_seconds
```

### 6. Check Integration Tests

Run the provided integration tests:

```bash
./gradlew test

# Specific test
./gradlew :sample-app:test --tests '*SampleControllerTest*'
```

Expected output:
```
SampleControllerTest
  ✓ GET api hello returns 200
  ✓ GET api chain returns 200
  ✓ actuator metrics endpoint is accessible
  ✓ actuator health endpoint returns UP
```

### 7. Enable Debug Logging for Observability Components

Increase logging for troubleshooting:

```yaml
logging:
  level:
    io.micrometer: DEBUG
    com.github.loki4j: DEBUG
    io.opentelemetry: DEBUG
```

### 8. Performance Profiling

Check observability overhead:

```bash
# Before observability-core
./gradlew :sample-app:bootRun &
# Test performance with: ab -c 10 -n 1000 http://localhost:8080/api/hello

# After observability-core
# Compare results - overhead should be < 5%
```

---

This comprehensive guide covers everything needed to integrate, configure, and operate the Spring Boot LGTM observability stack in both development and production environments.
