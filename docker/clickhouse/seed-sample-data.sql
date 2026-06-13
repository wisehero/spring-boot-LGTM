-- ClickHouse 샘플 데이터 시더 (데모용)
-- OTel Collector가 생성하는 것과 동일한 otel_traces / otel_logs 스키마를 만들고,
-- order→product→payment 흐름을 모사한 트레이스·로그를 주입한다(약 12% 에러).
-- Collector를 띄울 수 없는 환경에서 ClickHouse 대시보드를 미리 보기 위한 용도.
--   docker exec -i clickhouse clickhouse-client --password otel --multiquery < seed-sample-data.sql

CREATE DATABASE IF NOT EXISTS otel;

CREATE TABLE IF NOT EXISTS otel.otel_traces
(
    Timestamp DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    TraceId String CODEC(ZSTD(1)),
    SpanId String CODEC(ZSTD(1)),
    ParentSpanId String CODEC(ZSTD(1)),
    SpanName LowCardinality(String) CODEC(ZSTD(1)),
    SpanKind LowCardinality(String) CODEC(ZSTD(1)),
    ServiceName LowCardinality(String) CODEC(ZSTD(1)),
    Duration Int64 CODEC(ZSTD(1)),
    StatusCode LowCardinality(String) CODEC(ZSTD(1)),
    StatusMessage String CODEC(ZSTD(1)),
    ResourceAttributes Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    SpanAttributes Map(LowCardinality(String), String) CODEC(ZSTD(1))
)
ENGINE = MergeTree
PARTITION BY toDate(Timestamp)
ORDER BY (ServiceName, SpanName, toUnixTimestamp(Timestamp))
TTL toDateTime(Timestamp) + toIntervalDay(3);

CREATE TABLE IF NOT EXISTS otel.otel_logs
(
    Timestamp DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    TraceId String CODEC(ZSTD(1)),
    SpanId String CODEC(ZSTD(1)),
    SeverityText LowCardinality(String) CODEC(ZSTD(1)),
    SeverityNumber Int32 CODEC(ZSTD(1)),
    ServiceName LowCardinality(String) CODEC(ZSTD(1)),
    Body String CODEC(ZSTD(1)),
    ResourceAttributes Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    LogAttributes Map(LowCardinality(String), String) CODEC(ZSTD(1))
)
ENGINE = MergeTree
PARTITION BY toDate(Timestamp)
ORDER BY (ServiceName, SeverityText, toUnixTimestamp(Timestamp))
TTL toDateTime(Timestamp) + toIntervalDay(3);

TRUNCATE TABLE otel.otel_traces;
TRUNCATE TABLE otel.otel_logs;

-- 250개 주문(트레이스), 각 7개 span. ts/isErr/traceId는 주문번호로 결정적 → 로그와 키 정합.
INSERT INTO otel.otel_traces
(Timestamp, TraceId, SpanId, ParentSpanId, SpanName, SpanKind, ServiceName, Duration, StatusCode, StatusMessage, ResourceAttributes, SpanAttributes)
SELECT
    o.ts + toIntervalMillisecond(toInt64(tpl.4)) AS Timestamp,
    o.traceId AS TraceId,
    substring(lower(hex(MD5(concat(toString(o.n), tpl.2)))), 1, 16) AS SpanId,
    if(tpl.1 = 'order-service' AND tpl.3 = 'Server', '', substring(lower(hex(MD5(concat(toString(o.n), 'POST /api/orders')))), 1, 16)) AS ParentSpanId,
    tpl.2 AS SpanName,
    tpl.3 AS SpanKind,
    tpl.1 AS ServiceName,
    toInt64((tpl.5 + (rand() % tpl.6)) * 1000000) AS Duration,
    if(o.isErr AND tpl.1 = 'payment-service' AND tpl.2 = 'POST /api/payments', 'Error', 'Ok') AS StatusCode,
    if(o.isErr AND tpl.1 = 'payment-service' AND tpl.2 = 'POST /api/payments', 'payment rejected', '') AS StatusMessage,
    map('service.name', tpl.1) AS ResourceAttributes,
    map('order.id', toString(o.n)) AS SpanAttributes
FROM
(
    SELECT
        number AS n,
        now() - toIntervalSecond(toInt64(cityHash64(number) % 3600)) AS ts,
        lower(hex(MD5(toString(number)))) AS traceId,
        (cityHash64(number + 777) % 100) < 12 AS isErr
    FROM numbers(250)
) AS o
ARRAY JOIN
[
    ('order-service', 'POST /api/orders', 'Server', 0, 250, 200),
    ('order-service', 'OrderService.createOrder', 'Internal', 1, 245, 180),
    ('product-service', 'GET /api/products/{id}', 'Server', 5, 4, 10),
    ('product-service', 'ProductService.getProduct', 'Internal', 7, 2, 6),
    ('payment-service', 'POST /api/payments', 'Server', 20, 100, 400),
    ('payment-service', 'PaymentService.processPayment', 'Internal', 22, 100, 390),
    ('product-service', 'PATCH /api/products/{id}/stock', 'Server', 200, 6, 12)
] AS tpl;

-- 공통 INFO 로그 (모든 주문)
INSERT INTO otel.otel_logs
(Timestamp, TraceId, SpanId, SeverityText, SeverityNumber, ServiceName, Body, ResourceAttributes, LogAttributes)
SELECT
    o.ts + toIntervalMillisecond(toInt64(tpl.4)) AS Timestamp,
    o.traceId,
    substring(lower(hex(MD5(concat(toString(o.n), tpl.2)))), 1, 16),
    tpl.1, tpl.5, tpl.3, tpl.2,
    map('service.name', tpl.3), map('order.id', toString(o.n))
FROM
(
    SELECT number AS n,
           now() - toIntervalSecond(toInt64(cityHash64(number) % 3600)) AS ts,
           lower(hex(MD5(toString(number)))) AS traceId
    FROM numbers(250)
) AS o
ARRAY JOIN
[
    ('INFO', '주문 생성 시작: productId={}, quantity={}', 'order-service', 0, 9),
    ('INFO', '상품 조회 완료: name={}, price={}', 'order-service', 8, 9),
    ('INFO', '재고 확인: productId={}, quantity={}', 'product-service', 12, 9),
    ('INFO', '결제 처리 시작: orderId={}, amount={}', 'payment-service', 22, 9)
] AS tpl;

-- 정상 주문 로그
INSERT INTO otel.otel_logs
(Timestamp, TraceId, SpanId, SeverityText, SeverityNumber, ServiceName, Body, ResourceAttributes, LogAttributes)
SELECT
    o.ts + toIntervalMillisecond(toInt64(tpl.4)) AS Timestamp,
    o.traceId,
    substring(lower(hex(MD5(concat(toString(o.n), tpl.2)))), 1, 16),
    tpl.1, tpl.5, tpl.3, tpl.2,
    map('service.name', tpl.3), map('order.id', toString(o.n))
FROM
(
    SELECT number AS n,
           now() - toIntervalSecond(toInt64(cityHash64(number) % 3600)) AS ts,
           lower(hex(MD5(toString(number)))) AS traceId
    FROM numbers(250)
    WHERE (cityHash64(number + 777) % 100) >= 12
) AS o
ARRAY JOIN
[
    ('INFO', '결제 승인됨: orderId={}', 'payment-service', 320, 9),
    ('INFO', '결제 승인됨: orderId={}, 재고 차감 진행', 'order-service', 330, 9),
    ('INFO', '재고 차감: productId={}, quantity={}', 'product-service', 345, 9),
    ('INFO', '주문 확정: id={}, status=CONFIRMED', 'order-service', 360, 9)
] AS tpl;

-- 에러 주문 로그 (결제 거부 → 보상)
INSERT INTO otel.otel_logs
(Timestamp, TraceId, SpanId, SeverityText, SeverityNumber, ServiceName, Body, ResourceAttributes, LogAttributes)
SELECT
    o.ts + toIntervalMillisecond(toInt64(tpl.4)) AS Timestamp,
    o.traceId,
    substring(lower(hex(MD5(concat(toString(o.n), tpl.2)))), 1, 16),
    tpl.1, tpl.5, tpl.3, tpl.2,
    map('service.name', tpl.3), map('order.id', toString(o.n))
FROM
(
    SELECT number AS n,
           now() - toIntervalSecond(toInt64(cityHash64(number) % 3600)) AS ts,
           lower(hex(MD5(toString(number)))) AS traceId
    FROM numbers(250)
    WHERE (cityHash64(number + 777) % 100) < 12
) AS o
ARRAY JOIN
[
    ('WARN', '결제 거부됨: orderId={}', 'payment-service', 320, 13),
    ('ERROR', '재고 차감 실패 → 결제 보상(취소) 진행: orderId={}, paymentId={}', 'order-service', 340, 17),
    ('WARN', '결제 거부됨: orderId={}', 'order-service', 345, 13),
    ('INFO', '주문 확정: id={}, status=PAYMENT_FAILED', 'order-service', 360, 9)
] AS tpl;
