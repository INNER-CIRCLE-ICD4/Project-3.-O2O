package com.ddakta.notification.config

import com.ddakta.notification.sender.FcmNotificationSender
import com.ddakta.notification.sender.NotificationSender
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NotificationSenderConfig {
    @Bean
    fun notificationSender(fcmNotificationSender: FcmNotificationSender): NotificationSender {
        return fcmNotificationSender
    }
}

