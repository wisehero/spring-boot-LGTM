package com.example.product.init

import com.example.product.entity.Product
import com.example.product.repository.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class DataInitializer(
    private val productRepository: ProductRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DataInitializer::class.java)

    override fun run(args: ApplicationArguments?) {
        if (productRepository.count() > 0) {
            log.info("Products already initialized, skipping...")
            return
        }

        val products = listOf(
            Product(name = "Spring Boot in Action", price = BigDecimal("45000"), stock = 100, description = "스프링 부트 입문서"),
            Product(name = "Kotlin Cookbook", price = BigDecimal("38000"), stock = 50, description = "코틀린 레시피 모음"),
            Product(name = "Microservices Patterns", price = BigDecimal("52000"), stock = 30, description = "마이크로서비스 설계 패턴"),
            Product(name = "Observability Engineering", price = BigDecimal("48000"), stock = 75, description = "관측 가능성 엔지니어링"),
            Product(name = "Distributed Systems", price = BigDecimal("55000"), stock = 20, description = "분산 시스템 원리")
        )

        productRepository.saveAll(products)
        log.info("Initialized {} products", products.size)
    }
}
