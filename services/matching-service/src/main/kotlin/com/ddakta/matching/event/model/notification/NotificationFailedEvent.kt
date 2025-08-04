package com.ddakta.matching.event.model.notification

import java.time.LocalDateTime
import java.util.*

data class NotificationFailedEvent(
    val notificationId: UUID,
    val recipientId: UUID,
    val recipientType: String,
    val reason: String,
    val failedAt: LocalDateTime,
    val retryCount: Int
)