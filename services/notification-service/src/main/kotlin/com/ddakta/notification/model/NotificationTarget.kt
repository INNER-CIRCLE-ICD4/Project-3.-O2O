package com.ddakta.notification.model

import java.util.UUID

data class NotificationTarget(
    val userId: UUID,
    val deviceToken: String
)
