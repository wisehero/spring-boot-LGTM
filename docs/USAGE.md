# Spring Boot LGTM Observability - 사용 가이드

마지막 업데이트: 2026-01-25

이 가이드는 `observability-core` 모듈을 기존 Spring Boot 프로젝트에 적용하는 방법을 설명합니다. **OpenTelemetry Java Agent**와 **AOP 기반 자동 추적**을 중심으로 작성되었습니다.

## 목차

1. [개요 및 버전 정보](#개요-및-버전-정보)
2. [프로젝트에 적용하기](#프로젝트에-적용하기)
3. [Java Agent 환경변수 상세 설명](#java-agent-환경변수-상세-설명)
4. [AOP 기반 자동 추적 설정](#aop-기반-자동-추적-설정)
5. [JDBC 쿼리 추적](#jdbc-쿼리-추적)
6. [커스텀 메트릭](#커스텀-메트릭)
7. [커스텀 트레이스](#커스텀-트레이스)
8. [로그-트레이스 연동](#로그-트레이스-연동)
9. [Grafana 활용](#grafana-활용)
10. [환경별 설정](#환경별-설정)
11. [프로덕션 고려사항](#프로덕션-고려사항)
12. [트러블슈팅](#트러블슈팅)
13. [참고 자료](#참고-자료)

---

## 개요 및 버전 정보

이 프로젝트는 **OpenTelemetry Java Agent** 방식의 자동 계측을 사용합니다. 기존의 라이브러리 방식과 달리, 애플리케이션 코드 변경 없이 JVM 수준에서 자동으로 HTTP, JDBC, 메시지 큐 등을 추적합니다.

추가로 **Spring AOP**를 이용한 내부 메서드 자동 추적(TracingAspect)으로, Service/Repository 계층의 모든 public 메서드를 자동으로 span으로 생성합니다.

### 사용 중인 버전 (2026-01-25 기준)

| 컴포넌트 | 버전 |
|---------|-----|
| Spring Boot | 3.4.1 |
| Kotlin | 1.9.22 |
| OpenTelemetry Java Agent | 2.11.0 |
| OpenTelemetry Instrumentation Annotations | 2.12.0 |
| Micrometer | 1.14.2 |
| Micrometer Tracing | 1.4.1 |
| Loki4j | 1.5.2 |
| Grafana | 11.4.0 |
| Prometheus | 2.55.1 |
| Loki | 3.3.2 |
| Tempo | 2.6.1 |
| PostgreSQL | 16 |

---

## 프로젝트에 적용하기

### Step 1: 의존성 추가

프로젝트의 `build.gradle.kts`에 `observability-core` 의존성을 추가합니다.

```kotlin
dependencies {
    // 옵션 A: 로컬 모듈 (개발 중)
    implementation(project(":observability-core"))

    // 옵션 B: Maven Central 배포 라이브러리 (향후)
    // implementation("com.example:observability-core:0.0.1-SNAPSHOT")
}
```

또는 `libs.versions.toml`에서 정의된 번들을 사용할 수 있습니다:

```kotlin
dependencies {
    implementation(libs.bundles.observabilityAgent)
}
```

**주의**: `opentelemetry-exporter-otlp` 의존성은 포함하지 않습니다. Java Agent가 자체 exporter를 제공하므로 포함하면 중복 전송이 발생합니다.

### Step 2: Dockerfile에 Java Agent 추가

Docker 이미지에 OpenTelemetry Java Agent를 다운로드하고 실행 시 로드합니다.

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 헬스 체크용 curl 설치
RUN apk add --no-cache curl

# OpenTelemetry Java Agent 다운로드
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# 빌드된 JAR 파일 복사
COPY your-app/build/libs/your-app-*.jar app.jar

# Java 옵션 설정
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 포트 노출
EXPOSE 8080

# 헬스 체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Java Agent와 함께 실행
ENTRYPOINT ["sh", "-c", "java -javaagent:/app/opentelemetry-javaagent.jar $JAVA_OPTS -jar app.jar"]
```

**핵심 포인트**:
- `-javaagent:/app/opentelemetry-javaagent.jar` 플래그로 Java Agent 활성화
- JAR 파일 경로: `/app/opentelemetry-javaagent.jar`
- 환경변수를 통해 Agent 동작 제어

### Step 3: docker-compose.yml 환경변수 설정

`docker-compose.yml`에서 애플리케이션 서비스에 OpenTelemetry 환경변수를 설정합니다.

```yaml
services:
  your-app:
    build:
      context: ../
      dockerfile: your-app/Dockerfile
    container_name: your-app
    ports:
      - "8080:8080"
    environment:
      # 기본 Spring Boot 설정
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_APPLICATION_NAME=your-app

      # OpenTelemetry Java Agent 설정
      - OTEL_SERVICE_NAME=your-app
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318
      - OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
      - OTEL_TRACES_EXPORTER=otlp
      - OTEL_METRICS_EXPORTER=none          # Prometheus가 메트릭 담당
      - OTEL_LOGS_EXPORTER=none             # Loki가 로그 담당

      # 자동 계측 활성화
      - OTEL_INSTRUMENTATION_SPRING_WEBMVC_ENABLED=true
      - OTEL_INSTRUMENTATION_JDBC_ENABLED=true
      - OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE=true
      - OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED=true

      # Loki 설정
      - LOKI_URL=http://loki:3100
      - APP_NAME=your-app
      - ENV=docker

      # 데이터베이스 설정 (PostgreSQL 예시)
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/mydb
      - SPRING_DATASOURCE_USERNAME=app
      - SPRING_DATASOURCE_PASSWORD=app

    depends_on:
      tempo:
        condition: service_healthy
      loki:
        condition: service_healthy
      prometheus:
        condition: service_healthy
```

### Step 4: application.yml 설정

프로젝트의 `src/main/resources/application.yml`에 기본 설정을 추가합니다.

```yaml
spring:
  application:
    name: your-app
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

# AOP 기반 내부 메서드 자동 추적 활성화
observability:
  tracing:
    aop:
      enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics

  # 메트릭 태그 설정
  metrics:
    tags:
      application: ${spring.application.name}

  # 로깅 설정
logging:
  level:
    root: INFO
    com.example: DEBUG
    io.opentelemetry: INFO
  pattern:
    level: "%5p [${spring.application.name:},%X{trace_id:-},%X{span_id:-}]"
```

**docker 프로필용 설정** (`application-docker.yml`):

```yaml
spring:
  application:
    name: your-app
  jpa:
    hibernate:
      ddl-auto: create-drop  # Docker 환경에서는 자동 생성

# AOP 기반 추적 활성화
observability:
  tracing:
    aop:
      enabled: true

logging:
  level:
    root: INFO
    com.example: DEBUG
```

### Step 5: logback-spring.xml 설정

로깅 설정 파일을 생성하거나 업데이트합니다.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- observability-core의 로깅 설정 포함 -->
    <include resource="logback-spring-observability.xml"/>

    <!-- 환경 변수 설정 -->
    <springProperty scope="context" name="APP_NAME" source="spring.application.name" defaultValue="your-app"/>
    <springProperty scope="context" name="ENV" source="spring.profiles.active" defaultValue="local"/>
    <springProperty scope="context" name="LOKI_URL" source="loki.url" defaultValue="http://localhost:3100"/>

    <!-- 로컬 프로파일: 콘솔만 사용 -->
    <springProfile name="!docker">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <!-- Docker 프로파일: 콘솔 + Loki -->
    <springProfile name="docker">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
            <appender-ref ref="LOKI"/>
        </root>
    </springProfile>
</configuration>
```

**주의**: `logback-spring-observability.xml`은 `observability-core` 모듈에 포함되어 있으며, Java Agent가 MDC에 `trace_id`와 `span_id`를 **snake_case** 형식으로 자동 추가합니다.

---

## Java Agent 환경변수 상세 설명

OpenTelemetry Java Agent는 환경변수를 통해 동작을 제어합니다. 각 변수의 역할을 설명합니다.

### 필수 설정

#### OTEL_SERVICE_NAME
- **설명**: OpenTelemetry에서 인식하는 애플리케이션 이름
- **값**: `your-app`
- **영향**: Tempo에서 service 필터, Grafana 대시보드에 표시
- **예시**:
  ```bash
  OTEL_SERVICE_NAME=payment-service
  ```

#### OTEL_EXPORTER_OTLP_ENDPOINT
- **설명**: Trace 데이터를 보낼 OTLP 수신자 주소
- **값**: `http://tempo:4318` (Docker) 또는 `http://localhost:4318` (로컬)
- **형식**: `http://{host}:{port}` (TLS는 https 사용)
- **예시**:
  ```bash
  # 로컬 개발
  OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318

  # Docker Compose
  OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318

  # Kubernetes
  OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo.observability.svc.cluster.local:4318
  ```

#### OTEL_EXPORTER_OTLP_PROTOCOL
- **설명**: OTLP 프로토콜 선택
- **값**: `http/protobuf` (권장) 또는 `grpc`
- **추천**: `http/protobuf`는 HTTP/1.1 기반으로 더 안정적
- **예시**:
  ```bash
  OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
  ```

#### OTEL_TRACES_EXPORTER
- **설명**: Trace 데이터 내보내기 활성화
- **값**: `otlp`
- **예시**:
  ```bash
  OTEL_TRACES_EXPORTER=otlp
  ```

### 선택 설정

#### OTEL_METRICS_EXPORTER
- **설명**: Metric 데이터 내보내기 제어
- **값**: `none` (Prometheus가 대신 처리)
- **이유**: Micrometer를 통해 Prometheus가 메트릭 수집
- **예시**:
  ```bash
  OTEL_METRICS_EXPORTER=none
  ```

#### OTEL_LOGS_EXPORTER
- **설명**: Log 데이터 내보내기 제어
- **값**: `none` (Loki4j가 대신 처리)
- **이유**: Logback Appender (Loki4j)가 로그 전송
- **예시**:
  ```bash
  OTEL_LOGS_EXPORTER=none
  ```

#### OTEL_INSTRUMENTATION_SPRING_WEBMVC_ENABLED
- **설명**: Spring Web MVC 자동 계측 활성화
- **값**: `true` (기본값)
- **효과**: HTTP 요청/응답 자동 추적
- **예시**:
  ```bash
  OTEL_INSTRUMENTATION_SPRING_WEBMVC_ENABLED=true
  ```

#### OTEL_INSTRUMENTATION_JDBC_ENABLED
- **설명**: JDBC 쿼리 자동 계측 활성화
- **값**: `true` (기본값)
- **효과**: SQL 쿼리 span 자동 생성
- **예시**:
  ```bash
  OTEL_INSTRUMENTATION_JDBC_ENABLED=true
  ```

#### OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE
- **설명**: Logback MDC에 trace_id, span_id 자동 추가
- **값**: `true`
- **효과**: 모든 로그에 `trace_id`, `span_id` 포함
- **주의**: `true`로 설정하지 않으면 로그-트레이스 연동 불가
- **예시**:
  ```bash
  OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE=true
  ```

#### OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED
- **설명**: Java Agent 글로벌 자동 설정 활성화
- **값**: `true` (권장)
- **효과**: 환경변수 기반 자동 설정 적용
- **예시**:
  ```bash
  OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED=true
  ```

### 고급 설정

#### OTEL_EXPORTER_OTLP_TIMEOUT
- **설명**: OTLP 내보내기 타임아웃
- **값**: `10s` (기본값)
- **권장**:
  - 로컬/내부 네트워크: `10s`
  - 외부 네트워크: `30s`
  - 매우 빠른 응답 필요: `5s`
- **예시**:
  ```bash
  OTEL_EXPORTER_OTLP_TIMEOUT=30s
  ```

#### OTEL_EXPORTER_OTLP_HEADERS
- **설명**: OTLP 요청에 추가할 HTTP 헤더
- **값**: `key1=value1,key2=value2` 형식
- **사용 사례**: 인증 토큰, API 키
- **예시**:
  ```bash
  OTEL_EXPORTER_OTLP_HEADERS=Authorization=Bearer+token123,X-API-Key=secret
  ```

#### OTEL_TRACES_SAMPLER
- **설명**: 샘플링 전략
- **값**: `always_on`, `always_off`, `traceidratio`, `parentbased_*`
- **기본값**: `parentbased_always_on`
- **예시**:
  ```bash
  # 1% 샘플링
  OTEL_TRACES_SAMPLER=traceidratio
  OTEL_TRACES_SAMPLER_ARG=0.01
  ```

#### OTEL_RESOURCE_ATTRIBUTES
- **설명**: 리소스 속성 (모든 span에 추가)
- **값**: `key1=value1,key2=value2` 형식
- **예시**:
  ```bash
  OTEL_RESOURCE_ATTRIBUTES=deployment.environment=production,service.version=1.2.3
  ```

---

## AOP 기반 자동 추적 설정

Spring AOP를 이용한 자동 추적은 Service, Repository 등의 모든 public 메서드를 자동으로 span으로 변환합니다.

### TracingAspect 동작 원리

```
요청
 ↓
[Java Agent] HTTP 요청 자동 추적 (루트 span 생성)
 ↓
[TracingAspect] Service.getUser() 호출 감지
 ↓
span 자동 생성: "ServiceName.methodName"
 ↓
[TracingAspect] Service.processData() 호출 감지
 ↓
span 자동 생성: "ServiceName.methodName" (자식 span)
 ↓
[Java Agent] JDBC 쿼리 자동 추적
 ↓
[결과] Tempo에 전체 span 트리 전송
```

### 활성화 방법

`application.yml`에서 `observability.tracing.aop.enabled=true`로 설정합니다.

```yaml
observability:
  tracing:
    aop:
      enabled: true
```

**프로파일별 설정**:

```yaml
# application-dev.yml
observability:
  tracing:
    aop:
      enabled: true

# application-prod.yml
observability:
  tracing:
    aop:
      enabled: true  # 프로덕션에서도 활성화하되, 샘플링으로 부하 제어
```

### 추적 대상 커스터마이징

현재 `TracingAspect`는 다음을 자동 추적합니다:

- `com.example` 패키지 하위의 모든 public 메서드
- `@Service`, `@Repository`, `@Component` 등 모든 Spring Bean

**제외할 패키지**:
- `com.example.observability` (무한 루프 방지)

**커스터마이징 예시** (TracingAspect.kt 수정):

```kotlin
// 특정 패키지만 추적하려면
@Pointcut("within(com.example.service..*) || within(com.example.repository..*)")
fun applicationPackage() {}

// 특정 어노테이션만 추적하려면
@Pointcut("@within(org.springframework.stereotype.Service)")
fun serviceLayer() {}
```

### 비활성화 방법

특정 환경에서 AOP 추적을 끄려면:

```yaml
observability:
  tracing:
    aop:
      enabled: false
```

또는 환경변수:

```bash
OBSERVABILITY_TRACING_AOP_ENABLED=false
```

### 결과 확인 (Tempo에서)

AOP 추적이 활성화되면 Tempo에서 다음과 같은 span 구조를 볼 수 있습니다:

```
GET /api/users                             450ms (Agent: HTTP)
├─ UserService.getAllUsers                 120ms (AOP)
│  ├─ UserRepository.findAll                80ms (AOP)
│  │  └─ SELECT * FROM users               75ms (Agent: JDBC)
│  └─ UserService.enrichUsers              35ms (AOP)
└─ UserService.formatResponse               15ms (AOP)
```

---

## JDBC 쿼리 추적

OpenTelemetry Java Agent는 JDBC 쿼리를 자동으로 추적하고, 각 쿼리를 별도의 span으로 생성합니다.

### 활성화

docker-compose.yml에서 다음을 설정합니다:

```yaml
environment:
  - OTEL_INSTRUMENTATION_JDBC_ENABLED=true
```

### 자동 추적 대상

- **Driver**: JDBC 호환 모든 드라이버 (PostgreSQL, MySQL, Oracle 등)
- **작업**: SELECT, INSERT, UPDATE, DELETE, CREATE TABLE 등
- **정보**: 쿼리 문자열, 실행 시간, 성공/실패 상태

### 추적 결과

Tempo에서 쿼리 span은 다음과 같이 표시됩니다:

```
Database Query                              75ms
├─ db.system: postgresql
├─ db.name: mydb
├─ db.user: app
├─ db.statement: SELECT * FROM users WHERE id = ?
└─ duration: 75ms
```

### PostgreSQL 예시

```yaml
# docker-compose.yml
postgres:
  image: postgres:16-alpine
  environment:
    POSTGRES_USER: app
    POSTGRES_PASSWORD: app
    POSTGRES_DB: mydb

sample-app:
  environment:
    - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/mydb
    - SPRING_DATASOURCE_USERNAME=app
    - SPRING_DATASOURCE_PASSWORD=app
    - OTEL_INSTRUMENTATION_JDBC_ENABLED=true
```

### Grafana에서 SQL 쿼리 분석

Tempo에서 트레이스를 열면 각 JDBC span에서 쿼리를 확인할 수 있습니다:

```
Details 탭 → db.statement 필드 → 전체 SQL 확인
```

---

## 커스텀 메트릭

Micrometer를 통해 비즈니스 메트릭을 수집하고 Prometheus에 내보냅니다.

### 1. Counter (카운터)

이벤트 발생 횟수를 기록합니다.

```kotlin
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service

@Service
class OrderService(private val meterRegistry: MeterRegistry) {

    fun placeOrder(order: Order) {
        // ... 비즈니스 로직 ...

        // 카운터 증가
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

**Prometheus 쿼리**:
```promql
rate(orders_placed_total[5m])
```

**Grafana 대시보드**: 시계열 그래프로 시간대별 주문 수 표시

### 2. Gauge (게이지)

현재 값을 측정합니다 (예: 큐 크기, 활성 연결).

```kotlin
@Service
class QueueService(private val meterRegistry: MeterRegistry) {

    private val queue = ConcurrentLinkedQueue<Task>()

    @PostConstruct
    fun init() {
        // 큐 크기를 게이지로 등록
        meterRegistry.gauge(
            "queue.size",
            queue,
            { it.size }
        )
    }

    fun addTask(task: Task) {
        queue.add(task)
        // 게이지가 자동으로 업데이트됨
    }
}
```

**Prometheus 쿼리**:
```promql
queue_size
```

### 3. Timer (타이머)

작업 실행 시간을 측정합니다.

```kotlin
@Service
class ReportService(private val meterRegistry: MeterRegistry) {

    fun generateReport(type: String): Report {
        return meterRegistry.timer(
            "report.generation.duration",
            "type", type
        ).recordCallable {
            // 비즈니스 로직
            val result = computeReport(type)
            result
        }
    }
}
```

**Prometheus 쿼리 (P95 지연시간)**:
```promql
histogram_quantile(0.95, rate(report_generation_duration_seconds_bucket[5m]))
```

### 4. @Timed 어노테이션

메서드 수준에서 자동으로 메트릭을 수집합니다.

```kotlin
import io.micrometer.core.annotation.Timed
import org.springframework.stereotype.Service

@Service
class UserService {

    @Timed(value = "user.fetch", description = "사용자 조회 시간")
    fun fetchUser(userId: String): User {
        // 자동으로 메트릭 수집
        return userRepository.findById(userId)
    }
}
```

---

## 커스텀 트레이스

`observability-core`의 `TracingAspect`가 `com.example..*`의 모든 public 메서드 span을 자동 생성하므로
대부분은 추가 작업이 필요 없다. 더 세밀한 제어가 필요하면 아래 방법을 사용한다.

### 1. @WithSpan 어노테이션 (권장)

OpenTelemetry Java Agent가 지원하는 어노테이션으로, 메서드에 붙이면 자동으로 span이 생성됩니다.
`opentelemetry-instrumentation-annotations` 의존성이 필요하며 `observability-agent` 번들에 포함되어 있습니다.

```kotlin
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service

@Service
class OrderProcessingService {

    @WithSpan("process-order")
    fun processOrder(@SpanAttribute("order.id") orderId: Long): ProcessResult {
        // 자동으로 span 생성, 실행 시간 기록, 로그에 trace_id 포함
        val validation = validateOrder(orderId)
        val payment = capturePayment(orderId)
        return ProcessResult(validation, payment)
    }

    @WithSpan("validate-order")
    private fun validateOrder(orderId: Long): ValidationResult {
        // 자식 span 자동 생성
        return ValidationResult(...)
    }

    @WithSpan("capture-payment")
    private fun capturePayment(orderId: Long): PaymentResult {
        // 또 다른 자식 span
        return PaymentResult(...)
    }
}
```

**Tempo에서 결과**:
```
process-order (300ms)
├─ validate-order (50ms)
└─ capture-payment (200ms)
```

### 2. 수동 Span 생성

더 세밀한 제어가 필요하면 OTel API의 Tracer를 직접 사용합니다.
Agent가 `GlobalOpenTelemetry`에 SDK를 등록하므로 거기서 Tracer를 가져옵니다.

```kotlin
import io.opentelemetry.api.GlobalOpenTelemetry
import org.springframework.stereotype.Service

@Service
class DataProcessingService {

    private val tracer = GlobalOpenTelemetry.getTracer("data-processing")

    fun processLargeDataset(batches: List<Batch>) {
        for ((batchIndex, batch) in batches.withIndex()) {
            val span = tracer.spanBuilder("process-batch-$batchIndex")
                .setAttribute("dataset.size", 1_000_000L)
                .startSpan()
            try {
                span.makeCurrent().use {
                    processBatch(batch)
                }
            } finally {
                span.end()
            }
        }
    }
}
```

### 3. Span 속성과 이벤트

현재 span에 메타데이터(속성)와 이벤트를 추가합니다.

```kotlin
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.springframework.stereotype.Service

@Service
class TransactionService {

    @WithSpan("transaction.execution")
    fun executeTransaction(transId: String, amount: BigDecimal) {
        val span = Span.current()

        // 속성 추가 (검색 가능)
        span.setAttribute("transaction.id", transId)
        span.setAttribute("transaction.amount", amount.toString())
        span.setAttribute("transaction.currency", "USD")

        try {
            val result = processTransaction(transId, amount)
            span.setAttribute("transaction.status", "success")
            span.addEvent("transaction.completed")
        } catch (e: Exception) {
            span.setAttribute("transaction.status", "failed")
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, e.message ?: "error")
            throw e
        }
    }
}
```

**Tempo에서 검색** (TraceQL):
- `{ .transaction.status = "success" }`
- `{ .transaction.amount = "100.00" }`

---

## 로그-트레이스 연동

OpenTelemetry Java Agent는 자동으로 `trace_id`와 `span_id`를 Logback의 MDC에 추가하므로, 모든 로그에 traceId가 포함됩니다.

### 자동 추가 (Agent가 처리)

docker-compose.yml에서 다음을 설정하면:

```yaml
environment:
  - OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE=true
```

그러면 모든 로그에 **자동으로** `trace_id`와 `span_id`가 추가됩니다.

### 로그 패턴 설정

`logback-spring-observability.xml`에서:

```xml
<!-- Java Agent는 snake_case 사용: trace_id, span_id -->
<property name="TRACE_PATTERN" value="%X{trace_id:-},%X{span_id:-}"/>
<property name="LOG_PATTERN" value="%d{ISO8601} [%thread] %-5level %logger{36} - [${TRACE_PATTERN}] %msg%n"/>

<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <pattern>${LOG_PATTERN}</pattern>
    </encoder>
</appender>
```

### 로그 출력 예시

```
2024-01-25 10:30:45.123 [http-nio-8080-exec-1] INFO com.example.service.OrderService - [b6c89963909788a33fddf391627d1808,6e1110d5294cbcd7] 주문 처리 시작
2024-01-25 10:30:45.156 [http-nio-8080-exec-1] DEBUG com.example.repository.OrderRepository - [b6c89963909788a33fddf391627d1808,3f2e8c1a9d7f5b2c] 데이터베이스 쿼리 실행
```

### 로그에서 트레이스 조회

Loki 쿼리로 특정 traceId의 모든 로그를 조회합니다:

```logql
# 특정 traceId의 모든 로그
{app="my-service"} | json | traceId="b6c89963909788a33fddf391627d1808"

# ERROR 로그만 필터
{level="ERROR"} | json | traceId="b6c89963909788a33fddf391627d1808"

# 특정 서비스들의 로그
{app=~"order-service|payment-service"} | json | traceId=~"b6c89963.*"
```

---

## Grafana 활용

### 접속 정보

| 서비스 | URL | 계정 |
|--------|-----|------|
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| Tempo | http://localhost:3200 | - |
| Loki | http://localhost:3100 | - |

### 대시보드 생성 (예시 1: 메트릭)

**주문 처리 시간 (P95)**

1. Grafana 접속 → Dashboard → New Panel
2. Data Source: Prometheus
3. 쿼리:
   ```promql
   histogram_quantile(0.95, rate(order_processing_duration_seconds_bucket[5m]))
   ```
4. Panel Type: Time series
5. Unit: Seconds

### 대시보드 생성 (예시 2: 로그)

**애플리케이션 에러 로그**

1. Data Source: Loki
2. 쿼리:
   ```logql
   {app="my-service",level="ERROR"} | json
   ```
3. Panel Type: Logs

### 대시보드 생성 (예시 3: 트레이스)

**특정 트레이스 상세 보기**

1. Tempo UI 접속 (http://localhost:3200)
2. Search → Service 선택 → 추적 조건 입력
3. 원하는 트레이스 클릭 → 전체 스팬 구조 확인

### Loki에서 Tempo로 연결

Loki 로그에서 traceId를 클릭하면 자동으로 Tempo의 해당 트레이스로 이동합니다.

설정: docker/grafana/provisioning/datasources/datasources.yml 참조

---

## 환경별 설정

### 개발 환경

```yaml
# application.yml
observability:
  tracing:
    aop:
      enabled: true

management:
  tracing:
    sampling:
      probability: 1.0  # 100% 추적

logging:
  level:
    root: INFO
    com.example: DEBUG
```

### 스테이징 환경

```yaml
# application-staging.yml
observability:
  tracing:
    aop:
      enabled: true

management:
  tracing:
    sampling:
      probability: 0.1  # 10% 샘플링

logging:
  level:
    root: WARN
    com.example: INFO
```

### 프로덕션 환경

```yaml
# application-prod.yml
observability:
  tracing:
    aop:
      enabled: true  # 활성화하되 샘플링으로 제어

management:
  tracing:
    sampling:
      probability: 0.01  # 1% 샘플링

logging:
  level:
    root: WARN
    com.example: WARN
    io.micrometer: WARN
```

### 프로파일 활성화

```bash
# 명령어
./gradlew bootRun --args='--spring.profiles.active=staging'

# 환경변수
export SPRING_PROFILES_ACTIVE=prod
./gradlew bootRun

# Docker
docker run -e SPRING_PROFILES_ACTIVE=prod your-app:latest
```

---

## 프로덕션 고려사항

### 1. 샘플링 전략

프로덕션에서는 100% 추적이 성능을 저하시키고 비용을 증가시킵니다.

```yaml
management:
  tracing:
    sampling:
      probability: 0.01  # 1%에서 시작
```

**권장 샘플링 비율**:
- 개발: 1.0 (100%)
- 스테이징: 0.1 (10%)
- 프로덕션: 0.01 ~ 0.001 (1% ~ 0.1%)

### 2. 로그 볼륨 관리

```yaml
logging:
  level:
    root: WARN              # 경고 이상만
    com.example: INFO       # 애플리케이션 INFO만
    org.springframework: WARN
    io.micrometer: WARN
```

### 3. Loki4j 배치 설정

```xml
<!-- logback-spring.xml -->
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
        <url>${LOKI_URL}/loki/api/v1/push</url>
        <batchMaxItems>500</batchMaxItems>  <!-- 500개 로그마다 전송 -->
        <batchTimeoutMs>5000</batchTimeoutMs>  <!-- 또는 5초마다 -->
    </http>
</appender>
```

### 4. 리소스 제한 설정

docker-compose.yml에서:

```yaml
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

tempo:
  deploy:
    resources:
      limits:
        memory: 2G
```

### 5. 데이터 보존 정책

```yaml
# docker-compose.yml - prometheus
prometheus:
  command:
    - '--storage.tsdb.retention.time=30d'
    - '--storage.tsdb.retention.size=50GB'
```

### 6. 보안

```yaml
# grafana.ini
[security]
admin_password = ${GRAFANA_PASSWORD}  # 강력한 비밀번호
allow_sign_up = false

[auth.anonymous]
enabled = false
```

---

## 트러블슈팅

### 문제 1: Trace가 Tempo에 나타나지 않음

**증상**: 요청을 보냈지만 Tempo에서 trace를 찾을 수 없음

**해결 방법**:

1. Java Agent 활성화 확인:
   ```bash
   docker logs your-app | grep -i "opentelemetry"
   ```

2. 환경변수 확인:
   ```bash
   docker exec your-app env | grep OTEL
   ```

   예상 출력:
   ```
   OTEL_SERVICE_NAME=your-app
   OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318
   OTEL_TRACES_EXPORTER=otlp
   ```

3. Tempo 연결 확인:
   ```bash
   docker exec your-app curl -v http://tempo:4318/v1/traces
   ```

4. 샘플링 비율 확인 (매우 낮으면 trace가 드물 수 있음):
   ```yaml
   management:
     tracing:
       sampling:
         probability: 1.0  # 테스트 중에는 100%로 설정
   ```

### 문제 2: 로그에 traceId가 없음

**증상**: 로그에 trace_id와 span_id가 표시되지 않음

**해결 방법**:

1. 환경변수 확인:
   ```bash
   OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE=true
   ```

2. Logback 패턴 확인 (snake_case 사용):
   ```xml
   <!-- logback-spring-observability.xml -->
   <pattern>... %X{trace_id} %X{span_id} ...</pattern>
   ```

   **주의**: Micrometer는 camelCase (`traceId`)를 사용하지만, Agent는 snake_case (`trace_id`)를 사용합니다.

3. 콘솔 출력으로 확인:
   ```bash
   curl http://localhost:8080/api/test
   docker logs your-app | tail -20
   ```

### 문제 3: AOP Span이 생성되지 않음

**증상**: Service/Repository 메서드가 span으로 추적되지 않음

**해결 방법**:

1. AOP 활성화 확인:
   ```yaml
   observability:
     tracing:
       aop:
         enabled: true
   ```

2. 클래스가 Spring Bean인지 확인:
   ```kotlin
   @Service  // 또는 @Repository, @Component
   class MyService {
       fun myMethod() { }
   }
   ```

3. public 메서드인지 확인:
   ```kotlin
   @Service
   class MyService {
       fun publicMethod() { }  // public (암묵적)
       private fun privateMethod() { }  // private는 추적 안 됨
   }
   ```

4. 패키지 위치 확인:
   - `com.example` 패키지 내에 있어야 함
   - `com.example.observability` 패키지는 제외됨

### 문제 4: JDBC 쿼리 span이 없음

**증상**: SQL 쿼리가 별도의 span으로 생성되지 않음

**해결 방법**:

1. 환경변수 확인:
   ```bash
   OTEL_INSTRUMENTATION_JDBC_ENABLED=true
   ```

2. JDBC Driver가 호환되는지 확인:
   - PostgreSQL: 지원
   - MySQL: 지원
   - Oracle: 지원

3. 실제 쿼리가 실행되는지 확인:
   ```bash
   curl http://localhost:8080/api/query-data
   docker logs postgres
   ```

### 문제 5: Prometheus에 메트릭이 없음

**증상**: /actuator/prometheus 엔드포인트가 있지만 메트릭이 없음

**해결 방법**:

1. 엔드포인트 활성화 확인:
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics
   ```

2. 메트릭 수집 확인:
   ```bash
   curl http://localhost:8080/actuator/prometheus | head -50
   ```

3. Prometheus 스크래핑 확인:
   ```bash
   curl http://localhost:9090/api/v1/targets
   ```

### 문제 6: 높은 CPU 또는 메모리 사용

**증상**: 100% 샘플링으로 인한 성능 저하

**해결 방법**:

1. 샘플링 비율 감소:
   ```yaml
   management:
     tracing:
       sampling:
         probability: 0.1  # 또는 더 낮게
   ```

2. 로깅 레벨 상향:
   ```yaml
   logging:
     level:
       root: WARN
       io.micrometer: WARN
   ```

3. AOP 비활성화 (필요시):
   ```yaml
   observability:
     tracing:
       aop:
         enabled: false
   ```

---

## 참고 자료

### 공식 문서
- [OpenTelemetry Java Agent](https://opentelemetry.io/docs/zero-code/java/agent/)
- [Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)
- [Micrometer Documentation](https://micrometer.io/)
- [Grafana LGTM Stack](https://grafana.com/docs/lgtm/)

### 프로젝트 문서
- [ARCHITECTURE.md](/Users/wisehero/Documents/GitHub/spring-boot-LGTM/docs/ARCHITECTURE.md) - 아키텍처 상세
- [Dockerfile](/Users/wisehero/Documents/GitHub/spring-boot-LGTM/sample-app/Dockerfile) - Docker 설정 예시
- [docker-compose.yml](/Users/wisehero/Documents/GitHub/spring-boot-LGTM/docker/docker-compose.yml) - 전체 스택 설정

### 버전 관리
- [gradle/libs.versions.toml](/Users/wisehero/Documents/GitHub/spring-boot-LGTM/gradle/libs.versions.toml) - 모든 의존성 버전

### 샘플 애플리케이션
- [observability-core](/Users/wisehero/Documents/GitHub/spring-boot-LGTM/observability-core/) - 재사용 가능한 모듈
- [sample-app](/Users/wisehero/Documents/GitHub/spring-boot-LGTM/sample-app/) - 완전한 구현 예시
