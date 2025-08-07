package com.ddakta.notification.sender

import com.ddakta.notification.model.NotificationPayload
import com.ddakta.notification.model.NotificationTarget

interface NotificationSender {
    fun send(target: NotificationTarget, payload: NotificationPayload): Boolean

}
