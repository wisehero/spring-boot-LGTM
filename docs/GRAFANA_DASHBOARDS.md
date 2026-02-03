# Grafana 대시보드 사용 가이드

**Spring Boot LGTM 프로젝트의 Grafana 대시보드를 효과적으로 사용하기 위한 완벽한 가이드입니다.**

---

## 목차

1. [접속 정보](#1-접속-정보)
2. [대시보드 개요](#2-대시보드-개요)
3. [RED Metrics Overview](#3-red-metrics-overview)
4. [Logs & Traces Explorer](#4-logs--traces-explorer)
5. [Service Health](#5-service-health)
6. [실전 사용 시나리오](#6-실전-사용-시나리오)
7. [팁 & 활용법](#7-팁--활용법)
8. [트러블슈팅](#8-트러블슈팅)

---

## 1. 접속 정보

### 1.1 기본 접속 정보

| 항목 | 정보 |
|------|------|
| **URL** | http://localhost:3000 |
| **초기 계정** | admin |
| **초기 암호** | admin |
| **포트** | 3000 |

### 1.2 보안 설정 (프로덕션)

처음 로그인 후 **반드시 비밀번호를 변경**하세요.

1. 좌측 메뉴에서 프로필 아이콘 클릭
2. **Preferences** 선택
3. **Change password** 클릭
4. 새 비밀번호 설정

### 1.3 주요 메뉴 구성

```
좌측 사이드바
├─ Dashboards (대시보드)
│  └─ Spring Boot LGTM 폴더
│     ├─ RED Metrics Overview ← 핵심 대시보드
│     ├─ Logs & Traces Explorer
│     ├─ Service Health
│     ├─ Application Metrics (기존)
│     └─ JVM Metrics (기존)
├─ Explore (탐색)
├─ Alerts (알림)
└─ Configuration (설정)
   └─ Data Sources (데이터소스)
```

---

## 2. 대시보드 개요

### 2.1 대시보드 비교표

| 대시보드 | 용도 | 주요 사용자 | 보는 기간 |
|---------|------|-----------|---------|
| **RED Metrics Overview** | 성능 모니터링 및 병목 분석 | SRE, DevOps, 백엔드 개발자 | 최근 15분 ~ 1시간 |
| **Logs & Traces Explorer** | 에러 디버깅 및 요청 추적 | 개발자 | 최근 15분 |
| **Service Health** | 서비스 상태 및 자원 사용률 | 모든 팀 | 최근 6시간 |

### 2.2 데이터소스 연결 상태 확인

**설정 > Data Sources**에서 다음 데이터소스가 모두 **초록색 "Connected"** 상태인지 확인하세요:

```
✓ Prometheus     (http://prometheus:9090)   - 메트릭
✓ Loki          (http://loki:3100)         - 로그
✓ Tempo         (http://tempo:3200)        - 트레이스
```

문제가 있으면 [트러블슈팅](#8-트러블슈팅)을 참고하세요.

---

## 3. RED Metrics Overview

**성능 모니터링의 핵심 대시보드입니다. RED 방법론(Rate, Error, Duration)을 기반으로 한 11개 패널을 제공합니다.**

### 3.1 RED 방법론이란?

RED 방법론은 마이크로서비스 시스템의 성능을 평가하는 표준 방법론입니다:

| 지표 | 설명 | 정상 범위 | 경고 | 위험 |
|------|------|---------|------|------|
| **Rate** | 초당 요청 수 (RPS) | < 100 | 100-500 | > 500 |
| **Error** | 오류 발생률 (%) | 0% | < 1% | > 1% |
| **Duration** | 응답 시간 (ms) | < 500ms | 500-1000ms | > 1000ms |

### 3.2 TOP 4 - 주요 성능 지표

대시보드 상단의 4개 게이지 패널은 현재 상태를 **한눈에** 보여줍니다:

#### 📊 1. Total RPS (총 요청 수)
- **의미**: 애플리케이션이 처리하는 초당 요청 수
- **읽는 방법**:
  - 녹색(< 100): 정상
  - 노란색(100-500): 부하 증가
  - 빨간색(> 500): 부하 위험
- **대응**: 빨간색 지속 시 → 수평 확장 검토

#### ⏱️ 2. p99 Latency (응답 시간)
- **의미**: 상위 1%를 제외한 모든 요청이 이 시간 내에 완료됨
- **읽는 방법**:
  - 녹색(< 500ms): 정상
  - 노란색(500-1000ms): 지연 증가
  - 빨간색(> 1000ms): 성능 저하
- **대응**: 갑작스런 상승 → 아래 패널들에서 엔드포인트별 분석

#### 🚨 3. Error Rate % (오류율)
- **의미**: 전체 요청 중 실패한 요청의 비율 (4xx, 5xx)
- **읽는 방법**:
  - 녹색(0%): 모든 요청 성공
  - 노란색(~1%): 일부 오류
  - 빨간색(> 1%): 심각한 오류 발생
- **대응**: 급상승 → 에러 로그 확인 (아래 "Recent Error Logs" 패널)

#### 🟢 4. Health (애플리케이션 상태)
- **의미**: 애플리케이션 가용성 (1 = UP, 0 = DOWN)
- **읽는 방법**:
  - 녹색(1): 정상 작동
  - 빨간색(0): 응답 없음
- **대응**: 빨간색 → 애플리케이션 재시작 확인

### 3.3 시계열 패널 해석

#### 📈 5. Request Rate by Endpoint (엔드포인트별 요청률)
- **사용처**: 특정 엔드포인트의 트래픽 변화 추적
- **읽는 방법**:
  - 각 색상 선이 하나의 엔드포인트를 나타냄
  - 전체 요청의 흐름을 파악할 수 있음
- **예시**: GET /api/users 가 갑자기 상승 → 배치 작업이나 크롤러 증가

#### 📊 6. Request Rate by Status (상태코드별 요청률)
- **사용처**: HTTP 상태코드별 요청 추이 모니터링
- **읽는 방법**:
  - 200(초록): 성공
  - 301/302(파랑): 리다이렉트
  - 400/404(노랑): 클라이언트 오류
  - 500(빨강): 서버 오류
- **중요**: 200이 지속적으로 가장 높아야 함

#### ⚠️ 7. Error Rate 4xx/5xx (에러 분류)
- **사용처**: 클라이언트 오류 vs 서버 오류 구분
- **읽는 방법**:
  - 노란선(4xx): 잘못된 요청 (보통 클라이언트 문제)
  - 빨간선(5xx): 서버 내부 오류 (심각)
- **대응 사례**:
  - 4xx 급증 → API 문서 확인, 클라이언트 코드 검토
  - 5xx 급증 → 에러 로그 분석, 서버 리소스 확인

#### 📋 8. Recent Error Logs (최근 에러 로그)
- **사용처**: 발생한 오류의 상세 메시지 확인
- **읽는 방법**:
  - 가장 최근 로그가 맨 위
  - 타임스탠프, 레벨, 메시지 표시
- **상호작용**: 로그를 클릭하면 상세 정보 확장
- **필터링**: 로그 패널 상단의 필터 아이콘으로 특정 에러만 표시

### 3.4 성능 분석 패널

#### 📉 9. Latency Percentiles (응답 시간 백분위수)
- **사용처**: 응답 시간의 분포 패턴 분석
- **읽는 방법**:
  - p50(파랑): 중앙값, 사용자 절반이 경험하는 시간
  - p95(주황): 상위 5% 제외한 지점, 일반적인 느린 요청
  - p99(빨강): 상위 1%만 제외, 매우 느린 요청
- **분석 예시**:
  ```
  p50=100ms, p95=500ms, p99=2000ms
  → 대부분 빠르지만, 일부 매우 느린 요청 존재
  → 데이터베이스 느린 쿼리 의심
  ```

#### 🔥 10. Latency Heatmap (응답 시간 히트맵)
- **사용처**: 응답 시간 분포를 시각적으로 한눈에 파악
- **읽는 방법**:
  - X축: 시간 흐름
  - Y축: 응답 시간 구간
  - 색상이 진할수록 해당 구간에 많은 요청 집중
  - 주황색: 많은 요청, 흰색: 적은 요청
- **패턴 해석**:
  - 수평선 패턴(항상 같은 높이): 안정적인 성능
  - 수직 상승(갑자기 위로): 성능 저하 발생
  - 띠 모양: 여러 요청이 다양한 응답 시간 보유

#### 🔍 11. Recent Traces (최근 트레이스)
- **사용처**: 전체 요청 흐름의 세부사항 추적
- **읽는 방법**:
  - 서비스 이름, 지연시간, 스팬 수, 상태 표시
  - 초록색(아이콘): 성공 트레이스
  - 빨간색(아이콘): 오류 트레이스
- **클릭**: 트레이스를 클릭하면 상세 플레임 그래프 표시
- **예시**:
  ```
  GET /api/complex                    status: ok      duration: 659ms
  ├─ SampleController.complexOp...
  ├─ SampleService.getUserData
  └─ SampleService.getAllUsers...
  ```

### 3.5 엔드포인트 필터 사용법

상단 좌측의 **"Endpoint"** 필터로 특정 API만 분석할 수 있습니다:

```
[Endpoint ▼]  "All" 버튼 ← 현재 선택

사용 방법:
1. "All" 버튼 클릭 → 드롭다운 확인
2. 엔드포인트 선택 (예: GET /api/users)
3. 모든 패널이 자동으로 필터됨
4. 여러 개 선택 가능 (Ctrl+클릭)
```

**예시 시나리오**:
- 특정 엔드포인트의 성능만 모니터링하고 싶을 때
- GET /api/slow 선택 → 이 API의 레이턴시만 집중 분석

### 3.6 시간 범위 설정

우측 상단의 시간 선택기로 분석 기간 조정:

```
┌─────────────────────┐
│ Last 15 minutes ▼   │ ← 기본값
│ Last 1 hour         │
│ Last 6 hours        │
│ Last 24 hours       │
│ Custom range...     │
└─────────────────────┘
```

**추천 사용법**:
- **실시간 모니터링**: Last 15 minutes (자동 새로고침 10초)
- **최근 경고 분석**: Last 1 hour
- **성능 추세**: Last 6 hours
- **일일 리포트**: Last 24 hours

---

## 4. Logs & Traces Explorer

**에러 원인을 빠르게 찾기 위한 로그-트레이스 연동 대시보드입니다.**

### 4.1 대시보드 구성

| 패널 | 용도 | 데이터소스 |
|------|------|-----------|
| Log Volume by Level | 시간별 로그 발생량 | Loki |
| Log Stream | 실시간 로그 스트림 | Loki |
| Trace Search | 조건별 트레이스 검색 | Tempo |
| Error Logs with Trace Links | 에러 로그 + 트레이스 바로가기 | Loki |

### 4.2 패널별 상세 설명

#### 📊 1. Log Volume by Level (로그 발생량)
- **Y축**: 로그 수 (개수)
- **X축**: 시간
- **색상**:
  - 파랑(INFO): 정보성 로그
  - 노랑(WARN): 경고
  - 빨강(ERROR): 오류
  - 검정(DEBUG): 디버그 정보
- **읽는 방법**: ERROR 색깔이 갑자기 상승하는 구간 = 문제 발생 시점

#### 📝 2. Log Stream (로그 스트림)
- **역할**: 모든 로그를 실시간으로 확인
- **필터링**: 상단 `expr` 필터로 LogQL 쿼리 사용
- **기본 쿼리**: `{app="sample-app"} | json` (모든 로그)
- **예시 필터**:
  ```
  # ERROR 레벨만 보기
  {app="sample-app"} |= "ERROR"

  # 특정 클래스에서 나온 로그
  {app="sample-app"} |= "SampleService"

  # 특정 사용자 ID의 요청 로그
  {app="sample-app"} | json | user_id = "123"
  ```

#### 🔍 3. Trace Search (트레이스 검색)
- **역할**: 분산 트레이스 검색
- **필터 옵션**:
  - **Service**: sample-app
  - **Duration**: 예) > 500ms (느린 요청만)
  - **Status**: ok, error, unset
- **클릭 동작**: 트레이스를 클릭하면 플레임 그래프 표시
- **예시 검색**:
  ```
  Service: sample-app
  Duration: > 1000ms  ← 1초 이상 걸린 요청만
  Status: ok

  결과: 지연이 큰 성공 요청들을 확인
  ```

#### 🔴 4. Error Logs with Trace Links (에러 로그 + 트레이스)
- **역할**: 에러 로그와 해당 트레이스를 연결
- **구성**: `timestamp | log message | traceId | ...`
- **상호작용**:
  - 로그 클릭 → 상세 정보 확장
  - **traceId 링크** 클릭 → Tempo의 해당 트레이스로 이동
- **핵심 기능**: **로그와 트레이스의 양방향 연결**
  ```
  2026-01-25 10:35:42  ERROR  NullPointerException  traceId: abc123def
  ↓ (traceId 클릭)
  Tempo에서 abc123def 트레이스의 전체 흐름 표시
  ```

### 4.3 LogQL 쿼리 예제

**패널 우측 상단의 수정 버튼으로 쿼리를 직접 수정할 수 있습니다.**

```logql
# 1. 애플리케이션의 모든 로그
{app="sample-app"}

# 2. ERROR 레벨만
{app="sample-app"} |= "ERROR"

# 3. 특정 시간 이후의 로그 (타임스탐프 필터)
{app="sample-app"} | timestamp > "2026-01-25T10:00:00Z"

# 4. 레벨별 로그 수 (집계)
sum by (level) (count_over_time({app="sample-app"}[1m]))

# 5. 5분간의 ERROR 개수
count_over_time({app="sample-app"} |= "ERROR"[5m])

# 6. JSON 파싱해서 사용자별 로그 분석
{app="sample-app"} | json | username = "admin"
```

### 4.4 로그-트레이스 연동 워크플로우

실제 문제 해결 과정:

```
Step 1: Log Volume 패널에서 ERROR 증가 지점 확인
        ↓
Step 2: 시간대 클릭 → 해당 시간의 로그 필터
        ↓
Step 3: Log Stream에서 에러 메시지 확인
        ↓
Step 4: 해당 로그의 traceId 복사 (또는 클릭)
        ↓
Step 5: Tempo로 이동하여 전체 요청 흐름 분석
        → 각 마이크로서비스별 응답 시간 확인
        → 느린 구간 식별
        → 원인 파악 (DB 쿼리? 외부 API? 계산?)
```

---

## 5. Service Health

**JVM과 시스템 리소스 상태를 종합적으로 모니터링하는 대시보드입니다.**

### 5.1 4개 주요 지표 (상단)

#### 🟢 1. Uptime (가용률)
- **의미**: 지난 24시간 동안 서비스가 정상 작동한 시간 비율
- **정상 범위**: 99% 이상 (99.9% 권장)
- **계산**:
  - 99% = 하루에 약 14분 정지 허용
  - 99.9% = 하루에 약 1분 정지 허용
- **확인**:
  - 녹색: 99% 이상 (정상)
  - 노랑: 95-99% (경고)
  - 빨강: < 95% (위험)

#### 💾 2. Memory Usage (메모리 사용률)
- **의미**: JVM 힙 메모리 사용률
- **정상 범위**: < 70%
- **경고**: 70-85% (GC 압박 증가)
- **위험**: > 85% (OOM 위험)
- **대응**:
  - 85% 초과 시 → 힙 크기 증가 또는 메모리 누수 조사
  - 추세 우상향 → 메모리 누수 의심

#### 🧵 3. Live Threads (실행 중인 스레드)
- **의미**: 현재 활성 스레드 수
- **정상 범위**: 보통 50~200 (설정에 따라 다름)
- **급증**: 요청 폭증 또는 스레드 누수 의심
- **감소**: 요청 감소 또는 교착 상태 의심
- **트렌드**: 평탄한 선이 정상, 지속적 상승은 문제 신호

#### 📊 4. Active HTTP Requests (활성 HTTP 요청)
- **의미**: 현재 처리 중인 HTTP 요청 수
- **읽는 방법**: RPS와 평균 응답 시간으로 계산
  - 예: 100 RPS × 0.5초 응답 시간 = 약 50개 요청 동시 처리
- **급증**: 요청이 쌓이는 중 (응답 시간 증가 의심)

### 5.2 시스템 리소스 패널

#### 🔥 CPU Usage (CPU 사용률)
- **구성**: 프로세스 CPU vs 시스템 전체 CPU
- **읽는 방법**:
  - 프로세스 CPU(파랑): Spring Boot 애플리케이션만
  - 시스템 CPU(주황): 전체 시스템
- **정상**: 프로세스 CPU < 50%, 시스템 CPU < 70%
- **경고**: 프로세스 CPU > 70% (계산 집약적인 작업)
- **팁**: 애플리케이션이 CPU를 많이 쓰는데 시스템 CPU는 낮으면 다른 앱의 부하

#### 💾 Heap Memory (힙 메모리)
- **구성**: Used (사용 중) vs Max (최대 크기)
- **정상 패턴**: 톱날 모양 (GC 후 정기적 상승)
- **문제 패턴**:
  - 일직선 상승 → 메모리 누수 (해결 필요)
  - Max에 도달 → OOM 직전 (즉시 대응)
- **읽는 방법**:
  - 녹색(Used): 현재 사용 중인 메모리
  - 빨강(Max): 할당 가능한 최대 메모리
  - 두 선이 가까워지면 위험

#### 🗑️ GC Pause Time (가비지 컬렉션 일시정지)
- **의미**: GC로 인한 애플리케이션 정지 시간
- **정상**: < 100ms (사용자가 느끼지 못할 수준)
- **경고**: 100-500ms (느리게 보일 수 있음)
- **위험**: > 500ms (응답 시간 저하)
- **급증 의미**: 메모리 압박 증가 → GC 빈도 증가
- **대응**:
  - GC 시간 증가 → 메모리 리뷰 필요
  - 메모리 구성 최적화 또는 힙 크기 증가

#### 🧵 Thread States (스레드 상태)
- **3가지 선**:
  - Live(파랑): 현재 활성 스레드
  - Daemon(주황): 백그라운드 스레드
  - Peak(빨강): 최대 스레드 수
- **정상 패턴**: Live는 안정적, Peak는 거의 변화 없음
- **문제 신호**:
  - Live가 지속 상승 → 스레드 누수
  - Peak 급상승 → 요청 폭증
  - Live가 Peak에 근접 → 스레드 풀 한계 도달

### 5.3 임계값 이해

각 패널의 색상 변화를 통한 상태 인지:

```
🟢 GREEN   정상      권장 범위 내, 조치 불필요
🟡 YELLOW  경고      임계값 근처, 모니터링 강화
🔴 RED     위험      즉시 조치 필요
```

### 5.4 Service Health 활용 사례

**매일 아침 확인 체크리스트:**
1. Uptime 확인 → 야간 중단 확인
2. Memory Usage 추세 → 메모리 누수 조사
3. CPU Usage 확인 → 야간 배치 작업 확인
4. GC Pause Time → GC 압박도 평가

---

## 6. 실전 사용 시나리오

### 시나리오 1: 지연시간 급증 조사

**상황**: 사용자가 응답이 느려졌다고 보고함

**해결 단계**:

```
Step 1: RED Metrics Overview 접속
        → p99 Latency 게이지에서 350ms에서 1.5s로 증가 확인

Step 2: Latency Percentiles 패널에서 시간대 클릭
        → 10:30부터 지연 시작 확인

Step 3: Endpoint 필터로 엔드포인트별 분석
        GET /api/complex 선택
        → 이 API의 p99이 2s 임을 확인

Step 4: Latency Heatmap으로 분포 분석
        → 대부분 200ms이지만 일부 3s 이상
        → 특정 데이터만 느림 의심

Step 5: Recent Traces 패널에서 느린 트레이스 클릭
        → 플레임 그래프에서 느린 스팬 식별
        → SampleService.getAllUsersWithStats 171ms
        → SELECT * FROM users 114ms ← DB 쿼리 느림

Step 6: 데이터베이스 팀에 보고
        users 테이블 쿼리 성능 저하
        → 테이블 스캔 → 인덱스 추가 제안
```

**핵심**: 대시보드 → 특정 엔드포인트 → 트레이스 → 느린 스팬 순서로 좁혀나감

### 시나리오 2: 에러 원인 추적

**상황**: 에러율이 5%로 급증함

**해결 단계**:

```
Step 1: RED Metrics Overview 접속
        → Error Rate % 게이지에서 5% 확인
        → Recent Error Logs 패널 확인

Step 2: Error Logs 패널에서 에러 메시지 읽기
        "TypeError: Cannot read property 'id' of null"
        "2026-01-25 10:35:42.123"

Step 3: Logs & Traces Explorer 접속
        → Log Stream에서 해당 시간의 ERROR 로그 필터
        {app="sample-app"} |= "TypeError" 쿼리
        → 에러 메시지와 traceId 확인

Step 4: traceId 클릭하여 Tempo로 이동
        → 플레임 그래프에서 예외 발생 지점 확인
        → SampleService.processUserData에서 오류

Step 5: 소스 코드 검토
        getUserData() 응답이 null인 경우 처리 안 됨
        → null check 추가

Step 6: 수정 후 배포
        → 에러율 다시 0%로 정상화 확인
```

**핵심**: 에러율 증가 → 에러 로그 → 트레이스 → 소스 코드 추적

### 시나리오 3: 메모리 누수 의심

**상황**: 메모리 사용률이 매일 증가하고 있음

**해결 단계**:

```
Step 1: Service Health 대시보드 접속

Step 2: Heap Memory 패널을 Last 7 days로 변경
        → 우상향 일직선 패턴 확인
        → 정상적인 톱날(GC) 패턴이 아님
        → 메모리 누수 의심 강함

Step 3: Memory Usage 패널에서 추세 분석
        → 월요일 50% → 금요일 95%
        → 주중 누적 증가 (재시작으로 초기화되지 않음)

Step 4: 데이터베이스 연결/캐시 검토
        → Session 저장소가 계속 커지나?
        → 캐시가 만료 설정이 없나?

Step 5: Grafana 대시보드 외 추가 분석 필요
        → JVM 힙 덤프 수집 (jmap)
        → 메모리 프로파일러 실행 (JProfiler, YourKit)

Step 6: 원인 파악 및 수정
        예: Cache.put() 하지만 evict() 호출 안 함
        → LRU 캐시 정책 추가

Step 7: 배포 후 모니터링
        → Heap Memory 패턴이 톱날 모양으로 정상화 확인
```

**핵심**: 메모리 일직선 증가 = 누수 신호, 그 후 심화 분석 필요

### 시나리오 4: 야간 배치 작업 모니터링

**상황**: 매일 밤 12시 배치 작업 실행

**해결 단계**:

```
Step 1: RED Metrics Overview 접속
        Last 24 hours 선택

Step 2: Request Rate by Endpoint 패널에서
        22:00-01:00 사이에 POST /api/batch 급증 확인

Step 3: 동시 Request Rate by Endpoint 패널에서
        → GET /api/users 요청이 줄어들었는지 확인
        → 배치 작업이 부하를 빼앗는지 검증

Step 4: CPU Usage 패널에서 같은 시간대 확인
        → 22:00-01:00 CPU 80% 유지
        → 다른 시간대는 20% 평균
        → 배치 작업이 CPU를 많이 사용함

Step 5: 성능 개선 판단
        경우1) 야간 사용자 적으면 괜찮음 → 유지
        경우2) 야간에도 많은 사용자 → 배치 분산 필요

Step 6: Latency Percentiles 확인
        22:00-01:00 p99 500ms vs 일반 시간 p99 100ms
        → 배치로 인한 응답 저하 확인
```

**핵심**: 시간별 패턴 분석으로 배치 영향도 평가

---

## 7. 팁 & 활용법

### 7.1 효율적인 대시보드 탐색

#### 시간 단위별 최적 선택
```
실시간 트러블슈팅        → Last 15 minutes (자동 새로고침)
경고 발생 즉시 분석      → Last 15 minutes ~ 1 hour
일일 성능 리뷰           → Last 24 hours
주간 추세 분석           → Last 7 days
월간 용량 계획           → Last 30 days
```

#### 대시보드 간 이동 팁
```
패널 우측 상단의 "외부 링크" 아이콘
    ↓ 클릭
같은 시간대로 다른 대시보드 전환
```

### 7.2 패널 커스터마이징

#### 패널 수정
```
패널 우측 상단 "Edit" 버튼
    ↓
PromQL/LogQL 쿼리 수정
    ↓
임계값, 색상, 범위 조정
    ↓
"Save" 클릭
```

#### 새 패널 추가
```
대시보드 상단 "+ Add panel" 클릭
    ↓
데이터소스 선택 (Prometheus, Loki, Tempo)
    ↓
쿼리 입력
    ↓
시각화 유형 선택 (그래프, 테이블, 게이지)
    ↓
"Save" 클릭
```

### 7.3 알림(Alert) 설정하기

#### 임계값 기반 알림 설정
```
1. 대시보드 상단 "Alert" 메뉴
2. "New Alert Rule" 클릭
3. 쿼리 설정
   예: Error Rate > 1%
4. 평가 기간 설정
   예: 5분 동안 지속되면 알림
5. 알림 채널 설정
   예: Slack, 이메일
6. "Save" 클릭
```

### 7.4 주요 PromQL 쿼리

**복사-붙여넣기 가능한 유용한 쿼리들:**

```promql
# 1. 5분간의 요청 처리량
rate(http_server_requests_seconds_count[5m])

# 2. 에러율 (백분율)
(sum(rate(http_server_requests_seconds_count{status=~"[45].."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m]))) * 100

# 3. 응답시간 p95
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))

# 4. JVM 메모리 사용률 (%)
(jvm_memory_used_bytes / jvm_memory_max_bytes) * 100

# 5. CPU 사용률 (%)
process_cpu_usage * 100

# 6. 스레드 풀 활용률
jvm_threads_live_threads / jvm_threads_peak_threads * 100

# 7. 초당 요청 수 (엔드포인트별)
sum(rate(http_server_requests_seconds_count[1m])) by (uri)

# 8. 응답 시간 표준편차 (성능 편차)
stddev(rate(http_server_requests_seconds_bucket[5m])) by (le)
```

### 7.5 대시보드 공유 및 내보내기

#### 링크 공유
```
대시보드 우상단 "Share" 버튼
    ↓
URL 복사 또는 특정 시간대 임베딩
```

#### JSON 내보내기
```
대시보드 우상단 설정(톱니) 아이콘
    ↓
"Export dashboard as JSON"
    ↓
JSON 저장 (다른 Grafana로 임포트 가능)
```

---

## 8. 트러블슈팅

### 문제 1: 대시보드가 로드되지 않음

**증상**: "Dashboard not found" 또는 빈 화면

**원인 & 해결**:

```
원인 1: 데이터소스 연결 끊김
┗ 해결:
  Settings > Data Sources
  각 데이터소스의 상태 확인
  URL 및 포트 재확인

원인 2: Grafana 프로비저닝 파일 누락
┗ 해결:
  docker/grafana/provisioning/dashboards/ 폴더 확인
  red-metrics-dashboard.json 등 파일 존재 확인
  파일 JSON 문법 유효성 검사 (jsonlint.com)

원인 3: 파일 권한 문제
┗ 해결:
  docker-compose에서 Grafana 서비스 재시작
  docker-compose down && docker-compose up -d
```

### 문제 2: 패널에 데이터가 없음

**증상**: "No data" 또는 빈 패널

**원인 & 해결**:

```
원인 1: 애플리케이션이 메트릭을 생성 안 함
┗ 해결:
  1. 샘플 앱이 실행 중인지 확인
     docker-compose ps | grep spring-boot-app
  2. 애플리케이션에 요청 전송
     curl http://localhost:8080/api/hello
  3. Prometheus에서 직접 확인
     http://localhost:9090/graph
     쿼리: http_server_requests_seconds_count
  4. 메트릭이 없으면 애플리케이션 로그 확인
     docker logs spring-boot-app

원인 2: 메트릭 이름 불일치
┗ 해결:
  1. Prometheus UI에서 사용 가능한 메트릭 확인
     http://localhost:9090/api/v1/label/__name__/values
  2. 쿼리의 메트릭 이름 수정

원인 3: 필터가 너무 엄격함
┗ 해결:
  엔드포인트 필터 전체 선택 시도
  시간 범위 확대 (Last 15 minutes → Last 1 hour)
  URI 레이블 존재 확인 (예: /api/hello가 정말 존재?)
```

### 문제 3: 로그가 안 보임 (Logs 패널)

**증상**: "No logs" 또는 빈 로그 스트림

**원인 & 해결**:

```
원인 1: Loki에 로그가 저장 안 됨
┗ 해결:
  1. Loki 상태 확인
     docker-compose ps | grep loki
  2. Loki 로그 확인
     docker logs loki
  3. 로그 백엔드 설정 확인
     application.yml에서 loki4j 설정 확인
  4. 애플리케이션에 ERROR 생성
     curl http://localhost:8080/api/error (기대: 500 에러)
  5. 다시 Logs 패널 새로고침

원인 2: LogQL 쿼리 오류
┗ 해결:
  1. 쿼리 문법 확인
     {app="sample-app"} ← 레이블 정확성 확인
  2. 간단한 쿼리부터 시작
     {app="sample-app"}
  3. Loki Explore에서 디버깅
     좌측 Explore > Loki 선택

원인 3: 시간대 오류
┗ 해결:
  Last 15 minutes 대신 Last 24 hours 선택
  또는 Custom range에서 수동 설정
```

### 문제 4: 트레이스가 안 보임 (Traces 패널)

**증상**: "No traces" 또는 빈 트레이스 패널

**원인 & 해결**:

```
원인 1: Tempo에 트레이스 데이터 없음
┗ 해결:
  1. Tempo 상태 확인
     docker-compose ps | grep tempo
  2. OpenTelemetry Agent 활성화 확인
     Dockerfile의 OTEL_JAVAAGENT 설정 확인
  3. 애플리케이션 요청 생성
     for i in {1..10}; do curl http://localhost:8080/api/hello; done
  4. 다시 Traces 패널 새로고침

원인 2: TraceQL 쿼리 문법 오류
┗ 해결:
  1. 쿼리 간단히 시작
     { resource.service.name = "sample-app" }
  2. 필터 하나씩 추가
     { resource.service.name = "sample-app" && duration > 100ms }
  3. Tempo UI에서 직접 테스트
     http://localhost:3200
```

### 문제 5: 색상 임계값이 안 변함

**증상**: 패널이 항상 같은 색상 (초록색)

**원인 & 해결**:

```
원인: 임계값이 잘못 설정됨
┗ 해결:
  1. 패널 Edit 클릭
  2. 우측 "Field Config" 탭
  3. "Thresholds" 섹션 확인
  4. 실제 데이터 범위에 맞게 값 조정
     예: Error Rate는 0-100% 범위이므로
     Step 1: 0 (green)
     Step 2: 1 (yellow) ← 1% 오류율
     Step 3: 5 (red) ← 5% 오류율
  5. "Save" 클릭
```

### 문제 6: 매우 느린 쿼리 성능

**증상**: 대시보드 로드에 10초 이상 걸림

**원인 & 해결**:

```
원인 1: PromQL 쿼리가 범위를 크게 설정
┗ 해결:
  1. 패널 Edit 클릭
  2. PromQL 쿼리 확인
  3. rate() 함수의 시간 범위 축소
     [30m] → [5m]로 변경
  4. by (uri) 같은 집계에서 레이블 수 제한
  5. "Save" 클릭

원인 2: Prometheus 데이터가 너무 많음
┗ 해결:
  1. Prometheus 저장 기간 확인
     docker/prometheus/prometheus.yml 의 retention 설정
     storage.tsdb.retention.time=15d 같은 설정 확인
  2. 저장 기간 단축
     retention 줄이기 (예: 7d)
  3. 재시작
     docker-compose restart prometheus
```

### 문제 7: 메모리/CPU 급증 후 복구 안 됨

**증상**: Memory Usage가 95% 이상 유지, GC Pause Time 급증

**원인 & 해결**:

```
Step 1: 즉시 조치
┗ 애플리케이션 재시작
  docker-compose restart spring-boot-app

Step 2: 원인 분석
┗ 메모리 누수 의심
  1. Heap Memory 그래프를 Last 7 days로 봄
  2. 일직선 상승 패턴인지 확인
  3. 톱날 패턴(정상)이 아니면 누수 의심

Step 3: 더 깊은 분석 필요
┗ Grafana 외부 도구 사용
  1. 힙 덤프 수집
     docker exec spring-boot-app jmap -dump:live,format=b,file=/tmp/heap.bin 1
  2. 메모리 프로파일러 실행
     jhat, JProfiler, YourKit 등
  3. 누수 객체 식별

Step 4: 수정 및 재배포
┗ 소스 코드 수정 후 새 이미지 빌드
```

### 빠른 진단 체크리스트

```
□ Grafana 접속 가능? (http://localhost:3000)
□ Data Sources 모두 green "Connected"?
□ 샘플 앱 실행 중? (docker-compose ps)
□ 샘플 앱에 요청 보냈나? (curl http://localhost:8080/api/hello)
□ Prometheus에 메트릭 있나? (http://localhost:9090)
□ Loki에 로그 있나? (http://localhost:3100)
□ Tempo에 트레이스 있나? (http://localhost:3200)
□ 대시보드 파일이 docker/grafana/provisioning/dashboards/ 에 있나?
□ 패널 시간 범위를 Last 24 hours로 설정했나?
□ 자동 새로고침이 켜져 있나? (우상단 refresh 아이콘)
```

---

## 추가 자료

### 외부 참고 자료
- [Grafana 공식 문서](https://grafana.com/docs/grafana/latest/)
- [PromQL 쿼리 언어](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [LogQL 로그 쿼리](https://grafana.com/docs/loki/latest/logql/)
- [TraceQL 트레이스 쿼리](https://grafana.com/docs/tempo/latest/traceql/)
- [RED 방법론](https://grafana.com/docs/grafana-cloud/monitor-infrastructure/metrics/metrics-analysis/red-method/)

### 다른 가이드
- [설정 가이드](./SETUP_GUIDE.md) - LGTM 스택 설치 방법
- [아키텍처](./ARCHITECTURE.md) - 시스템 구조 상세
- [사용 가이드](./USAGE.md) - 애플리케이션 커스터마이징

---

**마지막 업데이트**: 2026-01-25
**작성자**: Spring Boot LGTM 프로젝트 팀
