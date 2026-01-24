# Spring Boot LGTM Observability Project - Work Plan

## Overview

Spring Boot 모놀리식 서비스에서 프로덕션 레디 관측가능성(Observability) 인프라를 처음부터 구축하는 프로젝트입니다.

**Plan Created:** 2026-01-24
**Plan Revised:** 2026-01-24 (Critic Review Applied)
**Target Location:** `/Users/wisehero/Documents/GitHub/spring-boot-LGTM`

---

## 1. Requirements Summary

### Core Requirements
- **LGTM Stack Integration**: Loki (Logs) + Grafana (Visualization) + Tempo (Traces) + Prometheus (Metrics)
- **Build Tool**: Gradle Kotlin DSL (build.gradle.kts)
- **Deployment**: Docker Compose for local development
- **Architecture**: Multi-module structure with reusable observability module

### Functional Requirements
| Signal | Technology | Protocol |
|--------|------------|----------|
| Logs | Loki4j Logback Appender | HTTP Push to Loki |
| Metrics | Micrometer + Prometheus Registry | Prometheus Scrape |
| Traces | Micrometer Tracing + OpenTelemetry Bridge | OTLP to Tempo |
| Visualization | Grafana | - |

### Non-Functional Requirements
- 재사용 가능한 observability 모듈 (의존성 추가만으로 타 프로젝트에서 사용 가능)
- 환경별 설정 외부화 (dev, staging, prod)
- 로그-메트릭-트레이스 상관관계(correlation) 지원

---

## 2. Acceptance Criteria

### AC-1: Project Structure
- [ ] Gradle 멀티모듈 프로젝트가 성공적으로 빌드됨
- [ ] `./gradlew build` 명령이 에러 없이 완료됨

### AC-2: Observability Module
- [ ] `observability-core` 모듈이 독립적으로 빌드 가능
- [ ] 다른 모듈에서 의존성 추가만으로 관측가능성 기능 활성화

### AC-3: Sample Application
- [ ] `sample-app` 모듈이 정상 실행됨
- [ ] REST API 엔드포인트가 응답함

### AC-4: Logs (Loki)
- [ ] 애플리케이션 로그가 Loki로 전송됨
- [ ] Grafana에서 로그 조회 가능
- [ ] TraceId가 로그에 포함됨 (Micrometer Tracing MDC 자동 전파)

### AC-5: Metrics (Prometheus)
- [ ] `/actuator/prometheus` 엔드포인트가 메트릭 노출
- [ ] Prometheus가 메트릭을 수집함
- [ ] Grafana에서 메트릭 대시보드 조회 가능

### AC-6: Traces (Tempo)
- [ ] HTTP 요청에 대한 트레이스가 Tempo로 전송됨
- [ ] Grafana에서 트레이스 조회 가능
- [ ] 로그에서 TraceId 클릭 시 해당 트레이스로 이동 가능 (Loki derived fields)

### AC-7: Docker Compose
- [ ] `docker-compose up` 명령으로 전체 LGTM 스택 실행
- [ ] 모든 서비스 헬스체크 통과

### AC-8: Grafana Dashboards
- [ ] JVM 메트릭 대시보드 자동 프로비저닝
- [ ] 애플리케이션 메트릭 대시보드 자동 프로비저닝
- [ ] Loki 데이터소스 자동 설정 (derived fields 포함)
- [ ] Tempo 데이터소스 자동 설정 (tracesToLogsV2 포함)

---

## 3. Architecture Overview

### Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Spring Boot Application                        │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐              │
│  │   Logback    │    │  Micrometer  │    │  Micrometer  │              │
│  │  + Loki4j    │    │   Metrics    │    │   Tracing    │              │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘              │
│         │                   │                   │                       │
│         │ HTTP Push         │ Scrape            │ OTLP                  │
└─────────┼───────────────────┼───────────────────┼───────────────────────┘
          │                   │                   │
          ▼                   ▼                   ▼
    ┌───────────┐       ┌───────────┐       ┌───────────┐
    │   Loki    │       │Prometheus │       │   Tempo   │
    │  :3100    │       │  :9090    │       │  :4318    │
    └─────┬─────┘       └─────┬─────┘       └─────┬─────┘
          │                   │                   │
          │ derivedFields     │                   │ tracesToLogsV2
          │ (traceId→Tempo)   │                   │ (trace→Loki)
          └───────────────────┴───────────────────┘
                              │
                              ▼
                        ┌───────────┐
                        │  Grafana  │
                        │  :3000    │
                        └───────────┘
```

### Bidirectional Correlation
- **Loki → Tempo**: Click traceId in logs → Opens trace in Tempo
- **Tempo → Loki**: Click "Logs for this span" → Opens logs filtered by traceId

---

## 4. Project Structure

```
spring-boot-LGTM/
├── build.gradle.kts                    # Root build configuration
├── settings.gradle.kts                 # Multi-module settings
├── gradle.properties                   # Gradle properties
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml              # Version catalog
├── gradlew
├── gradlew.bat
│
├── buildSrc/                           # Shared build logic
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── spring-boot-conventions.gradle.kts
│
├── observability-core/                 # Reusable observability module
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/example/observability/
│       │   ├── config/
│       │   │   ├── ObservabilityAutoConfiguration.kt
│       │   │   ├── TracingConfiguration.kt      # Optional customizers ONLY
│       │   │   ├── MetricsConfiguration.kt
│       │   │   └── LoggingConfiguration.kt
│       │   ├── filter/
│       │   │   └── RequestLoggingFilter.kt      # Request/response logging (NOT MDC)
│       │   └── aspect/
│       │       └── ObservabilityAspect.kt
│       └── resources/
│           ├── META-INF/
│           │   └── spring/
│           │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│           └── logback-spring-observability.xml
│
├── sample-app/                         # Demo application
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── kotlin/com/example/sample/
│       │   │   ├── SampleApplication.kt
│       │   │   ├── controller/
│       │   │   │   └── SampleController.kt
│       │   │   └── service/
│       │   │       └── SampleService.kt
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-docker.yml
│       │       └── logback-spring.xml
│       └── test/
│           └── kotlin/com/example/sample/
│               └── controller/
│                   └── SampleControllerTest.kt   # Basic integration test
│
├── docker/                             # Docker infrastructure
│   ├── docker-compose.yml              # LGTM stack + sample-app
│   ├── docker-compose.infra.yml        # LGTM stack only
│   ├── prometheus/
│   │   └── prometheus.yml
│   ├── loki/
│   │   └── loki-config.yml
│   ├── tempo/
│   │   └── tempo-config.yml
│   └── grafana/
│       ├── provisioning/
│       │   ├── datasources/
│       │   │   └── datasources.yml
│       │   └── dashboards/
│       │       ├── dashboards.yml
│       │       ├── jvm-dashboard.json
│       │       └── application-dashboard.json
│       └── grafana.ini
│
├── docs/
│   └── README.md                       # Usage documentation
│
└── .gitignore
```

---

## 5. Dependencies

### Version Catalog (libs.versions.toml)

```toml
[versions]
spring-boot = "3.4.1"
kotlin = "1.9.22"
micrometer = "1.14.2"
micrometer-tracing = "1.4.1"
opentelemetry = "1.45.0"
opentelemetry-instrumentation = "2.12.0"
loki4j = "1.5.2"

[libraries]
# Spring Boot
spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter" }
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator" }
spring-boot-starter-aop = { module = "org.springframework.boot:spring-boot-starter-aop" }
spring-boot-configuration-processor = { module = "org.springframework.boot:spring-boot-configuration-processor" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }

# Micrometer Core
micrometer-core = { module = "io.micrometer:micrometer-core", version.ref = "micrometer" }
micrometer-registry-prometheus = { module = "io.micrometer:micrometer-registry-prometheus", version.ref = "micrometer" }

# Micrometer Tracing (auto-propagates traceId/spanId to MDC)
micrometer-tracing = { module = "io.micrometer:micrometer-tracing", version.ref = "micrometer-tracing" }
micrometer-tracing-bridge-otel = { module = "io.micrometer:micrometer-tracing-bridge-otel", version.ref = "micrometer-tracing" }

# OpenTelemetry (exporter only - SDK managed by Spring Boot auto-config)
opentelemetry-exporter-otlp = { module = "io.opentelemetry:opentelemetry-exporter-otlp", version.ref = "opentelemetry" }

# Logging
loki-logback-appender = { module = "com.github.loki4j:loki-logback-appender", version.ref = "loki4j" }

[bundles]
observability = [
    "spring-boot-starter-actuator",
    "spring-boot-starter-aop",
    "micrometer-core",
    "micrometer-registry-prometheus",
    "micrometer-tracing",
    "micrometer-tracing-bridge-otel",
    "opentelemetry-exporter-otlp",
    "loki-logback-appender"
]

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.7" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
```

**Note:** `opentelemetry-sdk` removed from bundle - Spring Boot 3.4 auto-config manages the SDK.

---

## 6. Implementation Tasks

### Phase 1: Project Initialization

#### Task 1.1: Create Root Project Structure
**Priority:** P0 (Must)
**Files:**
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `.gitignore`

**Acceptance:**
- `./gradlew tasks` 명령 실행 성공

#### Task 1.2: Initialize Gradle Wrapper
**Priority:** P0 (Must)
**Command:** `gradle wrapper --gradle-version 8.12`
**Acceptance:**
- `gradlew` 파일 생성됨
- `./gradlew --version` 출력에 Gradle 8.12 표시

---

### Phase 2: Observability Core Module

#### Task 2.1: Create observability-core Module
**Priority:** P0 (Must)
**Files:**
- `observability-core/build.gradle.kts`

**Content Outline:**
```kotlin
plugins {
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.bundles.observability)
    implementation(libs.spring.boot.starter)
}
```

**Acceptance:**
- `./gradlew :observability-core:build` succeeds
- Module can be imported as dependency

#### Task 2.2: Create Auto-Configuration
**Priority:** P0 (Must)
**Files:**
- `observability-core/src/main/kotlin/com/example/observability/config/ObservabilityAutoConfiguration.kt`
- `observability-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Acceptance:**
- Spring Boot가 자동 설정 클래스를 감지
- Auto-configuration appears in `--debug` startup logs

#### Task 2.3: Create Tracing Configuration (Customizers Only)
**Priority:** P0 (Must)
**Files:**
- `observability-core/src/main/kotlin/com/example/observability/config/TracingConfiguration.kt`

**IMPORTANT - Architect Guidance:**
- **DO NOT** define manual OpenTelemetry SDK beans
- **DO NOT** create OtlpGrpcSpanExporter or SdkTracerProvider beans
- **RELY ON** Spring Boot 3.4 auto-configuration via `management.otlp.tracing.endpoint`

**Content Outline:**
```kotlin
@Configuration
@ConditionalOnClass(Tracer::class)
class TracingConfiguration {

    /**
     * Optional: Add custom resource attributes beyond defaults.
     * Spring Boot auto-config handles the rest.
     */
    @Bean
    fun otelResourceCustomizer(
        @Value("\${spring.application.name:unknown}") appName: String
    ): OtelResourceCustomizer {
        return OtelResourceCustomizer { resource ->
            resource.toBuilder()
                .put("service.namespace", "spring-boot-lgtm")
                .put("deployment.environment", System.getenv("ENV") ?: "local")
                .build()
        }
    }
}
```

**Acceptance:**
- TracingConfiguration compiles without SDK bean definitions
- Application starts without bean conflicts
- Traces appear in Tempo using auto-configured endpoint

#### Task 2.4: Create Metrics Configuration
**Priority:** P0 (Must)
**Files:**
- `observability-core/src/main/kotlin/com/example/observability/config/MetricsConfiguration.kt`

**Content Outline:**
- Common tags 설정 (application, environment)
- JVM 메트릭 활성화
- Optional: Custom MeterBinder implementations

**Acceptance:**
- `/actuator/prometheus` returns metrics with common tags
- JVM metrics (jvm_memory_*, jvm_gc_*) are present

#### Task 2.5: Create Request Logging Filter (NOT MDC Propagation)
**Priority:** P1 (Should)
**Files:**
- `observability-core/src/main/kotlin/com/example/observability/filter/RequestLoggingFilter.kt`

**IMPORTANT - Architect Guidance:**
- **DO NOT** manually propagate traceId/spanId to MDC
- **Micrometer Tracing already does this automatically** via `micrometer-tracing-bridge-otel`
- This filter is for **request/response logging only**

**Content Outline:**
```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(RequestLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val startTime = System.currentTimeMillis()

        // traceId is ALREADY in MDC via Micrometer Tracing - just log it
        logger.info("Request: {} {} - traceId already in MDC",
            request.method, request.requestURI)

        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            logger.info("Response: {} {} - status={} duration={}ms",
                request.method, request.requestURI, response.status, duration)
        }
    }
}
```

**Acceptance:**
- Filter logs request/response with duration
- traceId appears in logs WITHOUT manual MDC.put() calls
- Verify MDC.get("traceId") returns value during request

#### Task 2.6: Create Logback Configuration Template
**Priority:** P0 (Must)
**Files:**
- `observability-core/src/main/resources/logback-spring-observability.xml`

**Content Outline:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<included>
    <!-- Micrometer Tracing auto-populates MDC with traceId and spanId -->
    <!-- Reference: https://micrometer.io/docs/tracing#_baggage -->

    <property name="TRACE_PATTERN" value="%X{traceId:-},%X{spanId:-}"/>
    <property name="LOG_PATTERN" value="%d{ISO8601} [%thread] %-5level %logger{36} - [${TRACE_PATTERN}] %msg%n"/>

    <!-- Loki4j Appender with trace correlation -->
    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <http>
            <url>${LOKI_URL:-http://localhost:3100}/loki/api/v1/push</url>
        </http>
        <format>
            <label>
                <pattern>app=${APP_NAME:-unknown},env=${ENV:-local},level=%level</pattern>
            </label>
            <message>
                <!-- Include traceId in structured format for Grafana derived fields -->
                <pattern>traceId=%X{traceId:-none} spanId=%X{spanId:-none} | %d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </message>
        </format>
    </appender>
</included>
```

**Acceptance:**
- Logback configuration includes both LOKI and CONSOLE appenders
- Log pattern includes `%X{traceId:-}` and `%X{spanId:-}`
- traceId format matches regex: `[a-f0-9]{32}` (OpenTelemetry trace ID)

---

### Phase 3: Sample Application Module

#### Task 3.1: Create sample-app Module
**Priority:** P0 (Must)
**Files:**
- `sample-app/build.gradle.kts`

**Content Outline:**
```kotlin
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(project(":observability-core"))
    implementation(libs.spring.boot.starter.web)

    testImplementation(libs.spring.boot.starter.test)
}
```

#### Task 3.2: Create Main Application Class
**Priority:** P0 (Must)
**Files:**
- `sample-app/src/main/kotlin/com/example/sample/SampleApplication.kt`

#### Task 3.3: Create Sample Controller
**Priority:** P0 (Must)
**Files:**
- `sample-app/src/main/kotlin/com/example/sample/controller/SampleController.kt`

**Endpoints:**
- `GET /api/hello` - Simple hello response
- `GET /api/slow` - Simulated slow response (for trace visibility)
- `GET /api/error` - Simulated error (for error trace visibility)
- `GET /api/chain` - Calls internal service (for span hierarchy)

#### Task 3.4: Create Sample Service
**Priority:** P0 (Must)
**Files:**
- `sample-app/src/main/kotlin/com/example/sample/service/SampleService.kt`

**Content:**
- `@Observed` 어노테이션을 사용한 메서드 관측

#### Task 3.5: Create Application Configuration
**Priority:** P0 (Must)
**Files:**
- `sample-app/src/main/resources/application.yml`
- `sample-app/src/main/resources/application-docker.yml`

**Configuration Keys (Architect Recommended):**
```yaml
spring:
  application:
    name: sample-app

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    tags:
      application: ${spring.application.name}
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      # Spring Boot 3.4 auto-config - NO manual SDK beans needed
      endpoint: http://localhost:4318/v1/traces

logging:
  pattern:
    # Micrometer Tracing auto-populates traceId/spanId in MDC
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

**Acceptance:**
- Application starts without tracing-related bean conflicts
- `management.otlp.tracing.endpoint` is the ONLY tracing endpoint config
- Traces appear in Tempo without manual OTel SDK configuration

#### Task 3.6: Create Logback Configuration
**Priority:** P0 (Must)
**Files:**
- `sample-app/src/main/resources/logback-spring.xml`

**Content:**
- Include observability-core의 logback-spring-observability.xml
- Console appender 추가

#### Task 3.7: Create Dockerfile
**Priority:** P1 (Should)
**Files:**
- `sample-app/Dockerfile`

**Health Check Specification:**
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
```

#### Task 3.8: Create Sample Controller Test
**Priority:** P1 (Should)
**Files:**
- `sample-app/src/test/kotlin/com/example/sample/controller/SampleControllerTest.kt`

**Content Outline:**
```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SampleControllerTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `GET api hello returns 200`() {
        val response = restTemplate.getForEntity("/api/hello", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `GET api chain returns 200`() {
        val response = restTemplate.getForEntity("/api/chain", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `actuator prometheus endpoint is accessible`() {
        val response = restTemplate.getForEntity("/actuator/prometheus", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("jvm_memory")
    }
}
```

**Acceptance:**
- `./gradlew :sample-app:test` passes
- At least 3 test cases for basic endpoint verification

---

### Phase 4: Docker Infrastructure

#### Task 4.1: Create Main Docker Compose File
**Priority:** P0 (Must)
**Files:**
- `docker/docker-compose.yml`

**Services:**
| Service | Image | Ports | Health Check |
|---------|-------|-------|--------------|
| grafana | grafana/grafana:11.4.0 | 3000 | HTTP GET /api/health |
| prometheus | prom/prometheus:v2.55.1 | 9090 | HTTP GET /-/healthy |
| loki | grafana/loki:3.3.2 | 3100 | HTTP GET /ready |
| tempo | grafana/tempo:2.6.1 | 3200, 4317, 4318 | HTTP GET /ready |
| sample-app | (build from source) | 8080 | HTTP GET /actuator/health |

**Health Check Configuration Example:**
```yaml
services:
  loki:
    image: grafana/loki:3.3.2
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:3100/ready || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 20s
```

**Acceptance:**
- `docker-compose up -d` starts all services
- `docker-compose ps` shows all services healthy

#### Task 4.2: Create Infrastructure-Only Docker Compose
**Priority:** P1 (Should)
**Files:**
- `docker/docker-compose.infra.yml`

**Purpose:** LGTM 스택만 실행 (로컬에서 IDE로 앱 개발 시)

#### Task 4.3: Create Prometheus Configuration
**Priority:** P0 (Must)
**Files:**
- `docker/prometheus/prometheus.yml`

**Content Outline:**
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'spring-boot-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['sample-app:8080']
```

**Acceptance:**
- Prometheus successfully scrapes sample-app
- `http://localhost:9090/targets` shows sample-app UP

#### Task 4.4: Create Loki Configuration
**Priority:** P0 (Must)
**Files:**
- `docker/loki/loki-config.yml`

**Content Outline:**
- Auth disabled
- Ingester configuration
- Storage configuration (filesystem for local dev)
- Schema config with TSDB

**Acceptance:**
- Loki starts without errors
- `http://localhost:3100/ready` returns ready
- Labels visible at `/loki/api/v1/labels`

#### Task 4.5: Create Tempo Configuration
**Priority:** P0 (Must)
**Files:**
- `docker/tempo/tempo-config.yml`

**Content Outline:**
```yaml
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: "0.0.0.0:4317"
        http:
          endpoint: "0.0.0.0:4318"

storage:
  trace:
    backend: local
    local:
      path: /tmp/tempo/blocks

querier:
  frontend_worker:
    frontend_address: localhost:9095
```

**Acceptance:**
- Tempo starts without errors
- `http://localhost:3200/ready` returns ready
- OTLP receiver accepts traces on 4318

#### Task 4.6: Create Grafana Datasources Configuration (CRITICAL)
**Priority:** P0 (Must)
**Files:**
- `docker/grafana/provisioning/datasources/datasources.yml`

**CRITICAL - Complete Datasources Configuration:**
```yaml
apiVersion: 1

datasources:
  # Prometheus - default for metrics
  - name: Prometheus
    type: prometheus
    uid: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false

  # Loki - logs with derived fields for Tempo correlation
  - name: Loki
    type: loki
    uid: loki
    access: proxy
    url: http://loki:3100
    editable: false
    jsonData:
      derivedFields:
        - name: TraceID
          matcherRegex: 'traceId=([a-f0-9]+)'
          url: ''
          datasourceUid: tempo
          urlDisplayLabel: 'View Trace in Tempo'

  # Tempo - traces with logs correlation
  - name: Tempo
    type: tempo
    uid: tempo
    access: proxy
    url: http://tempo:3200
    editable: false
    jsonData:
      tracesToLogsV2:
        datasourceUid: loki
        spanStartTimeShift: '-1h'
        spanEndTimeShift: '1h'
        filterByTraceID: true
        filterBySpanID: false
        customQuery: true
        query: '{app="sample-app"} | json | traceId="${__span.traceId}"'
      nodeGraph:
        enabled: true
      serviceMap:
        datasourceUid: prometheus
      search:
        hide: false
      lokiSearch:
        datasourceUid: loki
```

**Acceptance:**
- All 3 datasources appear in Grafana
- Clicking traceId in Loki logs opens Tempo trace
- Clicking "Logs for this span" in Tempo opens Loki logs
- Service map shows sample-app

#### Task 4.7: Create Grafana Dashboard Provisioning
**Priority:** P0 (Must)
**Files:**
- `docker/grafana/provisioning/dashboards/dashboards.yml`
- `docker/grafana/provisioning/dashboards/jvm-dashboard.json`
- `docker/grafana/provisioning/dashboards/application-dashboard.json`

**Dashboards:**
- JVM Metrics (Heap, GC, Threads)
- Application Metrics (HTTP requests, latency, error rate)

---

### Phase 5: Documentation & Polish

#### Task 5.1: Create README
**Priority:** P1 (Should)
**Files:**
- `README.md`

**Sections:**
- Quick Start
- Architecture Overview
- Module Description
- Configuration Reference
- Troubleshooting

#### Task 5.2: Create Usage Documentation
**Priority:** P2 (Nice to have)
**Files:**
- `docs/USAGE.md`

---

## 7. Task Dependencies

```
Phase 1: Project Initialization
├── Task 1.1: Create Root Project Structure
└── Task 1.2: Initialize Gradle Wrapper
    │
    ▼
Phase 2: Observability Core Module
├── Task 2.1: Create observability-core Module
├── Task 2.2: Create Auto-Configuration
├── Task 2.3: Create Tracing Configuration (customizers only)
├── Task 2.4: Create Metrics Configuration
├── Task 2.5: Create Request Logging Filter (NOT MDC propagation)
└── Task 2.6: Create Logback Configuration Template
    │
    ▼
Phase 3: Sample Application Module (depends on Phase 2)
├── Task 3.1: Create sample-app Module
├── Task 3.2: Create Main Application Class
├── Task 3.3: Create Sample Controller
├── Task 3.4: Create Sample Service
├── Task 3.5: Create Application Configuration
├── Task 3.6: Create Logback Configuration
├── Task 3.7: Create Dockerfile
└── Task 3.8: Create Sample Controller Test
    │
    ▼
Phase 4: Docker Infrastructure (can run parallel with Phase 3)
├── Task 4.1: Create Main Docker Compose File (with health checks)
├── Task 4.2: Create Infrastructure-Only Docker Compose
├── Task 4.3: Create Prometheus Configuration
├── Task 4.4: Create Loki Configuration
├── Task 4.5: Create Tempo Configuration
├── Task 4.6: Create Grafana Datasources Configuration (with derived fields)
└── Task 4.7: Create Grafana Dashboard Provisioning
    │
    ▼
Phase 5: Documentation & Polish
├── Task 5.1: Create README
└── Task 5.2: Create Usage Documentation
```

---

## 8. Commit Strategy

| Commit # | Scope | Message |
|----------|-------|---------|
| 1 | Phase 1 | `chore: initialize gradle multi-module project structure` |
| 2 | Phase 2 | `feat(observability-core): add reusable observability module with LGTM support` |
| 3 | Phase 3 | `feat(sample-app): add demo application with observability integration and tests` |
| 4 | Phase 4 | `infra: add docker-compose with LGTM stack and bidirectional correlation` |
| 5 | Phase 5 | `docs: add README and usage documentation` |

---

## 9. Verification Steps

### V1: Build Verification
```bash
./gradlew clean build
```
**Expected:** BUILD SUCCESSFUL

### V2: Unit Test Verification
```bash
./gradlew test
```
**Expected:** All tests pass (at least SampleControllerTest with 3 test cases)

### V3: Docker Infrastructure Verification
```bash
cd docker
docker-compose up -d
docker-compose ps
```
**Expected:** All services healthy (verify health check status column)

### V4: Application Start Verification
```bash
./gradlew :sample-app:bootRun
curl http://localhost:8080/actuator/health
```
**Expected:** `{"status":"UP"}`

### V5: Prometheus Metrics Verification
```bash
curl http://localhost:8080/actuator/prometheus | head -20
```
**Expected:** JVM and application metrics exposed

### V6: Loki Logs Verification
```bash
curl -G 'http://localhost:3100/loki/api/v1/labels'
```
**Expected:** Labels including `app=sample-app`

### V7: Tempo Traces Verification
```bash
curl http://localhost:3200/api/status/buildinfo
```
**Expected:** Tempo build info returned

### V8: Grafana Dashboard Verification
1. Open http://localhost:3000
2. Login (admin/admin)
3. Navigate to Dashboards
4. Verify JVM dashboard shows data
5. Navigate to Explore > Loki
6. Query: `{app="sample-app"}`
7. Verify logs with traceId are visible

### V9: End-to-End Trace Correlation (Bidirectional)
```bash
curl http://localhost:8080/api/chain
```
Then in Grafana:
1. **Loki → Tempo:** Go to Explore > Loki, find log entry, click traceId link, verify it opens Tempo
2. **Tempo → Loki:** In Tempo trace view, click "Logs for this span", verify it opens Loki with filtered logs
3. Verify span hierarchy is visible in Tempo

---

## 10. Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Dependency version conflicts | High | Medium | Use Spring Boot BOM, test incrementally |
| Loki connection issues | Medium | Low | Add connection retry logic, graceful degradation |
| Trace sampling overhead | Medium | Low | Configure 100% for dev, lower for prod |
| Docker memory issues | Low | Medium | Set memory limits in docker-compose |
| Grafana dashboard import errors | Low | Low | Test dashboards manually first |
| OTel SDK bean conflicts | High | Low | Use Spring Boot auto-config ONLY (no manual beans) |

---

## 11. Must Have / Must NOT Have

### Must Have
- Gradle Kotlin DSL (build.gradle.kts)
- Spring Boot 3.4.x
- Kotlin for all application code
- LGTM stack integration
- Docker Compose for local development
- Reusable observability module
- TraceId correlation across logs/traces (automatic via Micrometer)
- Bidirectional Loki-Tempo correlation (derived fields + tracesToLogsV2)
- At least one test file for SampleController

### Must NOT Have
- Production deployment configurations (Kubernetes, etc.)
- Database integration (keep sample app simple)
- Authentication/Authorization
- External API calls (keep isolated)
- Cloud-specific configurations
- Manual OpenTelemetry SDK bean definitions (use Spring Boot auto-config)
- Manual MDC propagation (Micrometer handles this automatically)

---

## 12. Success Criteria

**This project is COMPLETE when:**

1. `./gradlew clean build` succeeds with zero errors
2. `./gradlew test` passes (SampleControllerTest minimum)
3. `docker-compose up` starts all LGTM services with healthy status
4. Sample application starts and responds to HTTP requests
5. Prometheus successfully scrapes application metrics
6. Loki receives and stores application logs with traceId
7. Tempo receives and stores distributed traces
8. Grafana displays metrics in provisioned dashboards
9. **Bidirectional correlation works:**
   - Clicking traceId in Loki opens corresponding trace in Tempo
   - Clicking "Logs for this span" in Tempo opens filtered logs in Loki
10. README provides clear instructions for getting started

---

## References

- [Spring Boot Observability Documentation](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [Loki4j Logback Appender](https://loki4j.github.io/loki-logback-appender/)
- [Micrometer Tracing Documentation](https://micrometer.io/docs/tracing)
- [Grafana Loki Derived Fields](https://grafana.com/docs/grafana/latest/datasources/loki/#derived-fields)
- [Grafana Tempo TraceToLogs](https://grafana.com/docs/grafana/latest/datasources/tempo/#trace-to-logs)
- [Spring Boot OTLP Tracing Auto-Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.micrometer-tracing.otlp)
