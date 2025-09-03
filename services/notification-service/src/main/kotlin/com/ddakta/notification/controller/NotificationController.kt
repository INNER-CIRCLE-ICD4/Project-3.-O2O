package com.ddakta.notification.controller

import com.ddakta.notification.model.NotificationPayload
import com.ddakta.notification.model.NotificationTarget
import com.ddakta.notification.sender.NotificationSender
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationSender: NotificationSender
) {

    data class SendNotificationRequest(
        val userId: UUID,
        val deviceToken: String,
        val title: String,
        val body: String,
        val data: Map<String, String>? = emptyMap(),
        val deduplicationKey: String? = null
    )

    data class SendNotificationResponse(
        val success: Boolean
    )

    @PostMapping
    fun sendNotification(
        @RequestBody request: SendNotificationRequest
    ): ResponseEntity<SendNotificationResponse> {
        val target = NotificationTarget(
            userId = request.userId,
            deviceToken = request.deviceToken
        )

        val payload = NotificationPayload(
            title = request.title,
            body = request.body,
            data = request.data ?: emptyMap(),
            deduplicationKey = request.deduplicationKey
                ?: "${'$'}{request.userId}:${'$'}{System.currentTimeMillis()}"
        )

        val success = notificationSender.send(target, payload)
        return if (success) {
            ResponseEntity.ok(SendNotificationResponse(true))
        } else {
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(SendNotificationResponse(false))
        }
    }
}

