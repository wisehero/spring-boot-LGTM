package com.example.sample.repository

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

/**
 * 가상의 데이터 저장소 - OTel Agent가 자동으로 모든 메서드를 추적합니다.
 * @Observed 또는 @WithSpan 어노테이션 없이도 span이 생성됩니다.
 */
@Repository
class DataRepository {
    private val log = LoggerFactory.getLogger(DataRepository::class.java)

    // 가상 데이터베이스
    private val database: MutableMap<String, Map<String, Any>> = mutableMapOf(
        "user-1" to mapOf("name" to "Alice", "age" to 30),
        "user-2" to mapOf("name" to "Bob", "age" to 25),
        "user-3" to mapOf("name" to "Charlie", "age" to 35)
    )

    fun findById(id: String): Map<String, Any>? {
        log.info("Repository: Finding by id={}", id)
        Thread.sleep(50) // DB 쿼리 시뮬레이션
        return database[id]
    }

    fun findAll(): List<Map<String, Any>> {
        log.info("Repository: Finding all records")
        Thread.sleep(100) // DB 쿼리 시뮬레이션
        return database.values.toList()
    }

    fun save(id: String, data: Map<String, Any>): Map<String, Any> {
        log.info("Repository: Saving id={}", id)
        Thread.sleep(80) // DB 쓰기 시뮬레이션
        database[id] = data
        return data
    }

    fun count(): Int {
        log.debug("Repository: Counting records")
        return database.size
    }
}
