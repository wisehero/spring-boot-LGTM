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
    api(libs.bundles.observability)
    implementation(libs.spring.boot.starter)
    implementation("org.springframework.boot:spring-boot-starter-web")

    // For Servlet Filter
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    // OpenTelemetry SDK autoconfigure for ResourceProvider
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
