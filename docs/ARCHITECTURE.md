# Spring Boot LGTM Observability - 아키텍처 문서

## 개요

이 프로젝트는 **모놀리식 Spring Boot 애플리케이션**에서 **LGTM 스택**을 활용한 완전한 관측가능성(Observability)을 구현합니다.

```
┌─────────────────────────────────────────────────────────────────┐
│                        Grafana (3000)                           │
│                    Dashboard & Exploration                       │
└──────────────┬──────────────┬──────────────┬────────────────────┘
               │              │              │
       ┌───────▼───┐   ┌──────▼──────┐   ┌──▼──────────┐
       │ Prometheus │   │    Loki     │   │   Tempo     │
       │   (9090)   │   │   (3100)    │   │   (3200)    │
       │  Metrics   │   │    Logs     │   │   Traces    │
       └───────▲───┘   └──────▲──────┘   └──▲──────────┘
               │              │              │
               │       ┌──────┴──────┐       │
               │       │   Loki4j    │       │
               │       │  Appender   │       │
               │       └──────▲──────┘       │
               │              │              │
       ┌───────┴──────────────┴──────────────┴───────┐
       │              Sample Application              │
       │                   (8080)                     │
       │  ┌─────────────────────────────────────┐    │
       │  │      OpenTelemetry Java Agent       │    │
       │  │  (bytecode instrumentation)         │    │
       │  └─────────────────────────────────────┘    │
       │  ┌─────────────────────────────────────┐    │
       │  │         TracingAspect (AOP)         │    │
       │  │  (Service/Repository auto-tracing)  │    │
       │  └─────────────────────────────────────┘    │
       └─────────────────────────────────────────────┘
```

## 프로젝트 구조

```
spring-boot-LGTM/
├── gradle/
│   └── libs.versions.toml        # 버전 카탈로그 (의존성 중앙 관리)
├── observability-core/           # 재사용 가능한 관측가능성 모듈
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/example/observability/
│       ├── aspect/
│       │   └── TracingAspect.kt  # AOP 기반 자동 추적
│       ├── config/
│       │   ├── ObservabilityAutoConfiguration.kt
│       │   ├── TracingConfiguration.kt
│       │   ├── MetricsConfiguration.kt
│       │   └── LoggingConfiguration.kt
│       └── filter/
│           └── RequestLoggingFilter.kt
├── sample-app/                   # 샘플 애플리케이션
│   ├── Dockerfile                # OTel Java Agent 포함
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/example/sample/
│       ├── controller/
│       │   └── SampleController.kt
│       ├── service/
│       │   └── SampleService.kt
│       └── repository/
│           └── DataRepository.kt
├── docker/
│   ├── docker-compose.yml        # 전체 스택 구성
│   ├── prometheus/
│   │   └── prometheus.yml
│   ├── loki/
│   │   └── loki-config.yml
│   ├── tempo/
│   │   └── tempo-config.yml
│   └── grafana/
│       ├── grafana.ini
│       └── provisioning/
│           ├── datasources/
│           │   └── datasources.yml  # Loki↔Tempo 상호 연결 설정
│           └── dashboards/
└── docs/
    └── ARCHITECTURE.md           # 이 문서
```

## 핵심 컴포넌트

### 1. OpenTelemetry Java Agent

**역할**: JVM 레벨에서 bytecode instrumentation을 통해 자동으로 트레이싱

```dockerfile
# Dockerfile
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", ...]
```

**자동 계측 대상**:
- HTTP 요청/응답 (Tomcat, Spring MVC)
- JDBC 쿼리
- HTTP 클라이언트 (RestTemplate, WebClient)
- 메시지 큐 (Kafka, RabbitMQ)

**환경변수 설정** (docker-compose.yml):
```yaml
environment:
  - OTEL_SERVICE_NAME=sample-app
  - OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318
  - OTEL_TRACES_EXPORTER=otlp
  - OTEL_METRICS_EXPORTER=none      # Prometheus가 메트릭 담당
  - OTEL_LOGS_EXPORTER=none         # Loki가 로그 담당
```

### 2. TracingAspect (AOP 기반 자동 추적)

**역할**: `@Service`, `@Repository` 클래스의 모든 public 메서드를 자동으로 span으로 추적

```kotlin
@Aspect
@Component
class TracingAspect {
    private val tracer by lazy {
        GlobalOpenTelemetry.getTracer("observability-aop", "1.0.0")
    }

    @Around("(serviceLayer() || repositoryLayer()) && publicMethod()")
    fun traceMethod(joinPoint: ProceedingJoinPoint): Any? {
        val span = tracer.spanBuilder(spanName)
            .setParent(Context.current())
            .startSpan()
        // ... 메서드 실행 및 span 종료
    }
}
```

**동작 원리**:
1. Java Agent가 `GlobalOpenTelemetry`에 SDK 등록
2. TracingAspect가 해당 SDK로 span 생성
3. Agent가 생성된 span을 Tempo로 전송

**결과 Trace 구조**:
```
GET /api/complex                          659ms  (Agent: Tomcat)
├─ SampleService.getUserData               90ms  (AOP)
│   └─ DataRepository.findById             56ms  (AOP)
├─ SampleService.getAllUsersWithStats     171ms  (AOP)
│   ├─ DataRepository.findAll             114ms  (AOP)
│   └─ DataRepository.count                 0ms  (AOP)
└─ SampleService.performOperation         260ms  (AOP)
```

### 3. 로그-트레이스 연동 (MDC)

**Java Agent의 MDC 자동 주입**:
```yaml
# docker-compose.yml
- OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE=true
```

**Logback 설정** (logback-spring-observability.xml):
```xml
<!-- Java Agent는 snake_case 사용: trace_id, span_id -->
<property name="TRACE_PATTERN" value="%X{trace_id:-},%X{span_id:-}"/>
```

**로그 출력 예시**:
```
[b6c89963909788a33fddf391627d1808,6e1110d5294cbcd7] Service: Processing data
```

### 4. Grafana 데이터소스 연동

**Loki → Tempo 연동** (derived fields):
```yaml
# datasources.yml
- name: Loki
  jsonData:
    derivedFields:
      - name: TraceID
        matcherRegex: 'traceId=([a-f0-9]+)'
        url: '$${__value.raw}'
        datasourceUid: tempo
```

**Tempo → Loki 연동** (tracesToLogsV2):
```yaml
- name: Tempo
  jsonData:
    tracesToLogsV2:
      datasourceUid: loki
      filterByTraceID: true
```

## 데이터 흐름

### Traces (추적)
```
[Application] → [OTel Agent] → [OTLP/HTTP] → [Tempo:4318] → [Grafana]
```

### Logs (로그)
```
[Application] → [Logback] → [Loki4j Appender] → [Loki:3100] → [Grafana]
```

### Metrics (메트릭)
```
[Application] → [Micrometer] → [/actuator/prometheus] ← [Prometheus:9090] → [Grafana]
```

## 의존성 구조

### observability-agent 번들 (libs.versions.toml)

```toml
observability-agent = [
    "spring-boot-starter-actuator",    # 메트릭 엔드포인트
    "spring-boot-starter-aop",         # AOP 지원
    "micrometer-core",                 # 메트릭
    "micrometer-registry-prometheus",  # Prometheus 포맷
    "opentelemetry-instrumentation-annotations",  # @WithSpan (선택적)
    "loki-logback-appender"            # Loki 로그 전송
]
```

**주의**: `opentelemetry-exporter-otlp`는 **포함하지 않음**
- Agent가 자체 exporter 제공
- 포함 시 localhost:4318로 중복 전송 시도

## 설정 가이드

### 새 프로젝트에 적용하기

1. **의존성 추가**:
```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":observability-core"))
    // 또는 별도 배포된 라이브러리
}
```

2. **Dockerfile 수정**:
```dockerfile
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
```

3. **환경변수 설정**:
```yaml
environment:
  - OTEL_SERVICE_NAME=your-app-name
  - OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318
  - OTEL_TRACES_EXPORTER=otlp
  - OTEL_METRICS_EXPORTER=none
  - OTEL_LOGS_EXPORTER=none
```

4. **application.yml**:
```yaml
observability:
  tracing:
    aop:
      enabled: true  # TracingAspect 활성화
```

### AOP 추적 비활성화

특정 환경에서 AOP 추적을 끄려면:
```yaml
observability:
  tracing:
    aop:
      enabled: false
```

## API 엔드포인트

| 엔드포인트 | 설명 |
|-----------|------|
| `GET /api/hello` | 단순 응답 |
| `GET /api/slow` | 2초 지연 응답 |
| `GET /api/error` | 에러 발생 |
| `GET /api/chain` | Service → private methods |
| `GET /api/users` | Service → Repository (다중) |
| `GET /api/users/{id}` | Service → Cache → Repository |
| `GET /api/complex` | 복합 호출 (전체 추적 테스트용) |

## 접속 정보

| 서비스 | URL | 인증 |
|--------|-----|------|
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| Tempo | http://localhost:3200 | - |
| Loki | http://localhost:3100 | - |
| Sample App | http://localhost:8080 | - |

## 트러블슈팅

### Trace가 Tempo에 없음
1. `OTEL_EXPORTER_OTLP_ENDPOINT` 확인 (tempo:4318)
2. `docker logs sample-app`에서 export 에러 확인
3. `opentelemetry-exporter-otlp` 의존성 제거 확인

### 로그에 traceId가 없음
1. `OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE=true` 확인
2. Logback에서 `%X{trace_id}` (snake_case) 사용 확인

### AOP Span이 생성되지 않음
1. `observability.tracing.aop.enabled=true` 확인
2. 대상 클래스에 `@Service` 또는 `@Repository` 어노테이션 확인
3. public 메서드인지 확인

## 참고 자료

- [OpenTelemetry Java Agent](https://opentelemetry.io/docs/zero-code/java/agent/)
- [Grafana LGTM Stack](https://grafana.com/docs/lgtm/)
- [Micrometer Tracing](https://micrometer.io/docs/tracing)
- [Loki4j Logback Appender](https://github.com/loki4j/loki-logback-appender)
