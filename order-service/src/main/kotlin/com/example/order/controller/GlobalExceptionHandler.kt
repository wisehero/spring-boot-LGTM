package com.example.order.controller

import com.example.order.service.InsufficientStockException
import com.example.order.service.ProductNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전역 예외 → HTTP 상태 변환. 컨트롤러에서 try-catch 를 제거하고 예외 매핑을 한곳으로 모은다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ProductNotFoundException::class)
    fun handleProductNotFound(e: ProductNotFoundException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))

    @ExceptionHandler(InsufficientStockException::class)
    fun handleInsufficientStock(e: InsufficientStockException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to e.message))

    @ExceptionHandler(Exception::class)
    fun handleGeneric(e: Exception): ResponseEntity<Map<String, String?>> {
        log.error("처리되지 않은 예외", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to (e.message ?: "Internal server error")))
    }
}
