package com.example.product.controller

import com.example.product.dto.StockDecreaseRequest
import com.example.product.service.ProductService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productService: ProductService
) {
    @GetMapping
    fun getAllProducts() = ResponseEntity.ok(productService.getAllProducts())

    @GetMapping("/{id}")
    fun getProduct(@PathVariable id: Long): ResponseEntity<Any> {
        val product = productService.getProduct(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(product)
    }

    @GetMapping("/{id}/stock")
    fun checkStock(
        @PathVariable id: Long,
        @RequestParam quantity: Int
    ): ResponseEntity<Any> {
        val stock = productService.checkStock(id, quantity)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(stock)
    }

    @PatchMapping("/{id}/stock")
    fun decreaseStock(
        @PathVariable id: Long,
        @RequestBody request: StockDecreaseRequest
    ): ResponseEntity<Any> {
        val product = productService.decreaseStock(id, request.quantity)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(product)
    }
}
