plugins {
    id("org.springframework.boot")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("jacoco")
}

dependencies {
    // Internal modules
    implementation(project(":common:domain"))
    implementation(project(":common:events"))
    implementation(project(":common:utils"))
    implementation(project(":common:user-client"))
    
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    
    // Spring Cloud
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    
    // Kafka
    implementation("org.springframework.kafka:spring-kafka")
    
    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.redisson:redisson-spring-boot-starter:3.24.3")
    
    // State Machine
    implementation("org.springframework.statemachine:spring-statemachine-starter:3.2.0")
    
    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    
    // H3 Geo Index
    implementation("com.uber:h3:4.1.1")
    
    // Utils
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testRuntimeOnly("com.h2database:h2")
}

tasks.bootJar {
    enabled = true
    mainClass.set("com.ddakta.matching.MatchingApplicationKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    jvmArgs("-Dnet.bytebuddy.experimental=true")
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests"
    group = "verification"
    
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    
    include("**/integration/**")
    
    systemProperty("spring.profiles.active", "test")
    jvmArgs("-Dnet.bytebuddy.experimental=true")
}

tasks.register<Test>("distributedTest") {
    description = "Runs distributed system tests"
    group = "verification"
    
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    
    include("**/DistributedSystemTest*")
    
    systemProperty("spring.profiles.active", "test")
    jvmArgs("-Dnet.bytebuddy.experimental=true")
}

configure<JacocoPluginExtension> {
    toolVersion = "0.8.12"
}

tasks.withType<JacocoReport> {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/config/**",
                    "**/entity/**",
                    "**/dto/**",
                    "**/enum/**",
                    "**/exception/**",
                    "**/*Application*"
                )
            }
        })
    )
}

tasks.withType<JacocoCoverageVerification> {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// K6 Load Testing Tasks
tasks.register<Exec>("installK6") {
    description = "Install K6 load testing tool"
    group = "verification"
    
    doFirst {
        val os = System.getProperty("os.name").toLowerCase()
        when {
            os.contains("mac") -> {
                commandLine("brew", "install", "k6")
            }
            os.contains("linux") -> {
                commandLine("sh", "-c", "wget -q -O - https://dl.k6.io/key.gpg | apt-key add - && echo 'deb https://dl.k6.io/deb stable main' | tee /etc/apt/sources.list.d/k6.list && apt-get update && apt-get install k6")
            }
            else -> {
                throw GradleException("Unsupported OS for K6 installation: $os")
            }
        }
    }
    
    isIgnoreExitValue = true
}

tasks.register<Exec>("checkK6") {
    description = "Check if K6 is installed"
    group = "verification"
    
    commandLine("k6", "version")
    isIgnoreExitValue = true
    
    doLast {
        if (executionResult.get().exitValue != 0) {
            logger.warn("K6 is not installed. Run './gradlew installK6' to install it.")
        }
    }
}

tasks.register<Exec>("loadTest") {
    description = "Run K6 load tests"
    group = "verification"
    
    dependsOn("checkK6")
    
    workingDir = file("test-infrastructure/k6-scripts")
    commandLine("k6", "run", 
        "--out", "json=load-test-results.json",
        "--summary-export=load-test-summary.json",
        "load-test.js"
    )
    
    environment("BASE_URL", System.getenv("BASE_URL") ?: "http://localhost:8080")
    
    doFirst {
        logger.lifecycle("Running K6 load tests...")
    }
    
    doLast {
        if (executionResult.get().exitValue != 0) {
            throw GradleException("Load tests failed. Check the results in test-infrastructure/k6-scripts/load-test-results.json")
        }
        logger.lifecycle("Load tests completed successfully")
    }
}

tasks.register<Exec>("loadTestWithHtmlReport") {
    description = "Run K6 load tests with HTML report generation"
    group = "verification"
    
    dependsOn("loadTest")
    
    workingDir = file("test-infrastructure/k6-scripts")
    commandLine("k6", "run",
        "--out", "json=load-test-results.json",
        "--out", "influxdb=http://localhost:8086/k6",
        "--summary-export=load-test-summary.json",
        "load-test.js"
    )
    
    environment("BASE_URL", System.getenv("BASE_URL") ?: "http://localhost:8080")
    
    doLast {
        // Generate HTML report from JSON results
        val reportFile = file("${buildDir}/reports/k6/load-test-report.html")
        reportFile.parentFile.mkdirs()
        generateK6HtmlReport(
            file("test-infrastructure/k6-scripts/load-test-summary.json"),
            reportFile
        )
        logger.lifecycle("Load test report generated: ${reportFile.absolutePath}")
    }
}

fun generateK6HtmlReport(summaryFile: File, outputFile: File) {
    if (!summaryFile.exists()) {
        logger.warn("K6 summary file not found: ${summaryFile.absolutePath}")
        return
    }
    
    val summaryJson = summaryFile.readText()
    val htmlTemplate = """
<!DOCTYPE html>
<html>
<head>
    <title>K6 Load Test Report - Matching Service</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        h1, h2 { color: #333; }
        .metric { 
            background: #f5f5f5; 
            padding: 15px; 
            margin: 10px 0; 
            border-radius: 5px;
            border-left: 4px solid #4CAF50;
        }
        .metric.failed { border-left-color: #f44336; }
        .metric-name { font-weight: bold; }
        .metric-value { float: right; }
        .threshold { color: #666; font-size: 0.9em; }
        .passed { color: #4CAF50; }
        .failed { color: #f44336; }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; }
        th, td { padding: 10px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background-color: #f5f5f5; }
    </style>
</head>
<body>
    <h1>K6 Load Test Report - Matching Service</h1>
    <div id="report-content">
        <!-- Report content will be injected here by JavaScript -->
    </div>
    <script>
        // Load and parse the K6 summary JSON
        const summaryData = $summaryJson;
        
        function generateReport(data) {
            let html = '<h2>Test Summary</h2>';
            
            // Add metrics
            html += '<h3>Performance Metrics</h3>';
            for (const [key, metric] of Object.entries(data.metrics)) {
                const passed = metric.thresholds ? Object.values(metric.thresholds).every(t => t.ok) : true;
                html += '<div class="metric ' + (passed ? '' : 'failed') + '">' +
                    '<span class="metric-name">' + key + '</span>' +
                    '<span class="metric-value">' + formatMetricValue(metric) + '</span>' +
                    (metric.thresholds ? '<div class="threshold">' + formatThresholds(metric.thresholds) + '</div>' : '') +
                '</div>';
            }
            
            document.getElementById('report-content').innerHTML = html;
        }
        
        function formatMetricValue(metric) {
            if (metric.values) {
                return Object.entries(metric.values)
                    .map(([k, v]) => k + ': ' + v)
                    .join(', ');
            }
            return metric.value || 'N/A';
        }
        
        function formatThresholds(thresholds) {
            return Object.entries(thresholds)
                .map(([name, result]) => {
                    const status = result.ok ? '<span class="passed">✓</span>' : '<span class="failed">✗</span>';
                    return status + ' ' + name;
                })
                .join(' | ');
        }
        
        generateReport(summaryData);
    </script>
</body>
</html>
    """.trimIndent()
    
    outputFile.writeText(htmlTemplate)
}

