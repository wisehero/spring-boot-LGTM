# Spring Boot LGTM 관측가능성 - 설정 가이드

자신의 Spring Boot 프로젝트에 LGTM 관측가능성 스택(Logs-Grafana-Traces-Metrics)을 단계별로 적용하는 완전한 가이드입니다.

**목표**: 기존 Spring Boot 프로젝트에 복사-붙여넣기하여 OpenTelemetry 기반의 완전한 관측가능성을 구현합니다.

---

## 목차

1. [STEP 1: 프로젝트 의존성 설정](#step-1-프로젝트-의존성-설정)
2. [STEP 2: Docker 인프라 디렉토리 구조 생성](#step-2-docker-인프라-디렉토리-구조-생성)
3. [STEP 3: Prometheus 설정](#step-3-prometheus-설정)
4. [STEP 4: Loki 설정](#step-4-loki-설정)
5. [STEP 5: Tempo 설정](#step-5-tempo-설정)
6. [STEP 6: Grafana 설정](#step-6-grafana-설정)
7. [STEP 7: Spring Boot 애플리케이션 설정](#step-7-spring-boot-애플리케이션-설정)
8. [STEP 8: Dockerfile 설정](#step-8-dockerfile-설정)
9. [STEP 9: docker-compose.yml 통합](#step-9-docker-composeyml-통합)
10. [STEP 10: 검증 및 테스트](#step-10-검증-및-테스트)
11. [체크리스트 및 주의사항](#체크리스트-및-주의사항)

---

## STEP 1: 프로젝트 의존성 설정

### 1.1 gradle/libs.versions.toml 생성 또는 업데이트

프로젝트 루트의 `gradle/` 디렉토리에 버전 카탈로그 파일을 생성합니다. (이미 있다면 아래 내용 병합)

**파일**: `gradle/libs.versions.toml`

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
spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter" }
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator" }
spring-boot-starter-aop = { module = "org.springframework.boot:spring-boot-starter-aop" }
spring-boot-configuration-processor = { module = "org.springframework.boot:spring-boot-configuration-processor" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }

# Micrometer: 메트릭 및 트레이싱 추상화
micrometer-core = { module = "io.micrometer:micrometer-core", version.ref = "micrometer" }
micrometer-registry-prometheus = { module = "io.micrometer:micrometer-registry-prometheus", version.ref = "micrometer" }

# Loki: 로그 집계
loki-logback-appender = { module = "com.github.loki4j:loki-logback-appender", version.ref = "loki4j" }

# OpenTelemetry: Java Agent 방식에서 @WithSpan 어노테이션 지원
opentelemetry-instrumentation-annotations = { module = "io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations", version.ref = "opentelemetry-instrumentation" }

# OpenTelemetry API (Agent가 런타임에 구현체 제공)
opentelemetry-api = { module = "io.opentelemetry:opentelemetry-api", version.ref = "opentelemetry" }

[bundles]
# Java Agent 방식: Agent가 traces 전송 처리
# 가장 간단한 방식으로 권장됩니다
observability-agent = [
    "spring-boot-starter-actuator",
    "spring-boot-starter-aop",
    "micrometer-core",
    "micrometer-registry-prometheus",
    "opentelemetry-instrumentation-annotations",
    "loki-logback-appender"
]

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.7" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
```

### 1.2 build.gradle.kts에 의존성 추가

**파일**: `build.gradle.kts` (또는 `your-app/build.gradle.kts`)

```kotlin
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    // LGTM 관측가능성 번들 (이 파일에 이전 설정이 있다면, 이것만 추가)
    implementation(libs.bundles.observability.agent)

    // 기본 Spring Boot 의존성
    implementation(libs.spring.boot.starter.web)
    // ... 기타 프로젝트 의존성
}
```

### 1.3 의존성 역할 설명

| 의존성 | 역할 | 설명 |
|--------|------|------|
| `spring-boot-starter-actuator` | 메트릭 엔드포인트 | `/actuator/prometheus` 엔드포인트 활성화 |
| `spring-boot-starter-aop` | AOP 지원 | 메서드 레벨 추적을 위한 AspectJ 활성화 |
| `micrometer-core` | 메트릭 추상화 | 메트릭 API 제공 |
| `micrometer-registry-prometheus` | Prometheus 메트릭 | Micrometer → Prometheus 형식 변환 |
| `opentelemetry-instrumentation-annotations` | 커스텀 추적 어노테이션 | `@WithSpan` 어노테이션 지원 |
| `opentelemetry-api` | OTel API | Tracer API (Agent가 구현체 제공) |
| `loki-logback-appender` | Loki 로그 전송 | Logback → Loki HTTP API로 로그 전송 |

### 1.4 의존성 확인

```bash
# 의존성 다운로드 및 확인
./gradlew dependencies --configuration implementation
```

---

## STEP 2: Docker 인프라 디렉토리 구조 생성

프로젝트 루트에 `docker/` 디렉토리를 생성하고 다음 구조를 만듭니다:

```
docker/
├── docker-compose.yml              # 완전한 스택 (LGTM 인프라 + 앱)
├── docker-compose.infra.yml        # 인프라만 (로컬 개발용)
├── prometheus/
│   └── prometheus.yml              # Prometheus 설정
├── loki/
│   └── loki-config.yml             # Loki 설정
├── tempo/
│   └── tempo-config.yml            # Tempo 설정
├── grafana/
│   ├── grafana.ini                 # Grafana 설정
│   └── provisioning/
│       ├── datasources/
│       │   └── datasources.yml     # 데이터소스 (Prometheus, Loki, Tempo)
│       └── dashboards/
│           └── dashboards.yml      # 대시보드 프로비저닝
```

### 명령어로 디렉토리 생성

```bash
cd /path/to/your-project
mkdir -p docker/prometheus
mkdir -p docker/loki
mkdir -p docker/tempo
mkdir -p docker/grafana/provisioning/datasources
mkdir -p docker/grafana/provisioning/dashboards
```

---

## STEP 3: Prometheus 설정

Prometheus는 Spring Boot의 메트릭 엔드포인트에서 메트릭을 주기적으로 수집합니다.

### 3.1 Prometheus 설정 파일

**파일**: `docker/prometheus/prometheus.yml`

```yaml
# Prometheus 전역 설정
global:
  scrape_interval: 15s          # 메트릭 수집 주기 (기본값)
  evaluation_interval: 15s      # 규칙 평가 주기
  external_labels:
    monitor: 'spring-boot-lgtm'

# Alert Manager 설정 (선택사항)
alerting:
  alertmanagers:
    - static_configs:
        - targets: []

# 규칙 파일 (선택사항)
rule_files: []

# 메트릭 수집 대상 설정
scrape_configs:
  # Prometheus 자신의 메트릭
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  # Docker 환경에서 실행되는 Spring Boot 앱
  # 주의: your-app-name을 실제 Docker 서비스명으로 변경
  - job_name: 'spring-boot-app'
    metrics_path: '/actuator/prometheus'  # Spring Boot Actuator 메트릭 엔드포인트
    static_configs:
      - targets: ['your-app-name:8080']   # Docker 네트워크 내 서비스명:포트
    scrape_interval: 5s                   # 더 자주 수집 (로컬 개발)

  # 로컬 개발용 (IDE에서 앱을 Docker 외부에서 실행할 때)
  # host.docker.internal은 Docker 호스트의 localhost를 의미합니다
  - job_name: 'spring-boot-app-local'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
    scrape_interval: 5s
```

### 3.2 설정 항목 설명

| 항목 | 설명 | 예시 |
|------|------|------|
| `scrape_interval` | 메트릭 수집 주기 | 15s (프로덕션), 5s (개발) |
| `metrics_path` | 메트릭 엔드포인트 경로 | `/actuator/prometheus` (Spring Boot) |
| `targets` | 수집 대상 주소 | `service-name:port` (Docker) 또는 `host.docker.internal:port` (로컬) |
| `job_name` | 메트릭 그룹 이름 | 자유롭게 설정 (Prometheus UI에 표시됨) |

---

## STEP 4: Loki 설정

Loki는 로그를 수집하고 저장하는 로그 집계 시스템입니다.

### 4.1 Loki 설정 파일

**파일**: `docker/loki/loki-config.yml`

```yaml
# 인증 설정 (개발 환경에서 비활성화)
auth_enabled: false

# 서버 설정
server:
  http_listen_port: 3100        # HTTP API 포트
  grpc_listen_port: 9096        # gRPC 포트 (선택사항)
  log_level: info               # 로그 레벨

# 공통 설정
common:
  instance_addr: 127.0.0.1
  path_prefix: /tmp/loki        # 데이터 저장 경로
  storage:
    filesystem:
      chunks_directory: /tmp/loki/chunks
      rules_directory: /tmp/loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

# 쿼리 결과 캐싱 설정
query_range:
  results_cache:
    cache:
      embedded_cache:
        enabled: true
        max_size_mb: 100

# 스키마 설정
schema_config:
  configs:
    - from: 2020-10-24
      store: tsdb                # 저장소 유형 (TSDB 권장)
      object_store: filesystem
      schema: v13                # 스키마 버전
      index:
        prefix: index_
        period: 24h              # 인덱스 기간

# Alert Manager 설정
ruler:
  alertmanager_url: http://localhost:9093

# 로그 수집 제한 설정
limits_config:
  ingestion_rate_mb: 16                # 초당 로그 수집량 제한
  ingestion_burst_size_mb: 24          # 버스트 크기
  max_streams_per_user: 10000          # 최대 로그 스트림 수
  max_line_size: 256kb                 # 단일 로그 라인 최대 크기
  max_entries_limit_per_second: 10000  # 초당 최대 로그 엔트리

# 프론트엔드 설정 (쿼리 최적화)
frontend:
  max_outstanding_per_tenant: 2048
```

### 4.2 설정 항목 설명

| 항목 | 설명 | 개발 환경 | 프로덕션 |
|------|------|----------|---------|
| `auth_enabled` | 인증 활성화 | `false` | `true` |
| `ingestion_rate_mb` | 초당 수집량 제한 | 16 MB | 1-4 MB |
| `max_streams_per_user` | 최대 로그 스트림 | 10000 | 1000 |
| `max_line_size` | 로그 라인 최대 크기 | 256 KB | 256 KB |

---

## STEP 5: Tempo 설정

Tempo는 분산 추적(traces)을 저장하고 관리하는 백엔드 시스템입니다.

### 5.1 Tempo 설정 파일

**파일**: `docker/tempo/tempo-config.yml`

```yaml
# 서버 설정
server:
  http_listen_port: 3200        # Tempo API 포트

# OTLP 리시버 설정 (OpenTelemetry Protocol)
distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: "0.0.0.0:4317"     # OTLP gRPC (낮은 레이턴시)
        http:
          endpoint: "0.0.0.0:4318"     # OTLP HTTP (Java Agent 기본값)

# Ingester 설정 (수집된 트레이스 처리)
ingester:
  max_block_duration: 5m        # 트레이스 블록 최대 지속 시간

# Compactor 설정 (트레이스 압축)
compactor:
  compaction:
    block_retention: 1h         # 트레이스 보존 기간 (개발: 1시간, 프로덕션: 24시간 이상)

# Metrics Generator 설정 (Tempo의 RED 메트릭 생성)
metrics_generator:
  registry:
    external_labels:
      source: tempo
      cluster: docker-compose
  storage:
    path: /tmp/tempo/generator/wal
    # Prometheus로 메트릭 전송 (storage 아래에 위치해야 함)
    remote_write:
      - url: http://prometheus:9090/api/v1/write
        send_exemplars: true      # Exemplar 포함 (트레이스 ↔ 메트릭 연결)

# 스토리지 설정
storage:
  trace:
    backend: local              # 로컬 저장소 (Docker 개발용)
    wal:
      path: /tmp/tempo/wal
    local:
      path: /tmp/tempo/blocks

# 기본 설정 오버라이드
overrides:
  defaults:
    metrics_generator:
      # RED 메트릭 생성 프로세서
      processors: [service-graphs, span-metrics]
      # - service-graphs: 서비스 간 의존성 그래프 생성
      # - span-metrics: span 기반 요청/에러/지연 메트릭
```

### 5.2 OTLP 엔드포인트 설명

| 프로토콜 | 포트 | 용도 | 특징 |
|---------|------|------|------|
| **gRPC** | 4317 | 고성능 트레이스 전송 | 낮은 레이턴시, 바이너리 |
| **HTTP** | 4318 | REST 기반 전송 | OpenTelemetry Java Agent 기본값 |

### 5.3 Tempo가 생성하는 메트릭

Metrics Generator가 다음 메트릭을 Prometheus로 전송합니다:

```
# 요청 수 (각 서비스별)
service_graph_request_total{client="service-a",server="service-b"}

# 요청 실패 수
service_graph_request_failed_total{client="service-a",server="service-b"}

# 요청 지연 시간 분포
service_graph_request_duration_seconds{client="service-a",server="service-b",le=...}
```

---

## STEP 6: Grafana 설정

Grafana는 Prometheus, Loki, Tempo를 시각화하는 대시보드 플랫폼입니다.

### 6.1 Grafana 설정 파일

**파일**: `docker/grafana/grafana.ini`

```ini
# Grafana 인스턴스 설정
[instance_name]
instance_name = Spring Boot LGTM

# 서버 설정
[server]
http_port = 3000               # Grafana 웹 포트
root_url = http://localhost:3000
domain = localhost

# 보안 설정
[security]
admin_user = admin             # Grafana 기본 사용자
admin_password = admin         # 프로덕션에서 변경 필수!
disable_brute_force_login_protection = false
secret_key = your-secret-key   # 프로덕션에서 강력한 값 설정

# 사용자 관리
[users]
allow_sign_up = false          # 외부 사용자 가입 비활성화
allow_org_create = false
auto_assign_org_role = Viewer

# 익명 사용자 (선택사항)
[auth.anonymous]
enabled = false

# 로그 설정
[log]
mode = console
level = info
format = json

# 데이터소스 관리
[datasources]
datasource_uid_header_name = X-Grafana-UID

# 플러그인 설정 (선택사항)
[plugins]
admin_enabled = true

# 기능 토글 (Grafana 최신 기능)
[feature_toggles]
# TraceQL 쿼리 에디터 활성화
enable = traceqlEditor
# Tempo 서비스 그래프 활성화
enable = tempoServiceGraph
# Service Map 활성화
enable = serviceMap
```

### 6.2 Grafana 데이터소스 설정

**파일**: `docker/grafana/provisioning/datasources/datasources.yml`

이 파일은 Grafana 시작 시 Prometheus, Loki, Tempo를 자동으로 등록합니다.

```yaml
apiVersion: 1

datasources:
  # Prometheus: 메트릭 데이터소스
  - name: Prometheus
    type: prometheus
    uid: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true            # Grafana 기본 데이터소스
    editable: false
    jsonData:
      timeInterval: 30s

  # Loki: 로그 데이터소스
  - name: Loki
    type: loki
    uid: loki
    access: proxy
    url: http://loki:3100
    editable: false
    jsonData:
      # 로그의 traceId에서 Tempo 트레이스로 링크
      derivedFields:
        - name: TraceID
          matcherRegex: 'traceId=([a-f0-9]+)'        # 로그에서 traceId 추출
          url: ''
          datasourceUid: tempo                        # Tempo로 링크
          urlDisplayLabel: 'View Trace in Tempo'     # 버튼 라벨
          # 로그 예시: "traceId=abc123def456"
      # 로그에서 spanId 추출 (선택사항)
      spanStartTimeShift: -1h
      spanEndTimeShift: 1h

  # Tempo: 분산 추적 데이터소스
  - name: Tempo
    type: tempo
    uid: tempo
    access: proxy
    url: http://tempo:3200
    editable: false
    jsonData:
      # 트레이스에서 로그로 역링크 설정
      tracesToLogsV2:
        datasourceUid: loki                           # Loki 데이터소스
        spanStartTimeShift: '-1h'                     # 시간 범위 조정
        spanEndTimeShift: '1h'
        filterByTraceID: true                         # traceId로 필터링
        filterBySpanID: false
        customQuery: true
        # your-app-name을 실제 앱 이름으로 변경
        query: '{app="your-app-name"} |= "${__span.traceId}"'
      # 노드 그래프 활성화 (서비스 간 호출 시각화)
      nodeGraph:
        enabled: true
      # Service Map 활성화 (서비스 메시 시각화)
      serviceMap:
        datasourceUid: prometheus                     # Prometheus 메트릭 사용
      # Loki 검색 통합
      lokiSearch:
        datasourceUid: loki
      # 기본 검색 활성화
      search:
        hide: false
```

### 6.3 Grafana 데이터소스 연동 설명

| 연동 | 설명 | 버튼 위치 |
|------|------|----------|
| **Loki → Tempo** | 로그 클릭 → 트레이스 확인 | Loki 로그에서 "View Trace in Tempo" |
| **Tempo → Loki** | 트레이스 클릭 → 해당 로그 확인 | Tempo 스팬 상세 보기에서 "Logs for this span" |
| **Prometheus ↔ Tempo** | 메트릭에서 서비스 그래프 보기 | Grafana Service Map 또는 Tempo Node Graph |

### 6.4 대시보드 프로비저닝 설정

**파일**: `docker/grafana/provisioning/dashboards/dashboards.yml`

Grafana 시작 시 대시보드 파일을 자동으로 로드합니다.

```yaml
apiVersion: 1

providers:
  - name: 'Spring Boot LGTM'
    orgId: 1
    folder: 'Spring Boot LGTM'      # Grafana에서 표시할 폴더 이름
    folderUid: 'spring-boot-lgtm'
    type: file
    disableDeletion: false           # UI에서 삭제 가능
    updateIntervalSeconds: 30        # 파일 변경 감지 주기
    allowUiUpdates: true             # UI에서 수정 가능
    options:
      path: /etc/grafana/provisioning/dashboards  # 대시보드 파일 경로
```

---

## STEP 7: Spring Boot 애플리케이션 설정

### 7.1 application.yml 설정

Spring Boot 애플리케이션의 기본 설정입니다.

**파일**: `src/main/resources/application.yml`

```yaml
# 애플리케이션 기본 설정
spring:
  application:
    name: your-app-name          # Prometheus, Tempo, Loki에서 표시되는 앱 이름

# 서버 설정
server:
  port: 8080

# 관리 엔드포인트 설정
management:
  # Actuator 엔드포인트 노출
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics  # 필요한 엔드포인트만 노출

  # 헬스 체크 상세 정보
  endpoint:
    health:
      show-details: always         # 전체 헬스 정보 표시

  # 메트릭 기본 태그
  metrics:
    tags:
      application: ${spring.application.name}

  # 트레이싱 샘플링 설정
  tracing:
    sampling:
      probability: 1.0             # 개발: 100%, 프로덕션: 0.1 (10%)

  # Tempo OTLP 엔드포인트 설정 (로컬 개발용)
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces

# 로깅 설정
logging:
  level:
    root: INFO
    com.example: DEBUG
  # 로그 패턴 (traceId와 spanId 포함)
  pattern:
    # Java Agent는 snake_case 사용: trace_id, span_id
    # 주의: traceId (camelCase) 아님
    level: "%5p [${spring.application.name:},%X{trace_id:-},%X{span_id:-}]"
```

### 7.2 application-docker.yml 설정

Docker 환경에서 사용할 프로파일입니다.

**파일**: `src/main/resources/application-docker.yml`

```yaml
# Docker 환경 설정
spring:
  application:
    name: your-app-name

  # JPA 설정 (데이터베이스 사용 시)
  jpa:
    hibernate:
      ddl-auto: create-drop        # 개발: 매번 재생성, 프로덕션: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQL10Dialect

# 관측가능성 설정 (observability-core 자동 구성)
observability:
  tracing:
    aop:
      enabled: true                # AOP 기반 메서드 추적 활성화

# 로깅 설정
logging:
  level:
    root: INFO
    com.example: DEBUG
    # OpenTelemetry Agent 디버그 로깅 (선택사항)
    io.opentelemetry: INFO
```

### 7.3 logback-spring.xml 설정

Loki4j를 통해 로그를 Loki로 전송하고, traceId/spanId를 로그에 포함시키는 설정입니다.

**파일**: `src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 환경 변수 설정 -->
    <springProperty scope="context" name="APP_NAME" source="spring.application.name" defaultValue="your-app"/>
    <springProperty scope="context" name="ENV" source="spring.profiles.active" defaultValue="local"/>
    <springProperty scope="context" name="LOKI_URL" source="loki.url" defaultValue="http://localhost:3100"/>

    <!--
    중요: Java Agent는 MDC에 snake_case로 주입합니다!
    - trace_id (O) / traceId (X)
    - span_id (O) / spanId (X)
    -->
    <property name="TRACE_PATTERN" value="%X{trace_id:-},%X{span_id:-}"/>
    <property name="LOG_PATTERN" value="%d{ISO8601} [%thread] %-5level %logger{36} - [${TRACE_PATTERN}] %msg%n"/>

    <!-- 콘솔 Appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- Loki4j Appender - Loki로 로그 전송 -->
    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <http>
            <url>${LOKI_URL}/loki/api/v1/push</url>
        </http>
        <format>
            <label>
                <!-- Grafana에서 필터링할 라벨 -->
                <pattern>app=${APP_NAME},env=${ENV},level=%level</pattern>
            </label>
            <message>
                <!-- 로그 메시지에 traceId 포함 (Loki → Tempo 링크용) -->
                <pattern>traceId=%X{trace_id:-none} spanId=%X{span_id:-none} | %d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </message>
        </format>
        <batchMaxItems>100</batchMaxItems>
        <batchTimeoutMs>1000</batchTimeoutMs>
    </appender>

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

**핵심 설정 설명**:

| 설정 | 설명 |
|------|------|
| `trace_id` (snake_case) | Java Agent가 MDC에 주입하는 형식. `traceId` (camelCase) 아님! |
| `app=${APP_NAME}` | Loki 라벨 - Grafana에서 `{app="your-app"}` 쿼리에 사용 |
| `traceId=%X{trace_id:-none}` | 로그 메시지에 traceId 포함 - Loki → Tempo 링크에 사용 |
| `batchMaxItems` | 한 번에 전송할 최대 로그 수 (성능 조절) |

### 7.4 AOP 기반 메서드 추적 활성화

application-docker.yml에서 `observability.tracing.aop.enabled: true`를 설정하면, 다음 클래스의 public 메서드가 자동으로 span으로 추적됩니다:

- `@Service` 클래스
- `@Repository` 클래스
- `@Component` 클래스
- `com.example.*` 패키지의 모든 클래스

---

## STEP 8: Dockerfile 설정

OpenTelemetry Java Agent를 다운로드하고 JVM에 연결합니다.

### 8.1 Dockerfile 작성

**파일**: `Dockerfile` (프로젝트 루트 또는 앱 모듈 디렉토리)

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 헬스 체크를 위한 curl 설치
RUN apk add --no-cache curl

# OpenTelemetry Java Agent 다운로드
# v2.11.0은 2026년 1월 기준 안정 버전입니다
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# JAR 파일 복사
# docker-compose.yml의 build.context에 따라 경로가 다릅니다
# context: ./ → COPY your-app/build/libs/*.jar
# context: ./your-app → COPY build/libs/*.jar
COPY your-app/build/libs/your-app-*.jar app.jar

# JVM 옵션 설정
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=docker

# 포트 노출
EXPOSE 8080

# 헬스 체크 설정
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# OpenTelemetry Java Agent와 함께 JVM 실행
# -javaagent:/app/opentelemetry-javaagent.jar는 Agent 활성화
ENTRYPOINT ["sh", "-c", "java -javaagent:/app/opentelemetry-javaagent.jar $JAVA_OPTS -jar app.jar"]
```

### 8.2 Dockerfile 주요 설정

| 항목 | 설명 | 참고 |
|------|------|------|
| **FROM** | Eclipse Temurin 21 (LTS) | Java 21 기반 이미지 |
| **ADD (Agent)** | v2.11.0 다운로드 | 변경 가능한 최신 버전 확인 |
| **COPY (JAR)** | 빌드된 JAR 파일 복사 | context 경로에 따라 조정 |
| **JAVA_OPTS** | JVM 메모리 옵션 | MaxRAMPercentage=75% (컨테이너 메모리의 75%) |
| **ENTRYPOINT** | Agent 활성화 | `-javaagent:` 옵션 필수 |

### 8.3 Java Agent 버전 확인

최신 버전이 있는지 확인하고 필요시 업데이트:

```bash
# GitHub에서 최신 Agent 버전 확인
curl -s https://api.github.com/repos/open-telemetry/opentelemetry-java-instrumentation/releases | grep '"tag_name"' | head -5
```

---

## STEP 9: docker-compose.yml 통합

모든 LGTM 컴포넌트와 Spring Boot 앱을 통합하는 docker-compose 설정입니다.

### 9.1 완전한 docker-compose.yml

**파일**: `docker/docker-compose.yml`

```yaml
version: '3.8'

services:
  # === LGTM 스택 ===

  # Prometheus: 메트릭 수집 및 저장
  prometheus:
    image: prom/prometheus:v2.55.1
    container_name: prometheus
    ports:
      - "9090:9090"                # Web UI: http://localhost:9090
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.enable-lifecycle'
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:9090/-/healthy"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
    networks:
      - lgtm

  # Loki: 로그 수집 및 저장
  loki:
    image: grafana/loki:3.3.2
    container_name: loki
    ports:
      - "3100:3100"               # API: http://localhost:3100
    volumes:
      - ./loki/loki-config.yml:/etc/loki/local-config.yaml:ro
    command: -config.file=/etc/loki/local-config.yaml
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:3100/ready || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 20s
    networks:
      - lgtm

  # Tempo: 분산 추적 저장
  tempo:
    image: grafana/tempo:2.6.1
    container_name: tempo
    ports:
      - "3200:3200"               # Web UI: http://localhost:3200
      - "4317:4317"               # OTLP gRPC
      - "4318:4318"               # OTLP HTTP (Java Agent 기본값)
    volumes:
      - ./tempo/tempo-config.yml:/etc/tempo/tempo.yaml:ro
    command: ["-config.file=/etc/tempo/tempo.yaml"]
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:3200/ready"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 20s
    networks:
      - lgtm

  # Grafana: 대시보드 및 시각화
  grafana:
    image: grafana/grafana:11.4.0
    container_name: grafana
    ports:
      - "3000:3000"               # Web UI: http://localhost:3000 (admin/admin)
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
      - ./grafana/grafana.ini:/etc/grafana/grafana.ini:ro
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:3000/api/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    depends_on:
      prometheus:
        condition: service_healthy
      loki:
        condition: service_healthy
      tempo:
        condition: service_healthy
    networks:
      - lgtm

  # === 선택사항: 데이터베이스 ===

  # PostgreSQL: 샘플 데이터베이스 (JPA 사용 시)
  postgres:
    image: postgres:16-alpine
    container_name: postgres
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_USER=app
      - POSTGRES_PASSWORD=app
      - POSTGRES_DB=appdb
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d appdb"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
    networks:
      - lgtm

  # === Spring Boot 애플리케이션 ===

  # 주의: your-app-name을 실제 서비스명으로 변경
  your-app-name:
    build:
      context: ../                          # 프로젝트 루트 (Dockerfile 위치)
      dockerfile: Dockerfile
    container_name: your-app-name
    ports:
      - "8080:8080"               # Web: http://localhost:8080
    environment:
      # Spring 프로파일 및 기본 설정
      - SPRING_PROFILES_ACTIVE=docker
      - LOKI_URL=http://loki:3100
      - APP_NAME=your-app-name

      # OpenTelemetry Java Agent 설정
      - OTEL_SERVICE_NAME=your-app-name    # Tempo에서 표시되는 서비스 이름
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318  # Tempo OTLP HTTP 엔드포인트
      - OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf

      # 트레이스 수집 설정
      - OTEL_TRACES_EXPORTER=otlp          # Trace를 Tempo로 전송
      - OTEL_METRICS_EXPORTER=none         # 메트릭은 Prometheus가 수집
      - OTEL_LOGS_EXPORTER=none            # 로그는 Loki가 수집

      # 자동 계측 활성화
      - OTEL_INSTRUMENTATION_SPRING_WEBMVC_ENABLED=true  # HTTP 요청 추적
      - OTEL_INSTRUMENTATION_JDBC_ENABLED=true          # SQL 쿼리 추적
      - OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE=true  # 로그에 traceId 주입
      - OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED=true

      # 데이터베이스 연결 (PostgreSQL 사용 시)
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/appdb
      - SPRING_DATASOURCE_USERNAME=app
      - SPRING_DATASOURCE_PASSWORD=app

    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 3s
      retries: 3
      start_period: 40s

    depends_on:
      prometheus:
        condition: service_healthy
      loki:
        condition: service_healthy
      tempo:
        condition: service_healthy
      postgres:
        condition: service_healthy

    networks:
      - lgtm

networks:
  lgtm:
    driver: bridge
```

### 9.2 환경 변수 설명

#### OpenTelemetry 설정

| 변수 | 값 | 설명 |
|------|-----|------|
| `OTEL_SERVICE_NAME` | your-app-name | 서비스 식별자 (Tempo, 메트릭에서 사용) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | http://tempo:4318 | Tempo OTLP 엔드포인트 |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | http/protobuf | OTLP 프로토콜 |
| `OTEL_TRACES_EXPORTER` | otlp | Trace 내보내기 방식 |
| `OTEL_METRICS_EXPORTER` | none | Prometheus가 메트릭 수집 |
| `OTEL_LOGS_EXPORTER` | none | Loki가 로그 수집 |

#### 계측 활성화

| 변수 | 값 | 설명 |
|------|-----|------|
| `OTEL_INSTRUMENTATION_SPRING_WEBMVC_ENABLED` | true | HTTP 요청/응답 자동 추적 |
| `OTEL_INSTRUMENTATION_JDBC_ENABLED` | true | SQL 쿼리 자동 추적 |
| `OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE` | true | 로그 MDC에 traceId 주입 |
| `OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED` | true | 자동 설정 활성화 |

### 9.3 Docker Compose 실행 순서

Docker Compose는 자동으로 의존성 순서대로 시작합니다:

```
1. prometheus, loki, tempo 시작 (병렬)
       ↓
2. grafana 시작 (prometheus/loki/tempo이 healthy 상태 대기)
       ↓
3. postgres 시작 (병렬)
       ↓
4. your-app-name 시작 (모든 서비스가 healthy 상태 대기)
```

---

## STEP 10: 검증 및 테스트

### 10.1 애플리케이션 빌드

```bash
cd /path/to/your-project

# Gradle 빌드
./gradlew clean build

# 빌드 완료 확인
ls -la your-app/build/libs/*.jar
```

### 10.2 Docker 이미지 빌드 및 실행

```bash
cd docker

# LGTM 스택 + 앱 시작 (처음 실행 시 이미지 빌드)
docker-compose up -d

# 서비스 상태 확인
docker-compose ps

# 모든 서비스가 healthy 상태일 때까지 대기 (1-2분)
# Status가 "healthy" 또는 "running"이 될 때까지 기다립니다
watch docker-compose ps
```

### 10.3 서비스 접속

docker-compose가 정상 실행 중이면 다음 URL에 접속 가능합니다:

| 서비스 | URL | 인증 | 설명 |
|--------|-----|------|------|
| 애플리케이션 | http://localhost:8080 | 없음 | Spring Boot 앱 |
| Grafana | http://localhost:3000 | admin / admin | 대시보드 |
| Prometheus | http://localhost:9090 | 없음 | 메트릭 저장소 |
| Tempo | http://localhost:3200 | 없음 | 트레이스 저장소 |
| Loki | http://localhost:3100 | 없음 | 로그 저장소 |
| PostgreSQL | localhost:5432 | app / app | 데이터베이스 |

### 10.4 메트릭 엔드포인트 확인

```bash
# Actuator 헬스 체크
curl http://localhost:8080/actuator/health
# 응답: {"status":"UP",...}

# Actuator 정보
curl http://localhost:8080/actuator/info
# 응답: {"app":{"name":"your-app-name",...}}

# Prometheus 메트릭 확인
curl http://localhost:8080/actuator/prometheus | head -20
# 응답: Prometheus 형식 메트릭
# # HELP jvm_memory_used_bytes
# # TYPE jvm_memory_used_bytes gauge
# jvm_memory_used_bytes{area="heap",...} 1234567890
```

### 10.5 테스트 요청 생성

애플리케이션에 요청을 보내서 로그, 메트릭, 트레이스가 수집되는지 확인합니다:

```bash
# 기본 요청
curl http://localhost:8080/api/hello
# 응답: {"message":"Hello from Spring Boot LGTM!"}

# 여러 번 요청 생성
for i in {1..10}; do
  curl http://localhost:8080/api/hello
  sleep 1
done

# 지연 요청 (duration 추적)
curl http://localhost:8080/api/slow
# 2초 지연 후 응답

# 에러 생성 (exception 추적)
curl http://localhost:8080/api/error
# 500 에러 응답
```

### 10.6 Grafana에서 데이터 확인

#### 메트릭 확인 (Prometheus)

1. Grafana 접속: http://localhost:3000
2. 좌측 메뉴 → **Explore**
3. 데이터소스: **Prometheus** 선택
4. 쿼리 입력: `http_server_requests_seconds_count`
5. 결과 확인

#### 로그 확인 (Loki)

1. **Explore** 탭에서 데이터소스: **Loki** 선택
2. 쿼리 입력:
   ```logql
   {app="your-app-name"}
   ```
3. 로그 목록 확인
4. 로그 라인의 **traceId** 클릭 → Tempo 트레이스로 이동 (derivedFields 설정 필요)

#### 트레이스 확인 (Tempo)

1. **Explore** 탭에서 데이터소스: **Tempo** 선택
2. 상단 드롭다운에서 서비스: **your-app-name** 선택
3. 트레이스 목록 확인
4. 트레이스 클릭 → 상세 정보 확인
5. **Logs for this span** 링크 확인

### 10.7 로그-트레이스 연동 검증

```bash
# 1. 애플리케이션 로그에서 traceId 확인
docker logs your-app-name | grep "traceId=" | head -5

# 2. Loki에서 같은 traceId로 검색
# Grafana Explore → Loki → {app="your-app-name"} | json | traceId="abc123..."

# 3. Tempo에서 같은 traceId로 검색
# Grafana Explore → Tempo → TraceID 검색 또는 직접 입력
```

---

## 체크리스트 및 주의사항

### 완료 체크리스트

설정 완료 후 다음을 확인합니다:

- [ ] **의존성 설정**
  - [ ] `gradle/libs.versions.toml` 생성 또는 업데이트
  - [ ] `build.gradle.kts`에 `observability-agent` 번들 추가
  - [ ] `./gradlew build` 성공

- [ ] **Docker 인프라**
  - [ ] `docker/` 디렉토리 구조 생성
  - [ ] 모든 설정 파일 생성: prometheus.yml, loki-config.yml, tempo-config.yml, grafana.ini, datasources.yml, dashboards.yml

- [ ] **Spring Boot 설정**
  - [ ] `application.yml` 설정 (앱 이름, 메트릭 엔드포인트)
  - [ ] `application-docker.yml` 설정 (Docker 환경 설정)
  - [ ] `logback-spring.xml` 생성 (Loki4j Appender + snake_case MDC 패턴)

- [ ] **Dockerfile**
  - [ ] `Dockerfile` 생성 (OpenTelemetry Java Agent 포함)
  - [ ] Docker 빌드 성공: `docker build -t your-app .`

- [ ] **docker-compose.yml**
  - [ ] 모든 서비스 환경 변수 확인 (your-app-name 변경)
  - [ ] 포트 충돌 확인 (8080, 3000, 3100, 3200, 9090)

- [ ] **검증 테스트**
  - [ ] `docker-compose up -d` 성공
  - [ ] `docker-compose ps` - 모든 서비스 healthy
  - [ ] Grafana 접속: http://localhost:3000
  - [ ] 테스트 요청 생성 및 데이터 확인

### 주의사항

#### 1. 애플리케이션 이름 변경

다음 파일에서 `your-app-name`을 실제 애플리케이션 이름으로 변경합니다:

- `prometheus.yml`: `targets: ['your-app-name:8080']`
- `datasources.yml`: `query: '{app="your-app-name"}'`
- `docker-compose.yml`: 서비스 이름, 환경 변수, 컨테이너 이름
- `application.yml`: `spring.application.name: your-app-name`

#### 2. OpenTelemetry Java Agent 버전

현재 권장 버전: **v2.11.0** (2026년 1월)

최신 버전 확인:
```bash
curl -s https://api.github.com/repos/open-telemetry/opentelemetry-java-instrumentation/releases/latest | grep tag_name
```

#### 3. 로그 패턴의 snake_case vs camelCase

**주의**: Java Agent는 MDC에 **snake_case**로 주입합니다.

```xml
<!-- ✓ 올바른 방식 (snake_case) -->
<property name="TRACE_PATTERN" value="%X{trace_id:-},%X{span_id:-}"/>

<!-- ✗ 잘못된 방식 (camelCase) -->
<property name="TRACE_PATTERN" value="%X{traceId:-},%X{spanId:-}"/>
```

#### 4. Docker 네트워크 내부 통신

- **Docker 내부**: `http://service-name:port`
- **Docker 외부 (호스트)**: `http://localhost:port`
- **호스트에서 Docker 접근**: `http://host.docker.internal:port`

#### 5. 프로덕션 배포 시 필수 변경사항

```yaml
# application-prod.yml
management:
  tracing:
    sampling:
      probability: 0.1              # 10% 샘플링 (성능 영향 감소)

logging:
  level:
    root: WARN                       # INFO에서 WARN으로 상향

grafana:
  security:
    admin_password: strong-password  # 기본 비밀번호 변경 필수!
```

#### 6. 포트 충돌 확인

필수 포트: 8080, 3000, 3100, 3200, 9090, 5432

```bash
# 포트 사용 여부 확인 (macOS/Linux)
lsof -i :8080
lsof -i :3000
```

### 트러블슈팅

#### 문제: Traces이 Tempo에 나타나지 않음

**해결 방법**:
1. Tempo 헬스 확인: `curl http://localhost:3200/ready`
2. 앱 로그 확인: `docker logs your-app-name | grep -i otel`
3. 환경 변수 확인: `docker inspect your-app-name`
4. 요청 생성 후 재확인

#### 문제: 로그에 traceId가 없음

**해결 방법**:
1. Logback 설정에서 snake_case 확인: `%X{trace_id:-}` (traceId 아님)
2. 환경 변수 확인: `OTEL_INSTRUMENTATION_LOGBACK_MDC_ADD_BAGGAGE=true`
3. 앱 재시작: `docker-compose restart your-app-name`

#### 문제: Prometheus 메트릭이 없음

**해결 방법**:
1. 메트릭 엔드포인트 테스트: `curl http://localhost:8080/actuator/prometheus`
2. Prometheus targets 확인: http://localhost:9090/targets
3. `prometheus.yml`의 targets 주소 확인

---

## 참고 자료

- **OpenTelemetry Java Agent**: https://opentelemetry.io/docs/zero-code/java/agent/
- **Grafana LGTM Stack**: https://grafana.com/docs/lgtm/
- **Micrometer**: https://micrometer.io/
- **Loki4j**: https://github.com/loki4j/loki-logback-appender
- **Spring Boot Observability**: https://docs.spring.io/spring-boot/reference/actuator/observability.html

---

**마지막 업데이트**: 2026-01-25

**이 가이드는 spring-boot-LGTM 프로젝트의 샘플 구성을 기반으로 작성되었습니다.**
