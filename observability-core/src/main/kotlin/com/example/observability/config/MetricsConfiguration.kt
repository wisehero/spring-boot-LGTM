package com.example.observability.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.config.MeterFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MetricsConfiguration {

    @Bean
    fun metricsCommonTags(
        @Value("\${spring.application.name:unknown}") appName: String
    ): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry ->
            registry.config()
                .commonTags(
                    "application", appName,
                    "environment", System.getenv("ENV") ?: "local"
                )
        }
    }

    @Bean
    fun meterFilter(): MeterFilter {
        return MeterFilter.deny { id ->
            // 메모리 문제를 일으킬 수 있는 높은 카디널리티 메트릭 거부
            val name = id.name
            name.startsWith("jvm.threads.") && id.getTag("state") != null &&
                id.tags.count() > 3
        }
    }
}
