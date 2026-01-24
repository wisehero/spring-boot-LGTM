package com.example.observability.config

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Import

@AutoConfiguration
@Import(
    TracingConfiguration::class,
    MetricsConfiguration::class,
    LoggingConfiguration::class
)
class ObservabilityAutoConfiguration
