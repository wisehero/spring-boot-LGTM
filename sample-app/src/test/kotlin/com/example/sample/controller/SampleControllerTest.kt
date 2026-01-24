package com.example.sample.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SampleControllerTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `GET api hello returns 200`() {
        val response = restTemplate.getForEntity("/api/hello", Map::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull
        @Suppress("UNCHECKED_CAST")
        val body = response.body as Map<String, Any>
        assertThat(body).containsKey("message")
    }

    @Test
    fun `GET api chain returns 200`() {
        val response = restTemplate.getForEntity("/api/chain", Map::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull
        @Suppress("UNCHECKED_CAST")
        val body = response.body as Map<String, Any>
        assertThat(body).containsKey("message")
        assertThat(body).containsKey("serviceResult")
    }

    @Test
    fun `actuator metrics endpoint is accessible`() {
        val response = restTemplate.getForEntity("/actuator/metrics", Map::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull
        @Suppress("UNCHECKED_CAST")
        val body = response.body as Map<String, Any>
        assertThat(body).containsKey("names")
    }

    @Test
    fun `actuator health endpoint returns UP`() {
        val response = restTemplate.getForEntity("/actuator/health", Map::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        @Suppress("UNCHECKED_CAST")
        val body = response.body as Map<String, Any>
        assertThat(body["status"]).isEqualTo("UP")
    }
}
