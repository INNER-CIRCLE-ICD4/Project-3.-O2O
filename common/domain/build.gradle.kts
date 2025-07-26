dependencies {
    // JPA for BaseEntity
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Library module - only jar task needed
tasks.withType<Jar> {
    enabled = true
    archiveClassifier.set("")
}