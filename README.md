# Spring Boot LGTM Observability

**OpenTelemetry Java Agent + AOP 기반 자동 추적을 제공하는 Spring Boot 관측가능성 스타터 프로젝트**

LGTM 스택(**L**oki, **G**rafana, **T**empo, Prometheus **M**etrics)을 활용한 완전한 관측가능성을 제공합니다.

## 주요 특징

- 🔍 **Zero-Code Tracing** - OpenTelemetry Java Agent로 HTTP, JDBC, 메시지 큐 자동 계측
- 🎯 **AOP 메서드 추적** - `@Service`, `@Repository` public 메서드 자동 span 생성
- 🔗 **로그-트레이스 연동** - 로그 → 트레이스, 트레이스 → 로그 양방향 링크
- 📊 **자동 대시보드** - JVM 및 애플리케이션 메트릭 대시보드 프로비저닝
- 📦 **재사용 모듈** - `observability-core` 의존성 하나로 모든 기능 활성화

## 빠른 시작

```bash
# 저장소 클론
git clone https://github.com/wisehero/spring-boot-LGTM.git
cd spring-boot-LGTM

# 빌드
./gradlew build

# 전체 스택 실행 (LGTM + 샘플 앱 + PostgreSQL)
cd docker && docker-compose up -d

# 상태 확인
docker-compose ps
```

**서비스 접속:**

| 서비스 | URL | 인증 |
|--------|-----|------|
| 샘플 앱 | http://localhost:8080 | - |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| Tempo | http://localhost:3200 | - |
| Loki | http://localhost:3100 | - |

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                       │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │            OpenTelemetry Java Agent                        │  │
│  │  • HTTP 요청/응답 자동 추적                                │  │
│  │  • JDBC 쿼리 자동 추적                                     │  │
│  │  • 로그 MDC에 trace_id/span_id 주입                        │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              TracingAspect (AOP)                           │  │
│  │  • @Service/@Repository public 메서드 span 생성            │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│         Traces            Metrics              Logs              │
│        (OTLP/HTTP)        (Scrape)           (Loki4j)           │
└────────────┬─────────────────┬─────────────────┬────────────────┘
             │                 │                 │
             ▼                 ▼                 ▼
┌────────────────┐  ┌──────────────────┐  ┌────────────────┐
│  Tempo (3200)  │  │ Prometheus (9090)│  │  Loki (3100)   │
│   분산 추적    │  │    메트릭 수집   │  │   로그 집계    │
└───────┬────────┘  └────────┬─────────┘  └───────┬────────┘
        │                    │                    │
        └────────────────────┼────────────────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │   Grafana (3000)     │
                  │  Dashboard & Explore │
                  └──────────────────────┘
```

## 트레이스 예시

`GET /api/complex` 호출 시 Tempo에서 확인되는 트레이스:

```
GET /api/complex                           659ms
├─ SampleController.complexOperation       650ms
│  ├─ SampleService.getUserData             90ms
│  │  └─ SELECT * FROM users WHERE id=?     56ms
│  ├─ SampleService.getAllUsersWithStats   171ms
│  │  ├─ SELECT * FROM users               114ms
│  │  └─ SELECT COUNT(*) FROM users          2ms
│  └─ SampleService.performOperation       260ms
└─ Response encoding                         8ms
```

## 프로젝트 구조

```
spring-boot-LGTM/
├── observability-core/          # 재사용 가능한 관측가능성 모듈
│   └── src/.../observability/
│       ├── aspect/TracingAspect.kt
│       └── config/
├── sample-app/                  # 샘플 애플리케이션
│   ├── Dockerfile               # OTel Java Agent 포함
│   └── src/.../sample/
├── docker/                      # LGTM 인프라
│   ├── docker-compose.yml
│   ├── prometheus/
│   ├── loki/
│   ├── tempo/
│   └── grafana/
└── docs/                        # 문서
    ├── SETUP_GUIDE.md           # 설정 가이드 (10단계)
    ├── ARCHITECTURE.md          # 아키텍처 상세
    └── USAGE.md                 # 사용 가이드
```

## 기술 스택

| 컴포넌트 | 버전 | 용도 |
|---------|------|------|
| Spring Boot | 3.4.1 | 애플리케이션 프레임워크 |
| Kotlin | 1.9.22 | 프로그래밍 언어 |
| OpenTelemetry Agent | 2.11.0 | 자동 계측 |
| Micrometer | 1.14.2 | 메트릭 추상화 |
| Grafana | 11.4.0 | 시각화 |
| Prometheus | 2.55.1 | 메트릭 저장소 |
| Loki | 3.3.2 | 로그 집계 |
| Tempo | 2.6.1 | 분산 추적 |

## 문서

| 문서 | 설명 |
|------|------|
| **[설정 가이드](docs/SETUP_GUIDE.md)** | 자신의 프로젝트에 적용하는 10단계 가이드 |
| [아키텍처](docs/ARCHITECTURE.md) | 시스템 구조 및 데이터 흐름 |
| [사용 가이드](docs/USAGE.md) | 상세 사용법 및 커스터마이징 |

## 외부 참고 자료

- [OpenTelemetry Java Agent](https://opentelemetry.io/docs/zero-code/java/agent/)
- [Grafana LGTM Stack](https://grafana.com/docs/lgtm/)
- [Spring Boot Observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)

## 라이선스

MIT

---

마지막 업데이트: 2026-01-25
