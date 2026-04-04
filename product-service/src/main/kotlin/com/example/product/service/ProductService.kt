package com.example.product.service

import com.example.product.dto.ProductResponse
import com.example.product.dto.StockResponse
import com.example.product.repository.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductService(
    private val productRepository: ProductRepository
) {
    private val log = LoggerFactory.getLogger(ProductService::class.java)

    fun getAllProducts(): List<ProductResponse> {
        log.info("전체 상품 목록 조회")
        return productRepository.findAll().map {
            ProductResponse(it.id, it.name, it.price, it.stock, it.description)
        }
    }

    fun getProduct(id: Long): ProductResponse? {
        log.info("상품 조회: id={}", id)
        return productRepository.findById(id).orElse(null)?.let {
            ProductResponse(it.id, it.name, it.price, it.stock, it.description)
        }
    }

    fun checkStock(productId: Long, quantity: Int): StockResponse? {
        log.info("재고 확인: productId={}, quantity={}", productId, quantity)
        val product = productRepository.findById(productId).orElse(null) ?: return null
        return StockResponse(
            productId = product.id,
            available = product.stock >= quantity,
            currentStock = product.stock
        )
    }

    @Transactional
    fun decreaseStock(productId: Long, quantity: Int): ProductResponse? {
        log.info("재고 차감: productId={}, quantity={}", productId, quantity)
        val product = productRepository.findById(productId).orElse(null) ?: return null
        if (product.stock < quantity) {
            throw IllegalStateException("Insufficient stock for product ${product.id}")
        }
        product.stock -= quantity
        try {
            val saved = productRepository.save(product)
            return ProductResponse(saved.id, saved.name, saved.price, saved.stock, saved.description)
        } catch (e: OptimisticLockingFailureException) {
            log.warn("낙관적 락 충돌 발생: productId={}, 재시도 필요", productId)
            throw IllegalStateException("Concurrent stock modification detected for product $productId. Please retry.")
        }
    }
}
