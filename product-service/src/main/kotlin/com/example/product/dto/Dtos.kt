package com.example.product.dto

import java.math.BigDecimal

data class StockResponse(
    val productId: Long,
    val available: Boolean,
    val currentStock: Int
)

data class StockDecreaseRequest(
    val quantity: Int
)

data class ProductResponse(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val stock: Int,
    val description: String
)
