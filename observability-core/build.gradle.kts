plugins {
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    // Java Agent 방식: Agent가 trace 전송 처리
    api(libs.bundles.observability.agent)
    implementation(libs.spring.boot.starter)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // OTel API - Agent가 런타임에 구현체 제공
    implementation("io.opentelemetry:opentelemetry-api:1.45.0")

    // Servlet Filter용
    compileOnly("jakarta.servlet:jakarta.servlet-api")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
