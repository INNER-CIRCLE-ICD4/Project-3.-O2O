package com.ddakta.notification.sender

import com.ddakta.notification.model.NotificationPayload
import com.ddakta.notification.model.NotificationTarget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class NotificationSenderMockTest {

    @Test
    fun `should call send method with correct arguments`() {
        // given
        val sender: NotificationSender = mock()

        val target = NotificationTarget(
            userId = UUID.randomUUID(),
            deviceToken = "mock-token"
        )
        val payload = NotificationPayload(
            title = "제목",
            body = "본문",
            data = mapOf("screen" to "RideDetail"),
            deduplicationKey = "ride:matched:123"
        )

        // when
        sender.send(target, payload)

        // then
        verify(sender).send(target, payload) // 호출 여부만 확인
    }
}
