# ClickHouse 전환 — 배경과 기대효과

이 문서는 LGTM 스택에 ClickHouse를 **병렬 백엔드로 추가**한 이유와, 그로부터 기대하는 효과·트레이드오프를 정리한다.
구현 자체(서비스·설정 파일)는 [아키텍처](ARCHITECTURE.md)와 `docker/` 디렉토리를 참고한다.

## 무엇을 바꿨나

기존에는 트레이스가 `OTel Java Agent → Tempo`로 직결됐다. 여기에 **OpenTelemetry Collector**를 한 계층 추가해
트레이스·로그를 LGTM과 ClickHouse 양쪽으로 동시에 보낸다(fan-out).

```
                         ┌─▶ Tempo (트레이스)         ─┐
서비스 ─OTLP─▶ OTel ─────┤                              ├─▶ Grafana
(agent)       Collector  └─▶ ClickHouse (트레이스+로그) ─┘    ├ Tempo / Loki / Prometheus (기존)
                                                              └ ClickHouse (신규, SQL)
로그: Loki4j ─▶ Loki (기존 유지)   +   agent OTLP 로그 ─▶ Collector ─▶ ClickHouse
메트릭: Prometheus scrape (변경 없음)
```

핵심은 **애플리케이션 코드 변경이 없다는 점**이다. agent의 `OTEL_EXPORTER_OTLP_ENDPOINT`를 Collector로 바꾸고
로그 exporter를 `otlp`로 켠 것이 전부다. 메트릭은 기존대로 Prometheus가 scrape 한다.

| 구성 요소 | 값 | 역할 |
|-----------|-----|------|
| OTel Collector (contrib) | 0.116.0 | OTLP 수신 → Tempo·ClickHouse fan-out |
| ClickHouse | 24.8 | 트레이스·로그 통합 OLAP 저장소 (포트 8123/9000) |
| Grafana 플러그인 | grafana-clickhouse-datasource | SQL로 traces/logs 조회 (Grafana ≥ 11.6) |

## 기대효과

### 1. 단일 저장소 + SQL 상관관계
LGTM은 Loki(LogQL)·Tempo(TraceQL)·Prometheus(PromQL)로 저장소와 질의 언어가 셋으로 나뉜다.
ClickHouse에서는 트레이스와 로그가 한 곳(`otel_traces`, `otel_logs`)에 들어가므로 **표준 SQL 하나로 다룬다**.
`TraceId`를 키로 로그·트레이스를 JOIN할 수 있고, 향후 주문/결제 같은 업무 테이블까지 함께 조인하는 것도 가능하다.
지금 `datasources.yml`에서 derivedFields·tracesToLogsV2로 수동 연결해 둔 상관관계를 쿼리 한 번으로 대체한다.

### 2. 고카디널리티 차원 처리
Prometheus는 `order_id`·`payment_id`·`user_id`처럼 값이 무수히 많은 라벨에 취약하다(시계열 폭발 → 메모리 압박).
ClickHouse는 컬럼형이라 이런 값을 span/log attribute로 저장하고 그대로 필터·집계할 수 있다.
order → product → payment Saga와 결제 보상(취소)이 있는 이 도메인에서, **특정 주문 ID 하나의 전체 트레이스+로그+결제 상태**를
바로 추적할 수 있다.

### 3. 임의 분석(ad-hoc analytics)
윈도우 함수, 서브쿼리, JOIN이 되므로 "지연 p99가 튄 주문들의 결제 보상 비율" 같은
**운영 지표와 비즈니스 지표를 섞은 질의**가 가능하다. LogQL/PromQL만으로는 다루기 어려운 영역이다.

### 4. 압축과 장기 보존
ClickHouse는 컬럼 압축(ZSTD/LZ4)으로 저장 효율이 높다.
현재 Tempo는 `block_retention: 1h`로 트레이스를 사실상 거의 남기지 않는데,
ClickHouse 쪽은 TTL을 72h로 잡아 두었고 비용 부담 없이 보존 기간을 늘릴 수 있다.

### 5. 대량 스캔 쿼리 성능
대용량 로그 풀스캔·집계에서 OLAP 엔진의 강점이 드러난다.
수억 row 규모에서도 집계 질의가 빠르게 돌아간다.

### 6. OTel 네이티브 · 무침투 통합
이미 OTLP로 텔레메트리를 내보내고 있어 Collector 한 계층만 추가하면 된다.
Collector의 ClickHouse exporter가 `create_schema`로 테이블을 자동 생성하므로 초기 스키마 작업도 거의 없다.

## 트레이드오프

| 항목 | 내용 |
|------|------|
| 메트릭은 Prometheus 유지 | PromQL·recording rules·alerting 생태계가 성숙하다. 메트릭까지 ClickHouse로 옮기는 건 이득 대비 비용이 크다. |
| 운영 복잡도 증가 | 스키마·TTL·파트 머지 관리가 생긴다. `create_schema`로 초기 부담은 낮췄지만 LGTM의 "던지면 끝"보다는 손이 간다. |
| 수집 파이프라인 추가 | Collector가 한 단계 늘어난다. 다만 fan-out이라 한쪽(Tempo/ClickHouse) 실패가 다른 쪽을 막지 않는다. |
| 알럿팅 | 실시간 알럿·룰 기반 경보는 Prometheus/LGTM이 우위다. |

## LGTM vs ClickHouse 한눈에

| 기준 | LGTM (Loki/Tempo/Prometheus) | ClickHouse |
|------|------------------------------|------------|
| 질의 언어 | LogQL · TraceQL · PromQL | 표준 SQL |
| 신호 통합 | 신호별 분리 저장 | logs·traces 단일 테이블군 |
| 고카디널리티 | Prometheus 취약 | 강함 |
| 상관관계 | 데이터소스 간 링크 | SQL JOIN |
| 압축·보존 | 백엔드별 정책 | 컬럼 압축 + TTL |
| 메트릭·알럿팅 | 강함 | 약함 |
| 운영 난이도 | 낮음 | 중간 |

## 정리

이 브랜치의 목표는 LGTM을 걷어내는 것이 아니라, **같은 텔레메트리를 두 백엔드에 동시에 적재해 Grafana에서 비교**하는 것이다.
트레이스·로그의 SQL 분석·고카디널리티·장기 보존이 필요하면 ClickHouse가, 메트릭·알럿팅·간편한 운영이 필요하면 LGTM이 강점을 가진다.
둘을 함께 두고 워크로드에 맞는 쪽을 고르는 구성이다.

검증 절차(테이블 자동 생성·데이터 적재 확인)는 [사용 가이드](USAGE.md)와 `docker/` 런북을 참고한다.
