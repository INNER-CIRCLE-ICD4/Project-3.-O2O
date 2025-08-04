package com.ddakta.payment.service

import com.ddakta.payment.event.DriveEndEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
@Profile("test")
class FakeEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val TOPIC = "drive-end"

    fun sendDriveEndEvent(event: DriveEndEvent): CompletableFuture<Void> {
        try {
            val message = objectMapper.writeValueAsString(event)
            log.info("이벤트 발행 시작: $event")

            return kafkaTemplate.send(TOPIC, message)
                .thenRun {
                    log.info("이벤트 발행 완료: $event")
                }
                .exceptionally { throwable ->
                    log.error("이벤트 발행 실패: $event", throwable)
                    throw throwable
                }

        } catch (e: Exception) {
            log.error("이벤트 직렬화 실패", e)
            throw e
        }
    }

    // 테스트를 위한 편의 메서드
    fun createAndSendDriveEndEvent(
        matchId: Long,
        userId: String,
        amount: Int,
        paymentMethod: String = "CARD"
    ): CompletableFuture<Void> {
        val event = DriveEndEvent(
            matchId = matchId,
            userId = userId,
            amount = amount,
            payMethod = paymentMethod
        )
        return sendDriveEndEvent(event)
    }
}

