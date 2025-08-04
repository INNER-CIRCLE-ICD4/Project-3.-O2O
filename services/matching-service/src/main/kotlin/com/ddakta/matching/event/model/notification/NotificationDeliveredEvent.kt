package com.ddakta.matching.event.model.notification

import java.time.LocalDateTime
import java.util.*

data class NotificationDeliveredEvent(
    val notificationId: UUID,
    val recipientId: UUID,
    val recipientType: String, // PASSENGER, DRIVER
    val referenceId: UUID?, // rideId or other reference
    val referenceType: String?, // RIDE, DRIVER_CALL, etc
    val deliveredAt: LocalDateTime
)