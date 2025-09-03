package com.ddakta.notification.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import mu.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.File
import java.io.FileInputStream
import javax.annotation.PostConstruct

@Configuration
class FirebaseConfig {

    private val logger = KotlinLogging.logger {}

    @PostConstruct
    fun initialize() {
        if (FirebaseApp.getApps().isNotEmpty()) return

        // 1) Prefer explicit env var path if provided
        val envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        val inputStream = try {
            when {
                !envPath.isNullOrBlank() && File(envPath).exists() -> {
                    logger.info { "Initializing Firebase with GOOGLE_APPLICATION_CREDENTIALS: $envPath" }
                    FileInputStream(envPath)
                }
                else -> {
                    // 2) Fallback to classpath resource
                    val resource = ClassPathResource("firebase/firebase-service-account.json")
                    if (resource.exists()) {
                        logger.info { "Initializing Firebase with classpath resource firebase/firebase-service-account.json" }
                        resource.inputStream
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to access Firebase service account file" }
            null
        }

        if (inputStream == null) {
            logger.warn { "Firebase credentials not found. Skipping Firebase initialization. Set GOOGLE_APPLICATION_CREDENTIALS or place firebase/firebase-service-account.json on classpath." }
            return
        }

        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(inputStream))
            .build()

        FirebaseApp.initializeApp(options)
        logger.info { "Firebase initialized" }
    }
}
