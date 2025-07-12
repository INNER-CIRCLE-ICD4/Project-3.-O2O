plugins {
    id("org.springframework.boot")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:events"))
    implementation(project(":common:utils"))
    
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.boot:spring-boot-starter-mail")
}

tasks.bootJar {
    enabled = true
    mainClass.set("com.ddakta.notification.NotificationApplicationKt")
}

