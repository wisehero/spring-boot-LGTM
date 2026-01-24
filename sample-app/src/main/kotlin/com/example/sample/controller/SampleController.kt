package com.example.sample.controller

import com.example.sample.service.SampleService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class SampleController(
    private val sampleService: SampleService
) {
    private val log = LoggerFactory.getLogger(SampleController::class.java)

    @GetMapping("/hello")
    fun hello(): Map<String, String> {
        log.info("Handling /api/hello request")
        return mapOf("message" to "Hello from Spring Boot LGTM!")
    }

    @GetMapping("/slow")
    fun slow(): Map<String, String> {
        log.info("Handling /api/slow request - simulating slow response")
        Thread.sleep(2000) // 2 second delay for trace visibility
        return mapOf("message" to "Slow response completed")
    }

    @GetMapping("/error")
    fun error(): Map<String, String> {
        log.error("Handling /api/error request - simulating error")
        throw RuntimeException("Simulated error for observability testing")
    }

    @GetMapping("/chain")
    fun chain(): Map<String, Any> {
        log.info("Handling /api/chain request - calling internal service")
        val serviceResult = sampleService.performOperation()
        return mapOf(
            "message" to "Chain completed",
            "serviceResult" to serviceResult
        )
    }
}
