package com.example.observability.config

import com.example.observability.aspect.TracingAspect
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Import

@AutoConfiguration
@Import(
    TracingConfiguration::class,
    MetricsConfiguration::class,
    LoggingConfiguration::class,
    TracingAspect::class
)
class ObservabilityAutoConfiguration
