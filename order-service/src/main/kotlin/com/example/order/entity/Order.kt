package com.example.order.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var productId: Long = 0,
    var productName: String = "",
    var quantity: Int = 0,
    var totalPrice: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING)
    var status: OrderStatus = OrderStatus.CREATED,
    var paymentId: Long? = null,
    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    protected constructor() : this(0, 0, "", 0, BigDecimal.ZERO, OrderStatus.CREATED, null, LocalDateTime.now())
}

enum class OrderStatus {
    CREATED, CONFIRMED, PAYMENT_FAILED
}
