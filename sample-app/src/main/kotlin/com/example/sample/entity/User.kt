package com.example.sample.entity

import jakarta.persistence.*

/**
 * 사용자 엔티티 - 실제 PostgreSQL 테이블과 매핑
 */
@Entity
@Table(name = "users")
class User(
    @Id
    var id: String,

    var name: String,

    var age: Int
) {
    // JPA 기본 생성자
    protected constructor() : this("", "", 0)

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "age" to age
    )

    companion object {
        fun fromMap(id: String, data: Map<String, Any>): User {
            return User(
                id = id,
                name = data["name"] as? String ?: "",
                age = (data["age"] as? Number)?.toInt() ?: 0
            )
        }
    }
}
