package com.ddakta.notification.model

data class NotificationPayload(
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val deduplicationKey: String
)
