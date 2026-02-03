#!/bin/bash

# Spring Boot LGTM 부하 생성 스크립트
# 사용법: ./load-generator.sh [간격(초)] [반복횟수]
# 예: ./load-generator.sh 5 100  (5초 간격으로 100회 반복)
# 예: ./load-generator.sh 3       (3초 간격으로 무한 반복)

INTERVAL=${1:-5}  # 기본값: 5초
MAX_COUNT=${2:-0}  # 기본값: 0 (무한)

BASE_URL="http://localhost:8080"
COUNT=0

echo "=========================================="
echo "  Spring Boot LGTM 부하 생성기"
echo "=========================================="
echo "대상: $BASE_URL"
echo "간격: ${INTERVAL}초"
echo "반복: $([ $MAX_COUNT -eq 0 ] && echo '무한' || echo "${MAX_COUNT}회")"
echo "중지: Ctrl+C"
echo "=========================================="
echo ""

# 엔드포인트 목록
ENDPOINTS=(
    "/api/hello"
    "/api/hello"
    "/api/hello"
    "/api/chain"
    "/api/users"
    "/api/users/user-1"
    "/api/complex"
    "/api/slow"
    "/api/error"
)

while true; do
    COUNT=$((COUNT + 1))
    TIMESTAMP=$(date '+%H:%M:%S')

    # 랜덤 엔드포인트 선택
    RANDOM_INDEX=$((RANDOM % ${#ENDPOINTS[@]}))
    ENDPOINT=${ENDPOINTS[$RANDOM_INDEX]}

    # 요청 전송
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code} %{time_total}s" "${BASE_URL}${ENDPOINT}" 2>/dev/null)
    STATUS=$(echo $RESPONSE | awk '{print $1}')
    TIME=$(echo $RESPONSE | awk '{print $2}')

    # 결과 출력 (색상)
    if [ "$STATUS" == "200" ]; then
        echo -e "[$TIMESTAMP] #$COUNT ${ENDPOINT} → \033[32m${STATUS}\033[0m (${TIME})"
    else
        echo -e "[$TIMESTAMP] #$COUNT ${ENDPOINT} → \033[31m${STATUS}\033[0m (${TIME})"
    fi

    # 종료 조건 확인
    if [ $MAX_COUNT -ne 0 ] && [ $COUNT -ge $MAX_COUNT ]; then
        echo ""
        echo "완료: ${COUNT}회 요청 전송"
        break
    fi

    sleep $INTERVAL
done
