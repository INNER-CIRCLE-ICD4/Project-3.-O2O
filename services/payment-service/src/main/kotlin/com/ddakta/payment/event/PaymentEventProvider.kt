package com.ddakta.payment.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import kotlin.jvm.javaClass

@Service
class PaymentEventProvider(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 매칭서비스 && 푸쉬서비스에 결제 실패를 알린다.
    fun paymentFailed(event: PaymentFailedEvent): CompletableFuture<Void> {
        try {
            val message = objectMapper.writeValueAsString(event)
            log.info("이벤트 발행 시작: $event")

            return kafkaTemplate.send("payment-fail", message)
                .thenRun {log.info("이벤트 발행 완료: $event")}
                .exceptionally { throwable ->
                    log.error("이벤트 발행 실패: $event", throwable)
                    throw throwable
                }
        } catch (e: Exception) {
            log.error("이벤트 직렬화 실패", e)
            throw e
        }
    }

    // 성공 이벤트 발행
    fun paymentSuccess(event: PaymentSuccessEvent): CompletableFuture<Void> {
        try {
            val message = objectMapper.writeValueAsString(event)
            log.info("이벤트 발행 시작: $event")

            return kafkaTemplate.send("payment-success", message)
                .thenRun {log.info("이벤트 발행 완료: $event")}
                .exceptionally { throwable ->
                    log.error("이벤트 발행 실패: $event", throwable)
                    throw throwable
                }
        } catch (e: Exception) {
            log.error("이벤트 직렬화 실패", e)
            throw e
        }
    }

}

