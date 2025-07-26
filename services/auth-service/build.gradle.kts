plugins {
    // Spring Boot
    id("org.springframework.boot") version "3.2.0"
    // Spring 의존성 관리
    id("io.spring.dependency-management") version "1.1.4"
    // Kotlin
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.spring") version "1.9.21"
    kotlin("plugin.jpa") version "1.9.21"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:utils"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring Configuration Properties 프로세서
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Eureka (개발 중 비활성화 권장)
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    // DB, Redis
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.lettuce:lettuce-core")
}

tasks.bootJar {
    enabled = true
    mainClass.set("com.ddakta.auth.AuthApplicationKt")
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    mainClass.set("com.ddakta.auth.AuthApplicationKt")
}
