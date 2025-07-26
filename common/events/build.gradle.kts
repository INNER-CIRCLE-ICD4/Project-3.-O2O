dependencies {
    implementation(project(":common:domain"))
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}

// Library module - only jar task needed
tasks.withType<Jar> {
    enabled = true
    archiveClassifier.set("")
}