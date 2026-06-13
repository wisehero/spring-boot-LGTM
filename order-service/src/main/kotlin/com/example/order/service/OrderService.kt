package com.example.order.service

import com.example.order.dto.*
import com.example.order.entity.Order
import com.example.order.entity.OrderStatus
import com.example.order.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val productServiceClient: RestClient,
    private val paymentServiceClient: RestClient
) {
    private val log = LoggerFactory.getLogger(OrderService::class.java)

    /**
     * 주문 생성 오케스트레이션.
     *
     * 의도적으로 메서드 전체를 @Transactional 로 감싸지 않는다. 이 흐름에는 상품 조회·재고
     * 확인·결제·재고 차감 등 여러 번의 원격 HTTP 호출(결제는 100~500ms 소요)이 포함되는데,
     * 단일 트랜잭션으로 감싸면 그 네트워크 I/O 내내 DB 커넥션을 점유하게 된다.
     * 각 주문 저장(orderRepository.save)은 그 자체로 원자적이므로, DB 쓰기 경계를 좁게 유지하고
     * 원격 호출은 트랜잭션 밖에서 수행한다.
     */
    fun createOrder(request: CreateOrderRequest): OrderResponse {
        log.info("주문 생성 시작: productId={}, quantity={}", request.productId, request.quantity)

        // 1. 상품 조회 (원격 호출 — 트랜잭션 밖)
        val product = getProduct(request.productId)
        log.info("상품 조회 완료: name={}, price={}", product.name, product.price)

        // 2. 재고 확인 (원격 호출 — 트랜잭션 밖)
        val stockResponse = checkStock(request.productId, request.quantity)
        if (!stockResponse.available) {
            log.warn("재고 부족: productId={}, 요청수량={}, 현재재고={}",
                request.productId, request.quantity, stockResponse.currentStock)
            throw InsufficientStockException(
                "Insufficient stock for product ${product.name}. Requested: ${request.quantity}, Available: ${stockResponse.currentStock}"
            )
        }

        // 3. 주문 생성 (CREATED 상태) — 단건 저장(원자적)
        val totalPrice = product.price.multiply(request.quantity.toBigDecimal())
        val savedOrder = orderRepository.save(
            Order(
                productId = request.productId,
                productName = product.name,
                quantity = request.quantity,
                totalPrice = totalPrice,
                status = OrderStatus.CREATED
            )
        )
        log.info("주문 생성 완료: id={}", savedOrder.id)

        // 4. 결제 요청 (원격 호출 — 트랜잭션 밖)
        val paymentResponse = processPayment(savedOrder.id, totalPrice)
        savedOrder.paymentId = paymentResponse.id
        var paymentStatus = paymentResponse.status

        if (paymentResponse.status == "APPROVED") {
            // 5-A. 결제 성공 → 재고 차감 시도
            log.info("결제 승인됨: orderId={}, 재고 차감 진행", savedOrder.id)
            try {
                decreaseStock(request.productId, request.quantity)
                savedOrder.status = OrderStatus.CONFIRMED
            } catch (e: RuntimeException) {
                // 5-A 보상: 재고 차감 실패 시 이미 승인된 결제를 취소(보상 트랜잭션)하여
                // 고아 결제를 방지한다. 주문은 PAYMENT_FAILED 로 마감한다.
                log.error("재고 차감 실패 → 결제 보상(취소) 진행: orderId={}, paymentId={}, 원인={}",
                    savedOrder.id, paymentResponse.id, e.message)
                paymentStatus = compensatePayment(paymentResponse.id)
                savedOrder.status = OrderStatus.PAYMENT_FAILED
            }
        } else {
            // 5-B. 결제 거부 → PAYMENT_FAILED
            log.warn("결제 거부됨: orderId={}", savedOrder.id)
            savedOrder.status = OrderStatus.PAYMENT_FAILED
        }

        // 6. 주문 상태 업데이트 — 단건 저장(원자적)
        val finalOrder = orderRepository.save(savedOrder)
        log.info("주문 확정: id={}, status={}", finalOrder.id, finalOrder.status)

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
        log.info("주문 조회: id={}", id)
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
        log.info("전체 주문 목록 조회")
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
            log.error("상품 조회 실패: productId={}, 원인={}", productId, e.message)
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
            log.error("재고 확인 실패: productId={}, 원인={}", productId, e.message)
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
            log.error("결제 처리 실패: orderId={}, 원인={}", orderId, e.message)
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
            log.error("재고 차감 실패: productId={}, 원인={}", productId, e.message)
            throw RuntimeException("Failed to decrease stock for product $productId", e)
        }
    }

    /**
     * 결제 보상(취소). 결제 승인 후 재고 차감이 실패했을 때 호출되어 승인된 결제를 되돌린다.
     * 보상 호출 자체가 실패하면 자동 복구가 불가능하므로 수동 정합성 점검이 필요함을 로깅한다.
     * @return 취소 후 결제 상태("CANCELLED") 또는 보상 실패 시 "CANCEL_FAILED"
     */
    private fun compensatePayment(paymentId: Long): String {
        return try {
            val response = paymentServiceClient.post()
                .uri("/api/payments/{id}/cancel", paymentId)
                .retrieve()
                .body(PaymentDto::class.java)
            log.info("결제 보상 완료: paymentId={}, status={}", paymentId, response?.status)
            response?.status ?: "CANCELLED"
        } catch (e: RestClientException) {
            log.error("결제 보상(취소) 실패: paymentId={}, 원인={} — 수동 정합성 점검 필요",
                paymentId, e.message)
            "CANCEL_FAILED"
        }
    }
}

class ProductNotFoundException(message: String) : RuntimeException(message)
class InsufficientStockException(message: String) : RuntimeException(message)
