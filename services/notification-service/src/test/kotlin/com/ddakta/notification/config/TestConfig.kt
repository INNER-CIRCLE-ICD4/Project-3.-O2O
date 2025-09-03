package com.ddakta.notification.config

import com.ddakta.notification.model.NotificationTarget
import com.ddakta.notification.model.NotificationPayload
import com.ddakta.notification.sender.NotificationSender
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("test")
@Configuration
class TestConfig {

    @Bean("fcmNotificationSender")
    fun mockFcmNotificationSender(): NotificationSender {
        return object : NotificationSender {
            override fun send(target: NotificationTarget, payload: NotificationPayload): Boolean {
                println("Mock FCM Sender: Always returning true")
                return true
            }
        }
    }
}
