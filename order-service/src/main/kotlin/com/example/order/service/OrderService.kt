package com.example.order.service

import com.example.order.dto.*
import com.example.order.entity.Order
import com.example.order.entity.OrderStatus
import com.example.order.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val productServiceClient: RestClient,
    private val paymentServiceClient: RestClient
) {
    private val log = LoggerFactory.getLogger(OrderService::class.java)

    @Transactional
    fun createOrder(request: CreateOrderRequest): OrderResponse {
        log.info("Creating order for productId={}, quantity={}", request.productId, request.quantity)

        // 1. 상품 조회
        val product = getProduct(request.productId)
        log.info("Product found: name={}, price={}", product.name, product.price)

        // 2. 재고 확인
        val stockResponse = checkStock(request.productId, request.quantity)
        if (!stockResponse.available) {
            log.warn("Insufficient stock for productId={}, requested={}, current={}",
                request.productId, request.quantity, stockResponse.currentStock)
            throw InsufficientStockException(
                "Insufficient stock for product ${product.name}. Requested: ${request.quantity}, Available: ${stockResponse.currentStock}"
            )
        }

        // 3. 주문 생성 (CREATED 상태)
        val totalPrice = product.price.multiply(request.quantity.toBigDecimal())
        val order = Order(
            productId = request.productId,
            productName = product.name,
            quantity = request.quantity,
            totalPrice = totalPrice,
            status = OrderStatus.CREATED
        )
        val savedOrder = orderRepository.save(order)
        log.info("Order created with id={}", savedOrder.id)

        // 4. 결제 요청
        val paymentResponse = processPayment(savedOrder.id, totalPrice)
        savedOrder.paymentId = paymentResponse.id

        var paymentStatus = paymentResponse.status

        if (paymentResponse.status == "APPROVED") {
            // 5-A. 결제 성공 → 재고 차감 + CONFIRMED
            log.info("Payment approved for orderId={}, decreasing stock", savedOrder.id)
            decreaseStock(request.productId, request.quantity)
            savedOrder.status = OrderStatus.CONFIRMED
        } else {
            // 5-B. 결제 실패 → PAYMENT_FAILED
            log.warn("Payment rejected for orderId={}", savedOrder.id)
            savedOrder.status = OrderStatus.PAYMENT_FAILED
        }

        // 6. 주문 상태 업데이트
        val finalOrder = orderRepository.save(savedOrder)
        log.info("Order finalized: id={}, status={}", finalOrder.id, finalOrder.status)

        return OrderResponse(
            id = finalOrder.id,
            productName = finalOrder.productName,
            quantity = finalOrder.quantity,
            totalPrice = finalOrder.totalPrice,
            status = finalOrder.status.name,
            paymentId = finalOrder.paymentId,
            paymentStatus = paymentStatus
        )
    }

    fun getOrder(id: Long): OrderResponse? {
        log.info("Fetching order with id={}", id)
        return orderRepository.findById(id).orElse(null)?.let {
            OrderResponse(
                id = it.id,
                productName = it.productName,
                quantity = it.quantity,
                totalPrice = it.totalPrice,
                status = it.status.name,
                paymentId = it.paymentId,
                paymentStatus = null
            )
        }
    }

    fun getAllOrders(): List<OrderResponse> {
        log.info("Fetching all orders")
        return orderRepository.findAll().map {
            OrderResponse(
                id = it.id,
                productName = it.productName,
                quantity = it.quantity,
                totalPrice = it.totalPrice,
                status = it.status.name,
                paymentId = it.paymentId,
                paymentStatus = null
            )
        }
    }

    private fun getProduct(productId: Long): ProductDto {
        try {
            return productServiceClient.get()
                .uri("/api/products/{id}", productId)
                .retrieve()
                .body(ProductDto::class.java)
                ?: throw ProductNotFoundException("Product not found: $productId")
        } catch (e: RestClientException) {
            log.error("Failed to fetch product {}: {}", productId, e.message)
            throw ProductNotFoundException("Product not found: $productId")
        }
    }

    private fun checkStock(productId: Long, quantity: Int): StockResponse {
        try {
            return productServiceClient.get()
                .uri("/api/products/{id}/stock?quantity={quantity}", productId, quantity)
                .retrieve()
                .body(StockResponse::class.java)
                ?: throw RuntimeException("Failed to check stock for product $productId")
        } catch (e: RestClientException) {
            log.error("Failed to check stock for product {}: {}", productId, e.message)
            throw RuntimeException("Failed to check stock for product $productId", e)
        }
    }

    private fun processPayment(orderId: Long, amount: java.math.BigDecimal): PaymentDto {
        try {
            val request = PaymentRequest(orderId = orderId, amount = amount)
            return paymentServiceClient.post()
                .uri("/api/payments")
                .body(request)
                .retrieve()
                .body(PaymentDto::class.java)
                ?: throw RuntimeException("Failed to process payment for order $orderId")
        } catch (e: RestClientException) {
            log.error("Failed to process payment for order {}: {}", orderId, e.message)
            throw RuntimeException("Failed to process payment for order $orderId", e)
        }
    }

    private fun decreaseStock(productId: Long, quantity: Int) {
        try {
            productServiceClient.patch()
                .uri("/api/products/{id}/stock", productId)
                .body(StockDecreaseRequest(quantity))
                .retrieve()
                .toBodilessEntity()
        } catch (e: RestClientException) {
            log.error("Failed to decrease stock for product {}: {}", productId, e.message)
            throw RuntimeException("Failed to decrease stock for product $productId", e)
        }
    }
}

class ProductNotFoundException(message: String) : RuntimeException(message)
class InsufficientStockException(message: String) : RuntimeException(message)
