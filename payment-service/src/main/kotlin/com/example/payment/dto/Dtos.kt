package com.example.payment.dto

import java.math.BigDecimal

data class PaymentRequest(
    val orderId: Long,
    val amount: BigDecimal,
    val paymentMethod: String = "CREDIT_CARD"
)

data class PaymentResponse(
    val id: Long,
    val orderId: Long,
    val amount: BigDecimal,
    val status: String,
    val paymentMethod: String
)
