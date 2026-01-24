package com.example.observability.config

import org.springframework.context.annotation.Configuration

/**
 * Java Agent 모드를 위한 Tracing 구성
 *
 * OpenTelemetry Java Agent 사용 시:
 * - Agent가 OTEL_* 환경 변수를 통해 SDK 구성을 처리합니다
 * - Agent가 구성된 엔드포인트(Tempo)로 trace를 내보냅니다
 * - TracingAspect는 GlobalOpenTelemetry(Agent가 설정)를 사용하여 span을 생성합니다
 *
 * 추가 Bean 구성이 필요하지 않습니다 - Agent가 모든 것을 관리합니다.
 */
@Configuration
class TracingConfiguration
