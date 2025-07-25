plugins {
    id("org.springframework.boot")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:utils"))
    
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    
    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
    
    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    
    // Service Discovery
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    
    // API Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
}

tasks.bootJar {
    enabled = true
    mainClass.set("com.ddakta.auth.AuthApplicationKt")
}