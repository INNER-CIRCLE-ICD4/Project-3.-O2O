package com.ddakta.notification.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream
import javax.annotation.PostConstruct

@Configuration
class FirebaseConfig {

    @PostConstruct
    fun initialize() {
        if (FirebaseApp.getApps().isEmpty()) {
            val serviceAccount =
                FileInputStream("src/main/resources/firebase/firebase-service-account.json")

            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build()

            FirebaseApp.initializeApp(options)
        }
    }
}
