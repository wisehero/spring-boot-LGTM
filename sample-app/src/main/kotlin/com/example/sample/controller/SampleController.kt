package com.example.sample.controller

import com.example.sample.service.SampleService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

/**
 * REST 컨트롤러 - OTel Agent가 HTTP 요청을 자동으로 추적합니다.
 * Spring MVC 계측으로 모든 엔드포인트가 root span으로 생성됩니다.
 */
@RestController
@RequestMapping("/api")
class SampleController(
    private val sampleService: SampleService
) {
    private val log = LoggerFactory.getLogger(SampleController::class.java)

    @GetMapping("/hello")
    fun hello(): Map<String, String> {
        log.info("Controller: Handling /api/hello request")
        return mapOf("message" to "Hello from Spring Boot LGTM!")
    }

    @GetMapping("/slow")
    fun slow(): Map<String, String> {
        log.info("Controller: Handling /api/slow request")
        Thread.sleep(2000) // 2초 지연
        return mapOf("message" to "Slow response completed")
    }

    @GetMapping("/error")
    fun error(): Map<String, String> {
        log.error("Controller: Handling /api/error request - simulating error")
        throw RuntimeException("Simulated error for observability testing")
    }

    /**
     * 체인 호출 - Controller → Service → (multiple private methods)
     * Tempo에서 전체 호출 스택을 볼 수 있습니다.
     */
    @GetMapping("/chain")
    fun chain(): Map<String, Any> {
        log.info("Controller: Handling /api/chain request")
        val serviceResult = sampleService.performOperation()
        return mapOf(
            "message" to "Chain completed",
            "serviceResult" to serviceResult
        )
    }

    /**
     * 사용자 조회 - Controller → Service → Cache check → Repository
     * 캐시 미스 시 Repository까지 내려가는 흐름을 추적합니다.
     */
    @GetMapping("/users/{userId}")
    fun getUser(@PathVariable userId: String): Map<String, Any> {
        log.info("Controller: Getting user {}", userId)
        val userData = sampleService.getUserData(userId)
        return if (userData != null) {
            mapOf("found" to true, "user" to userData)
        } else {
            mapOf("found" to false, "message" to "User not found")
        }
    }

    /**
     * 전체 사용자 + 통계 - 여러 Repository 호출이 발생합니다.
     * Controller → Service → [Repository.findAll, Repository.count, calculateStatistics]
     */
    @GetMapping("/users")
    fun getAllUsers(): Map<String, Any> {
        log.info("Controller: Getting all users with stats")
        return sampleService.getAllUsersWithStats()
    }

    /**
     * 복잡한 시나리오 - 여러 서비스 호출을 조합합니다.
     * 하나의 요청에서 여러 경로의 span이 생성됩니다.
     */
    @GetMapping("/complex")
    fun complexOperation(): Map<String, Any> {
        log.info("Controller: Starting complex operation")

        // 여러 서비스 호출
        val user1 = sampleService.getUserData("user-1")
        val user2 = sampleService.getUserData("user-2")
        val allStats = sampleService.getAllUsersWithStats()
        val operationResult = sampleService.performOperation()

        return mapOf(
            "user1" to (user1 ?: "not found"),
            "user2" to (user2 ?: "not found"),
            "statistics" to allStats["statistics"]!!,
            "operationResult" to operationResult
        )
    }
}
