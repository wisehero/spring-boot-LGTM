package com.example.product.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전역 예외 → HTTP 상태 변환. 재고 부족·동시 수정 충돌은 IllegalStateException 으로 던져지며
 * 409 Conflict 로 변환된다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to e.message))

    @ExceptionHandler(Exception::class)
    fun handleGeneric(e: Exception): ResponseEntity<Map<String, String?>> {
        log.error("처리되지 않은 예외", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to (e.message ?: "Internal server error")))
    }
}
