package com.ddakta.notification.sender

import com.ddakta.notification.model.NotificationPayload
import com.ddakta.notification.model.NotificationTarget
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component("fcmNotificationSender")
class FcmNotificationSender : NotificationSender {

    private val logger = LoggerFactory.getLogger(FcmNotificationSender::class.java)

    override fun send(target: NotificationTarget, payload: NotificationPayload): Boolean {
        return try {
            val message = Message.builder()
                .setToken(target.deviceToken)
                .putAllData(payload.data)
                .setNotification(
                    com.google.firebase.messaging.Notification.builder()
                        .setTitle("🔔 알림 도착: ${payload.title}")
                        .setBody(payload.body)
                        .build()
                )
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            logger.info("FCM message sent to ${target.userId}: $response")
            true
        } catch (e: Exception) {
            logger.error("🔥 FCM 전송 실패: ${e.message}", e)
            false
        }
    }
}
