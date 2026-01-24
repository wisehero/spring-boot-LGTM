package com.example.observability.aspect

import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.aop.ObservedAspect
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Enables @Observed annotation support for method-level observability.
 */
@Configuration
@ConditionalOnClass(ObservedAspect::class)
class ObservabilityAspect {

    @Bean
    @ConditionalOnBean(ObservationRegistry::class)
    fun observedAspect(observationRegistry: ObservationRegistry): ObservedAspect {
        return ObservedAspect(observationRegistry)
    }
}
