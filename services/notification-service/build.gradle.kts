plugins {
    id("org.springframework.boot")
    kotlin("plugin.spring")
}

repositories {
    mavenCentral()
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

    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Database driver for docker-compose Postgres
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("com.h2database:h2")

    implementation("com.google.firebase:firebase-admin:9.2.0")

}

tasks.bootJar {
    enabled = true
    mainClass.set("com.ddakta.notification.NotificationApplicationKt")
}
