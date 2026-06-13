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
        log.info("결제 처리 시작: orderId={}, amount={}", request.orderId, request.amount)

        // 실제 결제 처리 시간 시뮬레이션 (100~500ms) - DB 트랜잭션 밖에서 수행
        val delay = (100L..500L).random()
        log.debug("결제 처리 지연 시뮬레이션: {}ms", delay)
        Thread.sleep(delay)

        // ~10% 확률로 결제 거부 (에러 시나리오 시연용)
        val isRejected = Math.random() < 0.1
        val status = if (isRejected) {
            log.warn("결제 거부됨: orderId={}", request.orderId)
            PaymentStatus.REJECTED
        } else {
            log.info("결제 승인됨: orderId={}", request.orderId)
            PaymentStatus.APPROVED
        }

        // 단건 저장은 repository.save 자체가 원자적 트랜잭션이다.
        // (이전 구현은 같은 클래스의 @Transactional savePayment를 self-invocation 하여
        //  Spring 프록시를 우회했고, 그 결과 @Transactional 이 적용되지 않았다.)
        val saved = paymentRepository.save(
            Payment(
                orderId = request.orderId,
                amount = request.amount,
                paymentMethod = request.paymentMethod,
                status = status
            )
        )
        return saved.toResponse()
    }

    /**
     * 결제 취소 (보상 트랜잭션).
     * 주문 서비스에서 결제 승인 후 후속 단계(재고 차감 등)가 실패했을 때 호출되어
     * 이미 승인된 결제를 CANCELLED 로 되돌린다 — 고아 결제(orphan payment)를 방지한다.
     * 컨트롤러를 통해 외부에서 호출되므로 @Transactional 프록시가 정상 적용된다.
     */
    @Transactional
    fun cancelPayment(id: Long): PaymentResponse? {
        log.info("결제 취소(보상): id={}", id)
        val payment = paymentRepository.findById(id).orElse(null) ?: return null
        if (payment.status == PaymentStatus.CANCELLED) {
            log.info("이미 취소된 결제: id={}", id)
            return payment.toResponse()
        }
        payment.status = PaymentStatus.CANCELLED
        return paymentRepository.save(payment).toResponse()
    }

    fun getPayment(id: Long): PaymentResponse? {
        log.info("결제 조회: id={}", id)
        return paymentRepository.findById(id).orElse(null)?.toResponse()
    }

    private fun Payment.toResponse(): PaymentResponse = PaymentResponse(
        id = id,
        orderId = orderId,
        amount = amount,
        status = status.name,
        paymentMethod = paymentMethod
    )
}
