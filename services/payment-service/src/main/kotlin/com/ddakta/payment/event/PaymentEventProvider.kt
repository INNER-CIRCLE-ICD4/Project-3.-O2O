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
    fun paymentFailed(event: PaymentFailedEvent) {
        try {
            val message = objectMapper.writeValueAsString(event)
            log.info("이벤트 발행 시작: $event")

            kafkaTemplate.send("payment-fail", message)
        } catch (e: Exception) {
            log.error("결제실패 이벤트 처리 중 오류 발생", e)
            throw e
        }
    }

    // 성공 이벤트 발행
    fun paymentSuccess(event: PaymentSuccessEvent) {
        try {
            val message = objectMapper.writeValueAsString(event)
            log.info("이벤트 발행 시작: $event")
            kafkaTemplate.send("payment-success", message)
        } catch (e: Exception) {
            log.error("결제성공 이벤트 처리 중 오류 발생", e)
            throw e
        }
    }

    // 취소 이벤트 발행
    fun paymentCancelled(event: PaymentCancelledEvent) {
        try {
            val message = objectMapper.writeValueAsString(event)
            log.info("이벤트 발행 시작: $event")

            kafkaTemplate.send("payment-cancelled", message)
        } catch (e: Exception) {
            log.error("결제취소 이벤트 처리 중 오류 발생", e)
            throw e
        }
    }

}

