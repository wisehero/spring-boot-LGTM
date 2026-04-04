package com.example.order.dto

import java.math.BigDecimal

// Request DTOs
data class CreateOrderRequest(
    val productId: Long,
    val quantity: Int
)

// External service response DTOs
data class ProductDto(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val stock: Int
)

data class StockResponse(
    val productId: Long,
    val available: Boolean,
    val currentStock: Int
)

data class StockDecreaseRequest(
    val quantity: Int
)

data class PaymentRequest(
    val orderId: Long,
    val amount: BigDecimal,
    val paymentMethod: String = "CREDIT_CARD"
)

data class PaymentDto(
    val id: Long,
    val orderId: Long,
    val amount: BigDecimal,
    val status: String
)

// Order response DTO
data class OrderResponse(
    val id: Long,
    val productName: String,
    val quantity: Int,
    val totalPrice: BigDecimal,
    val status: String,
    val paymentId: Long?,
    val paymentStatus: String?
)
