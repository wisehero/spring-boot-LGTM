package com.example.order.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class RestClientConfig(
    @Value("\${services.product.url}") private val productUrl: String,
    @Value("\${services.payment.url}") private val paymentUrl: String
) {
    @Bean
    fun productServiceClient(restTemplateBuilder: RestTemplateBuilder): RestClient {
        val restTemplate = restTemplateBuilder
            .rootUri(productUrl)
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(10))
            .build()
        return RestClient.create(restTemplate)
    }

    @Bean
    fun paymentServiceClient(restTemplateBuilder: RestTemplateBuilder): RestClient {
        val restTemplate = restTemplateBuilder
            .rootUri(paymentUrl)
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(10))
            .build()
        return RestClient.create(restTemplate)
    }
}
