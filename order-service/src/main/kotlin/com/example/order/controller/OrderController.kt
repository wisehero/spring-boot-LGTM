package com.example.order.controller

import com.example.order.dto.CreateOrderRequest
import com.example.order.service.InsufficientStockException
import com.example.order.service.OrderService
import com.example.order.service.ProductNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {
    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<Any> {
        return try {
            val order = orderService.createOrder(request)
            ResponseEntity.ok(order)
        } catch (e: ProductNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to e.message))
        } catch (e: InsufficientStockException) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (e.message ?: "Internal server error")))
        }
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
