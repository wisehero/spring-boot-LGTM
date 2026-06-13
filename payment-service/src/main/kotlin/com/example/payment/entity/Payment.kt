package com.example.payment.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payments")
class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var orderId: Long = 0,
    var amount: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING)
    var status: PaymentStatus = PaymentStatus.PENDING,
    var paymentMethod: String = "CREDIT_CARD",
    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    protected constructor() : this(0, 0, BigDecimal.ZERO, PaymentStatus.PENDING, "CREDIT_CARD", LocalDateTime.now())
}

enum class PaymentStatus {
    PENDING, APPROVED, REJECTED, CANCELLED
}
