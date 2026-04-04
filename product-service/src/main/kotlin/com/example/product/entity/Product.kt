package com.example.product.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "products")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var name: String = "",
    var price: BigDecimal = BigDecimal.ZERO,
    var stock: Int = 0,
    var description: String = "",
    @Version
    var version: Long = 0
) {
    protected constructor() : this(0, "", BigDecimal.ZERO, 0, "", 0)
}
