package com.example.sample.service

import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SampleService {
    private val log = LoggerFactory.getLogger(SampleService::class.java)

    @Observed(name = "sample.service.operation", contextualName = "perform-operation")
    fun performOperation(): String {
        log.info("Performing internal operation")

        // Simulate some work
        Thread.sleep(500)

        val result = processData()
        log.info("Operation completed with result: {}", result)

        return result
    }

    @Observed(name = "sample.service.process", contextualName = "process-data")
    private fun processData(): String {
        log.debug("Processing data...")
        Thread.sleep(200)
        return "processed-${System.currentTimeMillis()}"
    }
}
