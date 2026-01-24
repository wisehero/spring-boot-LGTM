package com.example.observability.config

import org.springframework.context.annotation.Configuration

/**
 * Tracing configuration for Java Agent mode.
 *
 * When using OpenTelemetry Java Agent:
 * - Agent handles SDK configuration via OTEL_* environment variables
 * - Agent exports traces to configured endpoint (Tempo)
 * - TracingAspect creates spans using GlobalOpenTelemetry (set by Agent)
 *
 * No additional bean configuration needed - Agent manages everything.
 */
@Configuration
class TracingConfiguration
