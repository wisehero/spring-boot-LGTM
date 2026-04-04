package com.example.payment.service

import com.example.payment.dto.PaymentRequest
import com.example.payment.dto.PaymentResponse
import com.example.payment.entity.Payment
import com.example.payment.entity.PaymentStatus
import com.example.payment.repository.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository
) {
    private val log = LoggerFactory.getLogger(PaymentService::class.java)

    fun processPayment(request: PaymentRequest): PaymentResponse {
        log.info("Processing payment for orderId={}, amount={}", request.orderId, request.amount)

        // 실제 결제 처리 시간 시뮬레이션 (100~500ms) - DB 트랜잭션 밖에서 수행
        val delay = (100L..500L).random()
        log.debug("Simulating payment processing delay: {}ms", delay)
        Thread.sleep(delay)

        // ~10% 확률로 결제 거부 (에러 시나리오 시연용)
        val isRejected = Math.random() < 0.1
        val status = if (isRejected) {
            log.warn("Payment REJECTED for orderId={}", request.orderId)
            PaymentStatus.REJECTED
        } else {
            log.info("Payment APPROVED for orderId={}", request.orderId)
            PaymentStatus.APPROVED
        }

        return savePayment(request, status)
    }

    @Transactional
    fun savePayment(request: PaymentRequest, status: PaymentStatus): PaymentResponse {
        val payment = Payment(
            orderId = request.orderId,
            amount = request.amount,
            paymentMethod = request.paymentMethod,
            status = status
        )
        val saved = paymentRepository.save(payment)
        return PaymentResponse(
            id = saved.id,
            orderId = saved.orderId,
            amount = saved.amount,
            status = saved.status.name,
            paymentMethod = saved.paymentMethod
        )
    }

    fun getPayment(id: Long): PaymentResponse? {
        log.info("Fetching payment with id={}", id)
        return paymentRepository.findById(id).orElse(null)?.let {
            PaymentResponse(
                id = it.id,
                orderId = it.orderId,
                amount = it.amount,
                status = it.status.name,
                paymentMethod = it.paymentMethod
            )
        }
    }
}
