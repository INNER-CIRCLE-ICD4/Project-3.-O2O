package com.ddakta.notification.sender

import com.ddakta.notification.model.NotificationPayload
import com.ddakta.notification.model.NotificationTarget
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component("loggingNotificationSender")
class LoggingNotificationSender : NotificationSender {

    private val logger = KotlinLogging.logger {}

    override fun send(target: NotificationTarget, payload: NotificationPayload): Boolean {
        logger.info {
            "[MOCK SEND] user=${target.userId} device=${target.deviceToken} | title=${payload.title} | body=${payload.body}"
        }
        return true // 항상 성공했다고 가정하는 목 구현
    }
}
