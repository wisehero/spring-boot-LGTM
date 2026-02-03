# Grafana 대시보드 구현 계획

> **작성일:** 2026-01-25
> **상태:** 승인됨 (Ralplan 검증 완료)
> **예상 작업량:** 4-6시간

---

## 1. 개요

Spring Boot LGTM 프로젝트에 RED 메트릭 기반 Grafana 대시보드를 추가하여 관측성(Observability) 데모를 완성합니다.

### 1.1 목표

- RED(Rate, Error, Duration) 방법론 기반 대시보드 구축
- 로그-트레이스-메트릭 상관관계 시각화
- 한국어 문서화로 학습 자료 제공

### 1.2 RED 방법론이란?

| 지표 | 설명 | 질문 |
|------|------|------|
| **R**ate | 초당 요청 수 | 얼마나 많은 요청을 처리하는가? |
| **E**rrors | 오류율 | 얼마나 많은 요청이 실패하는가? |
| **D**uration | 응답 시간 | 각 요청이 얼마나 오래 걸리는가? |

---

## 2. 현재 상태 분석

### 2.1 기존 대시보드

| 대시보드 | 파일 | 용도 |
|----------|------|------|
| Application Metrics | `application-dashboard.json` | HTTP 요청 메트릭 |
| JVM Metrics | `jvm-dashboard.json` | JVM 상태 모니터링 |

### 2.2 데이터소스 (구성 완료)

| 데이터소스 | UID | URL | 용도 |
|------------|-----|-----|------|
| Prometheus | `prometheus` | http://prometheus:9090 | 메트릭 |
| Loki | `loki` | http://loki:3100 | 로그 |
| Tempo | `tempo` | http://tempo:3200 | 트레이스 |

### 2.3 사용 가능한 메트릭

**HTTP 메트릭 (Micrometer):**
```
http_server_requests_seconds_count{method, uri, status, outcome}
http_server_requests_seconds_sum{method, uri, status, outcome}
http_server_requests_seconds_bucket{method, uri, status, outcome, le}
```

**JVM 메트릭:**
```
jvm_memory_used_bytes, jvm_memory_max_bytes
jvm_gc_pause_seconds_count, jvm_gc_pause_seconds_sum
jvm_threads_live_threads
process_cpu_usage
```

---

## 3. 구현할 대시보드

### 3.1 대시보드 구조

```
Spring Boot LGTM (폴더)
├── RED Metrics Overview        [신규] ← 핵심 대시보드
├── Logs & Traces Explorer      [신규]
├── Service Health              [신규]
├── Application Metrics         [기존]
└── JVM Metrics                 [기존]
```

### 3.2 RED Metrics Overview (핵심)

**파일:** `docker/grafana/provisioning/dashboards/red-metrics-dashboard.json`

**레이아웃:**
```
┌─────────────────────────────────────────────────────────────────┐
│ [Endpoint 선택 ▼]  [시간 범위]  [새로고침: 10s]                  │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │
│  │ Total RPS│  │ p99 지연 │  │ 에러율 % │  │ 상태     │        │
│  │  (게이지) │  │ (게이지) │  │ (게이지) │  │ (게이지) │        │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘        │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────┐  ┌─────────────────────────┐      │
│  │  엔드포인트별 요청률     │  │  상태코드별 요청률       │      │
│  │     (Time Series)       │  │    (Stacked Area)       │      │
│  └─────────────────────────┘  └─────────────────────────┘      │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────┐  ┌─────────────────────────┐      │
│  │  에러율 (4xx vs 5xx)    │  │   최근 에러 로그        │      │
│  │     (Time Series)       │  │    (Logs Panel)         │      │
│  └─────────────────────────┘  └─────────────────────────┘      │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────┐  ┌─────────────────────────┐      │
│  │  지연시간 백분위수       │  │   지연시간 히트맵       │      │
│  │   (p50, p95, p99)       │  │    (Histogram)          │      │
│  └─────────────────────────┘  └─────────────────────────┘      │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    최근 트레이스 (Tempo)                 │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

**패널 목록:**

| ID | 패널 | 타입 | 쿼리 |
|----|------|------|------|
| 1 | Total Request Rate | gauge | `sum(rate(http_server_requests_seconds_count[5m]))` |
| 2 | p99 Latency | gauge | `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))` |
| 3 | Error Rate | gauge | `sum(rate(...{status=~"[45].."}[5m])) / sum(rate(...[5m]))` |
| 4 | Application Health | gauge | `up{job="spring-boot-app"}` |
| 5 | Request Rate by Endpoint | timeseries | `sum(rate(...[5m])) by (uri)` |
| 6 | Request Rate by Status | timeseries | `sum(rate(...[5m])) by (status)` |
| 7 | Error Rate (4xx/5xx) | timeseries | 4xx, 5xx 분리 쿼리 |
| 8 | Recent Error Logs | logs | `{app="sample-app"} \|= "ERROR"` |
| 9 | Latency Percentiles | timeseries | p50, p95, p99 |
| 10 | Latency Heatmap | heatmap | histogram bucket 쿼리 |
| 11 | Recent Traces | traces | TraceQL 쿼리 |

**임계값 설정:**

| 패널 | 🟢 정상 | 🟡 경고 | 🔴 위험 |
|------|--------|--------|--------|
| Request Rate | < 100 | 100-500 | > 500 |
| p99 Latency | < 500ms | 500ms-1s | > 1s |
| Error Rate | 0% | < 1% | > 1% |
| Health | 1 | - | 0 |

### 3.3 Logs & Traces Explorer

**파일:** `docker/grafana/provisioning/dashboards/logs-traces-dashboard.json`

**패널:**
- Log Volume (시계열) - 시간별 로그 발생량
- Log Stream (로그) - 실시간 로그 스트림
- Trace Search (트레이스) - 서비스/지연시간별 검색

**LogQL 쿼리 예시:**
```logql
# 레벨별 로그 볼륨
sum by (level) (count_over_time({app="sample-app"}[1m]))

# 에러 로그 (트레이스 연동)
{app="sample-app"} |= "ERROR" | json | line_format "{{.message}} [traceId={{.traceId}}]"
```

**TraceQL 쿼리 예시:**
```
# 느린 트레이스 찾기
{ resource.service.name = "sample-app" && duration > 1s }

# 에러 트레이스 찾기
{ resource.service.name = "sample-app" && status = error }
```

### 3.4 Service Health

**파일:** `docker/grafana/provisioning/dashboards/service-health-dashboard.json`

**패널:**
| 패널 | 쿼리 |
|------|------|
| Uptime | `avg_over_time(up{job="spring-boot-app"}[24h]) * 100` |
| Memory Usage | `jvm_memory_used_bytes / jvm_memory_max_bytes` |
| CPU Usage | `process_cpu_usage` |
| GC Pressure | `rate(jvm_gc_pause_seconds_sum[5m])` |
| Thread Count | `jvm_threads_live_threads` |

---

## 4. 구현 단계

### Phase 1: RED Dashboard (핵심)

- [ ] `red-metrics-dashboard.json` 생성
- [ ] 11개 패널 구현
- [ ] 변수(endpoint) 설정
- [ ] 임계값 및 색상 설정
- [ ] 패널 설명(한국어) 추가

### Phase 2: Logs & Traces Explorer

- [ ] `logs-traces-dashboard.json` 생성
- [ ] 로그-트레이스 연동 패널 구현
- [ ] LogQL/TraceQL 쿼리 최적화

### Phase 3: Service Health

- [ ] `service-health-dashboard.json` 생성
- [ ] JVM/시스템 메트릭 패널 구현

### Phase 4: 문서화

- [ ] `docs/GRAFANA_DASHBOARDS.md` 작성 (사용 가이드)
- [ ] `README.md` 업데이트

---

## 5. 핵심 쿼리 레퍼런스

### 5.1 Rate 쿼리

```promql
# 전체 RPS
sum(rate(http_server_requests_seconds_count{uri=~"$endpoint"}[5m]))

# 엔드포인트별 RPS
sum(rate(http_server_requests_seconds_count{uri=~"$endpoint"}[5m])) by (uri)

# 상태코드별 RPS
sum(rate(http_server_requests_seconds_count{uri=~"$endpoint"}[5m])) by (status)
```

### 5.2 Error 쿼리

```promql
# 5xx 에러율
sum(rate(http_server_requests_seconds_count{uri=~"$endpoint",status=~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count{uri=~"$endpoint"}[5m]))

# 4xx 에러율
sum(rate(http_server_requests_seconds_count{uri=~"$endpoint",status=~"4.."}[5m]))
/
sum(rate(http_server_requests_seconds_count{uri=~"$endpoint"}[5m]))
```

### 5.3 Duration 쿼리

```promql
# p50 지연시간
histogram_quantile(0.50,
  sum(rate(http_server_requests_seconds_bucket{uri=~"$endpoint"}[5m])) by (le)
)

# p95 지연시간
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{uri=~"$endpoint"}[5m])) by (le)
)

# p99 지연시간
histogram_quantile(0.99,
  sum(rate(http_server_requests_seconds_bucket{uri=~"$endpoint"}[5m])) by (le)
)
```

---

## 6. 파일 변경 요약

### 신규 파일

| 파일 | 설명 |
|------|------|
| `docker/grafana/provisioning/dashboards/red-metrics-dashboard.json` | RED 메트릭 대시보드 |
| `docker/grafana/provisioning/dashboards/logs-traces-dashboard.json` | 로그/트레이스 탐색기 |
| `docker/grafana/provisioning/dashboards/service-health-dashboard.json` | 서비스 상태 대시보드 |
| `docs/GRAFANA_DASHBOARDS.md` | 대시보드 사용 가이드 |

### 수정 파일

| 파일 | 변경 내용 |
|------|----------|
| `README.md` | 대시보드 섹션 추가 |

---

## 7. 검증 방법

```bash
# 1. Grafana 재시작
docker-compose restart grafana

# 2. 대시보드 로드 확인
curl -u admin:admin http://localhost:3000/api/search?query=RED

# 3. 테스트 트래픽 생성
for i in {1..10}; do
  curl http://localhost:8080/api/hello
  curl http://localhost:8080/api/slow
  curl http://localhost:8080/api/error || true
done

# 4. 대시보드 확인
open http://localhost:3000/d/red-metrics-overview
```

---

## 8. 리스크 및 대응

| 리스크 | 영향도 | 대응 방안 |
|--------|--------|----------|
| 메트릭 이름 불일치 | 높음 | `/actuator/prometheus`로 사전 확인 |
| Grafana 버전 호환성 | 중간 | schemaVersion 39 사용 (Grafana 11.x) |
| 변수 문법 오류 | 중간 | Explore에서 쿼리 개별 테스트 |
| 대시보드 JSON 문법 오류 | 낮음 | 배포 전 JSON 유효성 검사 |

---

## 9. 완료 기준

- [ ] 모든 대시보드가 에러 없이 로드됨
- [ ] 11개 패널 모두 실제 데이터 표시
- [ ] 변수 필터링 동작 확인
- [ ] 로그 → 트레이스 클릭 연동 동작
- [ ] 한국어 문서 작성 완료
- [ ] 데모 시나리오 실행 가능

---

## 10. 커밋 전략

```
feat(grafana): add RED metrics dashboard with documentation

- Add red-metrics-dashboard.json with Rate, Error, Duration panels
- Add logs-traces-dashboard.json for log/trace exploration
- Add service-health-dashboard.json for health overview
- Add GRAFANA_DASHBOARDS.md documentation in Korean
- Update README.md with dashboard section
```
