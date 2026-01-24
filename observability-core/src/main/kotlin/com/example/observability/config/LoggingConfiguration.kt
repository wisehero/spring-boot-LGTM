package com.example.observability.config

import org.springframework.context.annotation.Configuration

/**
 * 로깅 구성 플레이스홀더
 * 실제 로깅은 logback-spring-observability.xml을 통해 구성되며
 * trace 상관관계가 포함된 Loki4j appender를 포함합니다.
 */
@Configuration
class LoggingConfiguration
