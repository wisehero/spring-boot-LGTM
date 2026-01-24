# Spring Boot LGTM 관측가능성 (Observability)

**OpenTelemetry Java Agent + AOP 기반의 자동 추적을 제공하는 production-ready Spring Boot 프로젝트**

완전한 관측가능성(LGTM 스택: **L**oki 로그, **G**rafana 시각화, **T**empo 분산 추적, Prometheus **M**etricscs)을 단일 모듈 임포트로 구현합니다.

## 주요 특징

- **Zero-Code Tracing**: OpenTelemetry Java Agent가 바이트코드 레벨에서 자동 계측
- **AOP 기반 메서드 추적**: `@Service`, `@Repository`의 모든 public 메서드 자동 span 생성
- **PostgreSQL 쿼리 추적**: JDBC 자동 계측으로 데이터베이스 쿼리 추적
- **로그-트레이스 양방향 연동**: 로그의 traceId 클릭 → Tempo 트레이스 확인, 그리고 역방향도 지원
- **재사용 가능한 모듈**: 단일 의존성으로 모든 Spring Boot 앱에 관측가능성 추가
- **LGTM 스택 통합**: 로그(Loki), 메트릭(Prometheus), 트레이스(Tempo), 시각화(Grafana) 완전 통합
- **자동 프로비저닝 대시보드**: JVM 및 애플리케이션 메트릭 대시보드 자동 배포
- **Production-ready**: 샘플링 제어, 헬스 체크, 리소스 관리 설정 완료

## 빠른 시작

### 필수 요구사항

- **Java 21+** (OpenJDK, Eclipse Adoptium 또는 유사)
- **Docker & Docker Compose** (LGTM 인프라)
- **Gradle 8.12+** (래퍼 포함)
- **curl** (엔드포인트 테스트용)

### Option 1: Docker Compose로 전체 스택 실행 (권장)

완전한 스택(LGTM 인프라 + 샘플 앱 + PostgreSQL)을 한 번에 실행합니다:

```bash
# 저장소 클론 (이미 클론되어 있다면 스킵)
cd /Users/wisehero/Documents/GitHub/spring-boot-LGTM

# 애플리케이션 빌드
./gradlew build

# LGTM 스택 + 샘플 앱 시작 (모든 서비스 포함)
cd docker
docker-compose up -d

# 모든 서비스 헬스 확인
docker-compose ps
```

1-2분 후 모든 서비스가 정상(healthy) 상태가 됩니다. 접속 주소:

| 서비스 | URL | 인증 |
|--------|-----|------|
| 샘플 앱 | http://localhost:8080 | 없음 |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | 없음 |
| Tempo | http://localhost:3200 | 없음 |
| Loki | http://localhost:3100 | 없음 |
| PostgreSQL | localhost:5432 | app / app |

### Option 2: 로컬 개발 (IDE에서 앱, Docker에서 인프라)

IDE에서 hot-reload로 개발할 때:

```bash
# LGTM 인프라만 시작 (샘플 앱 제외)
cd docker
docker-compose -f docker-compose.infra.yml up -d

# 다른 터미널에서 IDE 또는 명령행으로 앱 실행
cd /Users/wisehero/Documents/GitHub/spring-boot-LGTM
./gradlew :sample-app:bootRun

# 앱이 http://localhost:8080에서 실행됩니다
```

## 아키텍처 개요

### 데이터 흐름 다이어그램

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Spring Boot Application                        │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  OpenTelemetry Java Agent (바이트코드 계측)                      │   │
│  │  - HTTP 요청/응답 자동 추적                                       │   │
│  │  - JDBC 쿼리 자동 추적                                            │   │
│  │  - 로그에 traceId/spanId 자동 주입 (MDC)                         │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  TracingAspect (AOP)                                            │   │
│  │  - @Service, @Repository public 메서드 자동 span 생성            │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                  │                   │                  │                 │
│        Traces (OTLP/HTTP)   Metrics (Scrape)     Logs (Loki4j)           │
└────────┬─────────┼───────────────────┼──────────────────┼────────────────┘
         │         │                   │                  │
         ▼         ▼                   ▼                  ▼
      ┌─────────────────┐   ┌──────────────────┐   ┌────────────────┐
      │ Tempo (3200)    │   │ Prometheus (9090)│   │ Loki (3100)    │
      │ 분산 추적       │   │ 메트릭 집계      │   │ 로그 집계      │
      └────────┬────────┘   └────────┬─────────┘   └────────┬───────┘
               │                    │                       │
               │  Derived Fields    │                       │
               │  (traceId link)    │                       │
               │                    │                       │
               └────────┬──────────────┬───────────────────┘
                        │              │
                        ▼              ▼
                   ┌──────────────────────────┐
                   │    Grafana (3000)        │
                   │  Dashboard 및 탐색      │
                   └──────────────────────────┘
```

### 핵심 컴포넌트

#### 1. OpenTelemetry Java Agent (0-설정 자동 계측)

**역할**: JVM 레벨에서 바이트코드 계측을 통해 다음을 자동으로 추적합니다:
- HTTP 요청/응답 (Spring MVC, Tomcat)
- JDBC 쿼리 (PostgreSQL 포함)
- HTTP 클라이언트 (RestTemplate, WebClient)
- 메시지 큐 (Kafka, RabbitMQ)

**Dockerfile에서의 설정**:
```dockerfile
# OpenTelemetry Java Agent 다운로드 및 실행
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# Agent와 함께 실행
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
```

**환경 변수** (docker-compose.yml에서 설정):
```yaml
environment:
  - OTEL_SERVICE_NAME=sample-app           # 서비스 이름
  - OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318  # Tempo 엔드포인트
  - OTEL_TRACES_EXPORTER=otlp               # OTLP로 trace 전송
  - OTEL_METRICS_EXPORTER=none              # Prometheus가 메트릭 담당
  - OTEL_LOGS_EXPORTER=none                 # Loki가 로그 담당
  - OTEL_INSTRUMENTATION_JDBC_ENABLED=true  # JDBC 쿼리 추적 활성화
  - OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE=true  # 로그에 traceId 주입
```

#### 2. TracingAspect (AOP 기반 메서드 추적)

**역할**: `com.example` 패키지의 모든 public 메서드를 자동으로 span으로 추적합니다.

**동작 원리**:
```kotlin
@Aspect
@Component
class TracingAspect {
    private val tracer = GlobalOpenTelemetry.getTracer("observability-aop", "1.0.0")

    @Around("applicationPackage() && publicMethod()")
    fun traceMethod(joinPoint: ProceedingJoinPoint): Any? {
        val spanName = "${className}.${methodName}"
        val span = tracer.spanBuilder(spanName).startSpan()
        try {
            return joinPoint.proceed()
        } finally {
            span.end()
        }
    }
}
```

**자동 추적 대상**:
- `@Service` 클래스의 public 메서드
- `@Repository` 클래스의 public 메서드
- `@Component` 클래스의 public 메서드
- 모든 other classes under `com.example.*`

#### 3. 결과 트레이스 예시

`GET /api/complex` 호출 시 Tempo에서 보이는 트레이스:

```
GET /api/complex                              659ms (HTTP)
├─ SampleController.complexOperation          650ms (AOP)
│   ├─ SampleService.getUserData              90ms (AOP)
│   │   └─ SELECT * FROM users WHERE id=...    56ms (JDBC)
│   ├─ SampleService.getUserData              85ms (AOP)
│   │   └─ SELECT * FROM users WHERE id=...    50ms (JDBC)
│   ├─ SampleService.getAllUsersWithStats    171ms (AOP)
│   │   ├─ SELECT * FROM users                114ms (JDBC)
│   │   └─ SELECT COUNT(*) FROM users           2ms (JDBC)
│   └─ SampleService.performOperation        260ms (AOP)
└─ json_encode                                  8ms (기타)
```

**설명**:
- HTTP 요청은 Agent가 자동 추적
- 각 Service 메서드는 AOP가 자동 span 생성
- 각 SQL 쿼리는 JDBC 계측이 자동 추적
- 모든 span이 부모-자식 관계로 자동 연결

#### 4. 로그-트레이스 양방향 연동

**로그에서 보이는 형식**:
```
2025-01-25 10:15:32.123 [sample-app,b6c89963909788a33fddf391627d1808,6e1110d5294cbcd7] INFO  SampleService: Processing user data
```

- `b6c8...1808` = traceId (클릭 가능)
- `6e11...bcd7` = spanId

**Grafana 설정**:
- **Loki → Tempo**: 로그의 traceId 링크 클릭 시 Tempo 트레이스 표시
- **Tempo → Loki**: 트레이스 상세 보기에서 "Logs for this span" 클릭 시 해당 로그 표시

## 자신의 프로젝트에 적용하는 방법

### Step 1: observability-core 의존성 추가

**build.gradle.kts**:
```kotlin
dependencies {
    implementation(project(":observability-core"))
    // 또는 Maven Central에 배포 후:
    // implementation("com.example:observability-core:0.0.1-SNAPSHOT")
}
```

### Step 2: Dockerfile에 Java Agent 추가

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN apk add --no-cache curl

# OpenTelemetry Java Agent 다운로드
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# 빌드된 JAR 복사
COPY your-app/build/libs/your-app-*.jar app.jar

# 포트 노출
EXPOSE 8080

# Agent와 함께 실행
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
```

### Step 3: docker-compose.yml에 환경변수 설정

**docker-compose.yml** (sample-app 서비스 참조):
```yaml
services:
  your-app:
    build:
      context: ./
      dockerfile: Dockerfile
    environment:
      # OpenTelemetry Java Agent 설정
      - OTEL_SERVICE_NAME=your-app-name
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318
      - OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
      - OTEL_TRACES_EXPORTER=otlp
      - OTEL_METRICS_EXPORTER=none        # Prometheus가 메트릭 담당
      - OTEL_LOGS_EXPORTER=none           # Loki가 로그 담당

      # 계측 활성화
      - OTEL_INSTRUMENTATION_SPRING_WEBMVC_ENABLED=true
      - OTEL_INSTRUMENTATION_JDBC_ENABLED=true
      - OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE=true

      # 데이터베이스 연결 (PostgreSQL 사용 시)
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/appdb
      - SPRING_DATASOURCE_USERNAME=app
      - SPRING_DATASOURCE_PASSWORD=app
    depends_on:
      postgres:
        condition: service_healthy
```

### Step 4: application.yml 설정

**src/main/resources/application.yml**:
```yaml
spring:
  application:
    name: your-app-name

management:
  # 메트릭 및 헬스 엔드포인트 노출
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics

  # Tracing 샘플링 설정
  tracing:
    sampling:
      probability: 1.0  # 개발: 100%, 프로덕션: 0.1 (10%)

  # AOP 기반 메서드 자동 추적 활성화
  observability:
    tracing:
      aop:
        enabled: true  # TracingAspect 활성화 (기본값: true)

logging:
  level:
    root: INFO
    your.package: DEBUG
  pattern:
    # Java Agent는 MDC에 snake_case로 주입합니다 (trace_id, span_id)
    level: "%5p [${spring.application.name:},%X{trace_id:-},%X{span_id:-}]"
```

### Step 5: 시작 스크립트 (선택사항)

```bash
#!/bin/bash
# start-observability.sh

cd docker

# LGTM 인프라 시작
docker-compose up -d

echo "LGTM 스택 시작 중..."
echo "Grafana: http://localhost:3000 (admin/admin)"
echo "Prometheus: http://localhost:9090"
echo "Tempo: http://localhost:3200"
echo "Loki: http://localhost:3100"
echo "앱: http://localhost:8080"
```

## API 엔드포인트 테스트

### 기본 엔드포인트

```bash
# 1. 간단한 요청 (기본 로깅)
curl http://localhost:8080/api/hello
# Response: {"message":"Hello from Spring Boot LGTM!"}

# 2. 지연 요청 (duration 추적)
curl http://localhost:8080/api/slow
# 2초 지연 후: {"message":"Slow response completed"}

# 3. 에러 요청 (exception 추적)
curl http://localhost:8080/api/error
# Response: 500 Error (exception이 Tempo에서 추적됨)

# 4. 체인 호출 (Service → 다중 메서드)
curl http://localhost:8080/api/chain
# Response: {"message":"Chain completed","serviceResult":...}

# 5. 사용자 조회 (Repository 호출)
curl http://localhost:8080/api/users/user-123
# Response: {"found":true,"user":{...}}

# 6. 모든 사용자 + 통계
curl http://localhost:8080/api/users
# Response: {"users":[...],"statistics":{...}}

# 7. 복합 작업 (다중 Service 호출)
curl http://localhost:8080/api/complex
# Response: {"user1":{...},"user2":{...},"statistics":{...},"operationResult":...}

# 8. 메트릭 확인
curl http://localhost:8080/actuator/prometheus | grep http_server
# Prometheus 포맷 메트릭 출력

# 9. 헬스 체크
curl http://localhost:8080/actuator/health
# Response: {"status":"UP",...}
```

## PostgreSQL + JPA 통합 및 JDBC 쿼리 추적

이 프로젝트는 PostgreSQL 데이터베이스와 Spring Data JPA를 사용합니다. OpenTelemetry Java Agent가 모든 JDBC 쿼리를 자동으로 추적합니다.

### 데이터베이스 설정

**docker-compose.yml**의 PostgreSQL 서비스:
```yaml
postgres:
  image: postgres:16-alpine
  environment:
    - POSTGRES_USER=app
    - POSTGRES_PASSWORD=app
    - POSTGRES_DB=sampledb
```

### JPA 엔티티 정의

**User.kt**:
```kotlin
@Entity
@Table(name = "users")
class User(
    @Id
    var id: String,
    var name: String,
    var age: Int
)
```

### Repository 작성

```kotlin
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, String> {
    fun findByName(name: String): List<User>
}
```

### 서비스에서 사용

```kotlin
@Service
class UserService(private val userRepository: UserRepository) {

    // AOP가 자동으로 "UserService.findUser" span 생성
    // JDBC 계측이 SELECT 쿼리 자동 추적
    fun findUser(userId: String): User? {
        return userRepository.findById(userId).orElse(null)
    }

    // 여러 쿼리 호출 → 각각 별도 span으로 추적
    fun getAllUsersWithStats(): Map<String, Any> {
        val users = userRepository.findAll()       // SELECT * FROM users
        val count = userRepository.count()         // SELECT COUNT(*) FROM users
        return mapOf(
            "users" to users,
            "totalCount" to count
        )
    }
}
```

### Tempo에서 JDBC 쿼리 추적 확인

**Tempo UI에서 trace 보기**:
```
GET /api/users                                   145ms
├─ UserService.getAllUsersWithStats              142ms
│   ├─ UserRepository.findAll                     78ms
│   │   └─ SELECT * FROM users                    56ms (JDBC)
│   └─ UserRepository.count                       62ms
│       └─ SELECT COUNT(*) FROM users              1ms (JDBC)
└─ Response Encoding                              2ms
```

**로그에서 확인**:
```
[app,abc123def456,span789] SELECT * FROM users WHERE id = ? [LIMIT 1]
[app,abc123def456,span790] SELECT COUNT(*) FROM users
```

### 주의사항

- **자동 계측**: JDBC 환경변수만 설정하면 모든 SQL 쿼리가 자동으로 추적됩니다
- **성능 영향**: 프로덕션에서는 `OTEL_INSTRUMENTATION_JDBC_ENABLED=false`로 비활성화 고려 (또는 sampling 비율 감소)
- **쿼리 파라미터**: 보안상 파라미터는 `?`로 표시되며 실제 값은 기록되지 않습니다

## Grafana 대시보드 활용

### 자동 프로비저닝된 대시보드

프로젝트는 두 가지 대시보드를 자동으로 배포합니다:

#### 1. JVM 메트릭 대시보드

Java Virtual Machine 상태를 모니터링합니다:
- Heap 메모리 사용량 및 GC 활동
- 스레드 개수 및 상태
- CPU 사용률
- 클래스 로딩

**접속**: Grafana 메인 → Dashboards → JVM Metrics

#### 2. 애플리케이션 메트릭 대시보드

애플리케이션 동작을 모니터링합니다:
- HTTP 요청 레이트 및 지연시간
- 엔드포인트별 에러율
- 서비스 응답 시간
- 커스텀 애플리케이션 메트릭

**접속**: Grafana 메인 → Dashboards → Application Metrics

### 로그 탐색 (Loki)

**Grafana Explore 탭**:
1. Data Source: **Loki** 선택
2. 쿼리 예시:
   ```
   {app="sample-app"} | json | level="ERROR"
   ```
3. 결과에서 traceId 클릭 → Tempo의 해당 트레이스로 이동

### 트레이스 탐색 (Tempo)

**Grafana Explore 탭**:
1. Data Source: **Tempo** 선택
2. 서비스, 기간, 상태로 필터링
3. 트레이스 클릭 → 상세 정보 및 "Logs for this span" 링크 확인

## 프로젝트 구조

```
spring-boot-LGTM/
├── observability-core/           # 재사용 가능한 관측가능성 모듈
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/example/observability/
│       ├── config/
│       │   ├── ObservabilityAutoConfiguration.kt   # Spring Boot Auto-config
│       │   ├── TracingConfiguration.kt             # Agent 모드 설정
│       │   ├── MetricsConfiguration.kt             # 메트릭 설정
│       │   └── LoggingConfiguration.kt             # 로깅 설정
│       ├── aspect/
│       │   └── TracingAspect.kt                    # AOP 기반 자동 추적
│       └── filter/
│           └── RequestLoggingFilter.kt             # HTTP 요청 필터
│
├── sample-app/                   # 데모 애플리케이션
│   ├── Dockerfile                # OpenTelemetry Java Agent 포함
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/example/sample/
│       ├── SampleApplication.kt
│       ├── controller/SampleController.kt          # REST 컨트롤러
│       ├── service/SampleService.kt                # 비즈니스 로직
│       ├── entity/User.kt                          # JPA 엔티티
│       └── repository/UserRepository.kt            # Data JPA
│
├── docker/                       # LGTM 스택 구성
│   ├── docker-compose.yml        # 완전한 스택 (LGTM + 샘플 앱 + DB)
│   ├── docker-compose.infra.yml  # 인프라만 (로컬 개발용)
│   ├── prometheus/
│   │   └── prometheus.yml        # Prometheus 설정
│   ├── loki/
│   │   └── loki-config.yml       # Loki 설정
│   ├── tempo/
│   │   └── tempo-config.yml      # Tempo 설정
│   └── grafana/
│       ├── provisioning/
│       │   ├── datasources/datasources.yml         # 데이터소스 설정
│       │   └── dashboards/                         # 자동 대시보드
│       └── grafana.ini           # Grafana 설정
│
├── docs/
│   ├── ARCHITECTURE.md           # 한글 아키텍처 문서
│   └── USAGE.md                  # 상세 사용 가이드
│
├── gradle/
│   └── libs.versions.toml        # 버전 카탈로그 (의존성 중앙 관리)
│
├── build.gradle.kts              # 루트 빌드 설정
├── settings.gradle.kts           # 멀티-모듈 설정
├── gradlew                        # Gradle 래퍼 (Linux/Mac)
├── gradlew.bat                    # Gradle 래퍼 (Windows)
└── README.md                      # 이 파일
```

## 설정 레퍼런스

### 환경 변수

Docker Compose에서 사용되는 환경 변수:

| 변수 | 기본값 | 설명 | 설정 위치 |
|------|--------|------|----------|
| `OTEL_SERVICE_NAME` | `sample-app` | 서비스 이름 | docker-compose.yml |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://tempo:4318` | Tempo OTLP 엔드포인트 | docker-compose.yml |
| `OTEL_TRACES_EXPORTER` | `otlp` | Trace 내보내기 방식 | docker-compose.yml |
| `OTEL_INSTRUMENTATION_JDBC_ENABLED` | `true` | JDBC 쿼리 추적 | docker-compose.yml |
| `OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE` | `true` | 로그에 traceId 주입 | docker-compose.yml |
| `SPRING_DATASOURCE_URL` | - | 데이터베이스 URL | docker-compose.yml |
| `SPRING_DATASOURCE_USERNAME` | - | 데이터베이스 사용자 | docker-compose.yml |

### Spring Boot 설정 (application.yml)

```yaml
spring:
  application:
    name: your-app-name

management:
  # 메트릭 및 헬스 엔드포인트 노출
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics

  # Tracing 샘플링 (개발: 1.0 = 100%, 프로덕션: 0.1 = 10%)
  tracing:
    sampling:
      probability: 1.0

  # Tempo 엔드포인트 설정
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces

# 로그 패턴 (traceId 및 spanId 포함)
logging:
  level:
    root: INFO
    your.package: DEBUG
  pattern:
    level: "%5p [${spring.application.name:},%X{trace_id:-},%X{span_id:-}]"
```

## 트러블슈팅

### 문제: Traces이 Tempo에 나타나지 않음

**증상**: Tempo UI에서 "No traces found" 메시지

**해결 방법**:

1. Tempo가 정상 실행 중인지 확인:
   ```bash
   curl http://localhost:3200/ready
   # 응답: 204 No Content
   ```

2. OTLP 엔드포인트 설정 확인:
   ```bash
   docker logs sample-app | grep OTEL_EXPORTER_OTLP_ENDPOINT
   # 출력: http://tempo:4318 (Docker 내부 네트워크)
   ```

3. 샘플 요청 생성 및 Tempo 확인:
   ```bash
   curl http://localhost:8080/api/chain
   sleep 2
   # Tempo UI에서 새 trace 확인 → http://localhost:3200
   ```

4. Agent 로그에서 export 에러 확인:
   ```bash
   docker logs sample-app 2>&1 | grep -i export
   ```

### 문제: 로그에 traceId가 없음

**증상**: 로그 출력: `[sample-app,-,-]` (traceId 부분 공백)

**해결 방법**:

1. Agent MDC 설정 확인:
   ```bash
   docker inspect sample-app | grep OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE
   # 응답: true
   ```

2. Logback 설정에서 snake_case 사용 확인:
   ```xml
   <!-- logback-spring.xml에서 -->
   <property name="TRACE_PATTERN" value="%X{trace_id:-},%X{span_id:-}"/>
   <!-- trace_id (snake_case) 사용, traceId (camelCase) 아님 -->
   ```

3. 앱 재시작:
   ```bash
   docker restart sample-app
   ```

### 문제: 메트릭이 Prometheus에 없음

**증상**: Prometheus UI의 dashboards 목록이 비어 있음

**해결 방법**:

1. 메트릭 엔드포인트 확인:
   ```bash
   curl http://localhost:8080/actuator/prometheus | head -20
   # Prometheus 포맷 메트릭 출력 확인
   ```

2. Prometheus 스크레이프 대상 확인:
   - http://localhost:9090/targets 접속
   - `spring-boot-app` 대상 상태: **UP**
   - 최근 스크레이프 타임스탬프 확인

3. Prometheus 설정 확인:
   ```bash
   cat docker/prometheus/prometheus.yml | grep -A 5 spring-boot-app
   # metrics_path: '/actuator/prometheus' 확인
   # targets: ['sample-app:8080'] 확인 (Docker 네트워크)
   ```

4. Docker 네트워크 연결 확인:
   ```bash
   docker exec prometheus curl -v http://sample-app:8080/actuator/prometheus
   # 200 OK 응답 확인
   ```

### 문제: Grafana 데이터소스 연결 실패

**증상**: Grafana UI에서 "Connection failed" 경고

**해결 방법**:

1. 모든 LGTM 서비스가 healthy 상태인지 확인:
   ```bash
   docker-compose ps
   # 모든 서비스 Status: healthy 또는 running
   ```

2. 데이터소스 내부 네트워크 이름 확인:
   - Prometheus: `http://prometheus:9090` (localhost 아님)
   - Loki: `http://loki:3100`
   - Tempo: `http://tempo:3200`

3. Grafana에서 데이터소스 테스트:
   - Grafana UI → Configuration → Data sources
   - 각 데이터소스의 "Test" 버튼 클릭
   - "Data source is working" 메시지 확인

4. Grafana 재시작:
   ```bash
   docker-compose restart grafana
   ```

## 기술 스택

| 컴포넌트 | 버전 | 용도 |
|---------|------|------|
| Spring Boot | 3.4.1 | 애플리케이션 프레임워크 |
| Kotlin | 1.9.22 | 프로그래밍 언어 |
| OpenTelemetry Java Agent | 2.11.0 | 자동 바이트코드 계측 |
| Micrometer | 1.14.2 | 메트릭 및 트레이싱 추상화 |
| Spring Data JPA | 3.4.1 | 데이터베이스 접근 |
| PostgreSQL | 16 | 관계형 데이터베이스 |
| Grafana | 11.4.0 | 시각화 플랫폼 |
| Prometheus | 2.55.1 | 메트릭 저장소 |
| Loki | 3.3.2 | 로그 집계 시스템 |
| Tempo | 2.6.1 | 분산 추적 백엔드 |

## 프로덕션 배포 가이드

### 1. Trace 샘플링 조정

프로덕션에서는 trace 샘플링을 줄여 성능 영향을 최소화합니다:

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 10% 샘플링 (환경에 따라 조정)
```

### 2. 로그 레벨 설정

불필요한 로그를 줄여 성능과 스토리지를 절약합니다:

```yaml
logging:
  level:
    root: WARN          # INFO에서 WARN으로 상향
    your.package: INFO  # 중요한 앱 로그는 INFO 유지
```

### 3. 리소스 제한 설정

Docker 컨테이너의 메모리 및 CPU 제한을 설정합니다:

```yaml
services:
  sample-app:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 512M
        reservations:
          cpus: '0.5'
          memory: 256M
```

### 4. Prometheus 보존 정책

메트릭 저장 용량을 제어합니다:

```yaml
prometheus:
  command:
    - '--storage.tsdb.retention.time=30d'  # 30일 보존
    - '--storage.tsdb.retention.size=10GB' # 10GB 용량 제한
```

### 5. 헬스 체크 및 모니터링

Docker Compose의 healthcheck가 자동으로 설정되어 있습니다. Kubernetes 배포 시:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 40
  periodSeconds: 30

readinessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 10
```

## 기여 가이드

코드 기여 전 다음을 확인하세요:

1. Kotlin 스타일 컨벤션 준수
   ```bash
   ./gradlew ktlintFormat
   ```

2. 테스트 작성 및 실행
   ```bash
   ./gradlew test
   ```

3. 전체 빌드 확인
   ```bash
   ./gradlew clean build
   ```

4. 문서 업데이트 (README, 주석)

## 라이선스

MIT

## 추가 리소스

- [OpenTelemetry Java Agent 공식 문서](https://opentelemetry.io/docs/zero-code/java/agent/)
- [Grafana LGTM Stack 가이드](https://grafana.com/docs/lgtm/)
- [Micrometer Tracing 문서](https://micrometer.io/docs/tracing)
- [Spring Boot Observability 가이드](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [Loki4j Logback Appender](https://github.com/loki4j/loki-logback-appender)

## 커뮤니티 및 지원

- 이슈 보고: GitHub Issues
- 개선 사항 제안: GitHub Discussions
- 질문 및 토론: GitHub Discussions

---

**이 프로젝트로 Spring Boot 애플리케이션의 완전한 관측가능성을 달성하세요!**

마지막 업데이트: 2026-01-25
