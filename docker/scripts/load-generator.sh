#!/bin/bash

# Spring Boot LGTM 마이크로서비스 부하 생성 스크립트
# 사용법: ./load-generator.sh [간격(초)] [반복횟수]
# 예: ./load-generator.sh 5 100  (5초 간격으로 100회 반복)
# 예: ./load-generator.sh 3       (3초 간격으로 무한 반복)

INTERVAL=${1:-5}  # 기본값: 5초
MAX_COUNT=${2:-0}  # 기본값: 0 (무한)

ORDER_URL="http://localhost:8080"
PRODUCT_URL="http://localhost:8081"
PAYMENT_URL="http://localhost:8082"

COUNT=0

echo "=========================================="
echo "  Spring Boot LGTM 부하 생성기 (마이크로서비스)"
echo "=========================================="
echo "Order Service:   $ORDER_URL"
echo "Product Service: $PRODUCT_URL"
echo "Payment Service: $PAYMENT_URL"
echo "간격: ${INTERVAL}초"
echo "반복: $([ $MAX_COUNT -eq 0 ] && echo '무한' || echo "${MAX_COUNT}회")"
echo "중지: Ctrl+C"
echo "=========================================="
echo ""

# 요청 타입 목록 (GET/POST)
request_types=(
    "GET:${PRODUCT_URL}/api/products"
    "GET:${PRODUCT_URL}/api/products/1"
    "GET:${PRODUCT_URL}/api/products/2"
    "GET:${PRODUCT_URL}/api/products/1/stock?quantity=1"
    "GET:${ORDER_URL}/api/orders"
    "POST:${ORDER_URL}/api/orders"
    "POST:${ORDER_URL}/api/orders"
    "POST:${ORDER_URL}/api/orders"
    "GET:${PAYMENT_URL}/api/payments/1"
)

# 주문 요청 바디 (랜덤 상품 선택)
order_bodies=(
    '{"productId":1,"quantity":1}'
    '{"productId":2,"quantity":2}'
    '{"productId":3,"quantity":1}'
    '{"productId":4,"quantity":3}'
    '{"productId":5,"quantity":1}'
)

while true; do
    COUNT=$((COUNT + 1))
    TIMESTAMP=$(date '+%H:%M:%S')

    # 랜덤 요청 선택
    RANDOM_INDEX=$((RANDOM % ${#request_types[@]}))
    REQUEST=${request_types[$RANDOM_INDEX]}
    METHOD=$(echo "$REQUEST" | cut -d: -f1)
    URL=$(echo "$REQUEST" | cut -d: -f2-)

    if [ "$METHOD" == "POST" ]; then
        # 랜덤 주문 바디 선택
        BODY_INDEX=$((RANDOM % ${#order_bodies[@]}))
        BODY=${order_bodies[$BODY_INDEX]}
        RESPONSE=$(curl -s -o /dev/null -w "%{http_code} %{time_total}s" \
            -X POST -H "Content-Type: application/json" -d "$BODY" "$URL" 2>/dev/null)
        DISPLAY="${METHOD} ${URL##*://*/} ($BODY)"
    else
        RESPONSE=$(curl -s -o /dev/null -w "%{http_code} %{time_total}s" "$URL" 2>/dev/null)
        DISPLAY="${METHOD} ${URL##*://*/}"
    fi

    STATUS=$(echo $RESPONSE | awk '{print $1}')
    TIME=$(echo $RESPONSE | awk '{print $2}')

    # 결과 출력 (색상)
    if [ "$STATUS" == "200" ]; then
        echo -e "[$TIMESTAMP] #$COUNT ${DISPLAY} → \033[32m${STATUS}\033[0m (${TIME})"
    elif [ "$STATUS" == "409" ]; then
        echo -e "[$TIMESTAMP] #$COUNT ${DISPLAY} → \033[33m${STATUS}\033[0m (${TIME})"
    else
        echo -e "[$TIMESTAMP] #$COUNT ${DISPLAY} → \033[31m${STATUS}\033[0m (${TIME})"
    fi

    # 종료 조건 확인
    if [ $MAX_COUNT -ne 0 ] && [ $COUNT -ge $MAX_COUNT ]; then
        echo ""
        echo "완료: ${COUNT}회 요청 전송"
        break
    fi

    sleep $INTERVAL
done
