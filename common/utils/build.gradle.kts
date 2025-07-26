dependencies {
    // 다른 common 모듈 의존
    implementation(project(":common:domain"))
    
    // Spring 의존성
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // JWT for security utils
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
}

// Library module - only jar task needed
tasks.withType<Jar> {
    enabled = true
    archiveClassifier.set("")
}