package com.example.sample.service

import com.example.sample.repository.DataRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 비즈니스 로직 서비스 - OTel Agent가 모든 메서드를 자동으로 추적합니다.
 * 어노테이션 없이도 Tempo에서 각 메서드가 별도 span으로 표시됩니다.
 */
@Service
class SampleService(
    private val dataRepository: DataRepository
) {
    private val log = LoggerFactory.getLogger(SampleService::class.java)

    fun performOperation(): String {
        log.info("Service: Starting operation")

        // Step 1: 데이터 검증
        val isValid = validateRequest()

        // Step 2: 데이터 처리
        val processedData = processData()

        // Step 3: 결과 포맷팅
        val result = formatResult(processedData)

        log.info("Service: Operation completed with result: {}", result)
        return result
    }

    fun getUserData(userId: String): Map<String, Any>? {
        log.info("Service: Getting user data for userId={}", userId)

        // 캐시 확인 (시뮬레이션)
        val cached = checkCache(userId)
        if (cached != null) {
            log.info("Service: Cache hit for userId={}", userId)
            return cached
        }

        // 캐시 미스 - DB 조회
        log.info("Service: Cache miss, querying repository")
        val userData = dataRepository.findById(userId)

        // 캐시 저장 (시뮬레이션)
        userData?.let { saveToCache(userId, it) }

        return userData
    }

    fun getAllUsersWithStats(): Map<String, Any> {
        log.info("Service: Getting all users with statistics")

        val users = dataRepository.findAll()
        val count = dataRepository.count()
        val stats = calculateStatistics(users)

        return mapOf(
            "users" to users,
            "count" to count,
            "statistics" to stats
        )
    }

    // === Private helper methods - 이것들도 전부 span으로 추적됩니다 ===

    private fun validateRequest(): Boolean {
        log.debug("Service: Validating request")
        Thread.sleep(30) // 검증 로직 시뮬레이션
        return true
    }

    private fun processData(): String {
        log.debug("Service: Processing data")
        Thread.sleep(200) // 처리 로직 시뮬레이션
        return "processed-${System.currentTimeMillis()}"
    }

    private fun formatResult(data: String): String {
        log.debug("Service: Formatting result")
        Thread.sleep(20)
        return "RESULT[$data]"
    }

    private fun checkCache(key: String): Map<String, Any>? {
        log.debug("Service: Checking cache for key={}", key)
        Thread.sleep(10) // 캐시 조회 시뮬레이션
        return null // 항상 캐시 미스로 시뮬레이션
    }

    private fun saveToCache(key: String, value: Map<String, Any>) {
        log.debug("Service: Saving to cache key={}", key)
        Thread.sleep(15) // 캐시 저장 시뮬레이션
    }

    private fun calculateStatistics(users: List<Map<String, Any>>): Map<String, Any> {
        log.debug("Service: Calculating statistics for {} users", users.size)
        Thread.sleep(50)
        return mapOf(
            "totalUsers" to users.size,
            "calculatedAt" to System.currentTimeMillis()
        )
    }
}
