package com.example.sample.repository

import com.example.sample.entity.User
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

/**
 * 데이터 저장소 - 실제 PostgreSQL과 JDBC로 통신합니다.
 * Java Agent가 JDBC 호출을 자동으로 추적하여 Tempo에서 SQL span을 볼 수 있습니다.
 *
 * 기존 서비스 코드와의 호환성을 위해 Map 인터페이스를 유지합니다.
 */
@Repository
class DataRepository(
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(DataRepository::class.java)

    /**
     * 초기 데이터 삽입
     */
    @PostConstruct
    fun initData() {
        log.info("리포지토리: 초기 데이터 삽입 중")
        if (userRepository.count() == 0L) {
            userRepository.saveAll(listOf(
                User("user-1", "Alice", 30),
                User("user-2", "Bob", 25),
                User("user-3", "Charlie", 35)
            ))
            log.info("리포지토리: 초기 데이터 삽입 완료")
        }
    }

    fun findById(id: String): Map<String, Any>? {
        log.info("리포지토리: ID로 조회 중 id={}", id)
        return userRepository.findById(id)
            .map { it.toMap() }
            .orElse(null)
    }

    fun findAll(): List<Map<String, Any>> {
        log.info("리포지토리: 전체 레코드 조회 중")
        return userRepository.findAll().map { it.toMap() }
    }

    fun save(id: String, data: Map<String, Any>): Map<String, Any> {
        log.info("리포지토리: 저장 중 id={}", id)
        val user = User.fromMap(id, data)
        return userRepository.save(user).toMap()
    }

    fun count(): Int {
        log.debug("리포지토리: 레코드 개수 세는 중")
        return userRepository.count().toInt()
    }
}
