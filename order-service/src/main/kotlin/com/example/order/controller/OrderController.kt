package com.example.order.controller

import com.example.order.dto.CreateOrderRequest
import com.example.order.service.OrderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {
    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<Any> {
        val order = orderService.createOrder(request)
        return ResponseEntity.ok(order)
    }

    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: Long): ResponseEntity<Any> {
        val order = orderService.getOrder(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(order)
    }

    @GetMapping
    fun getAllOrders(): ResponseEntity<Any> {
        return ResponseEntity.ok(orderService.getAllOrders())
    }
}
