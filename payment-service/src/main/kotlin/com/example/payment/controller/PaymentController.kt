package com.example.payment.controller

import com.example.payment.dto.PaymentRequest
import com.example.payment.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService
) {
    @PostMapping
    fun processPayment(@RequestBody request: PaymentRequest): ResponseEntity<Any> {
        val payment = paymentService.processPayment(request)
        return ResponseEntity.ok(payment)
    }

    @GetMapping("/{id}")
    fun getPayment(@PathVariable id: Long): ResponseEntity<Any> {
        val payment = paymentService.getPayment(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(payment)
    }
}
