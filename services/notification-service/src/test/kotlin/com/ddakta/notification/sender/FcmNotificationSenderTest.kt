package com.ddakta.notification.sender

import com.ddakta.notification.NotificationApplication
import com.ddakta.notification.model.NotificationPayload
import com.ddakta.notification.model.NotificationTarget
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AbstractBooleanAssert
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@SpringBootTest(classes = [NotificationApplication::class])
@ActiveProfiles("test")
class FcmNotificationSenderTest {

    @Autowired
    @Qualifier("fcmNotificationSender")
    lateinit var sender: NotificationSender

    @Test
    fun `푸시 알림을 전송한다`() {
        val payload = NotificationPayload(
            title = "FCM 테스트 제목!!",
            body = "이것은 테스트 메시지입니다~",
            data = mapOf("rideId" to "1234-5678"),
            deduplicationKey = UUID.randomUUID().toString()
        )

        val target = NotificationTarget(
            userId = UUID.randomUUID(),
            deviceToken = "dAr4Xt8UpeBtio0HgTNbI1:APA91bGNpcZglqfOgUyp7cTVA7Xbpepb9W3LHEaqMlmY34xyLe92QKZ5Nig55_xHVoiW9KUm5Txq7WrmsLlf0yy6Rj4-mf4mzq3AExllaJBvQ6Z7Z316fmo"
        )

        val result = sender.send(target, payload)

        // ✅ Boolean으로 결과 검증
        assertThat(result).isEqualTo(true)
        println("✅ FCM 전송 성공 여부: $result")
    }
}
