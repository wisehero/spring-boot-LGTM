package com.example.sample.repository

import com.example.sample.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * JPA Repository - 실제 JDBC 쿼리가 실행됩니다.
 * Java Agent가 JDBC 호출을 자동으로 추적하여 Tempo에서 SQL span을 볼 수 있습니다.
 */
@Repository
interface UserRepository : JpaRepository<User, String>
