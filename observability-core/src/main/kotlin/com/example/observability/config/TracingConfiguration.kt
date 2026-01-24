package com.example.observability.config

import io.micrometer.tracing.Tracer
import io.opentelemetry.sdk.resources.Resource
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Tracing configuration that relies on Spring Boot 3.4 auto-configuration.
 * DO NOT define manual OpenTelemetry SDK beans - use management.otlp.tracing.endpoint property instead.
 */
@Configuration
@ConditionalOnClass(Tracer::class, OtlpAutoConfiguration::class)
class TracingConfiguration {

    /**
     * Customize OpenTelemetry resource attributes.
     * Spring Boot auto-config handles SDK, exporter, and span processor.
     */
    @Bean
    fun otelResourceCustomizer(
        @Value("\${spring.application.name:unknown}") appName: String,
        @Value("\${observability.service.namespace:spring-boot-lgtm}") namespace: String
    ): io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider {
        return io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider {
            Resource.builder()
                .put("service.namespace", namespace)
                .put("deployment.environment", System.getenv("ENV") ?: "local")
                .build()
        }
    }
}
