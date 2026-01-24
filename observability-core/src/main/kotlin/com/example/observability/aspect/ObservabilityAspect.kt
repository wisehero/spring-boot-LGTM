package com.example.observability.aspect

import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.aop.ObservedAspect
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 메서드 레벨 관찰성을 위한 @Observed 어노테이션 지원을 활성화합니다.
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
