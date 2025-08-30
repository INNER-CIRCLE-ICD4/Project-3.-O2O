package com.ddakta.payment.event

import com.ddakta.payment.service.PaymentService
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class PaymentEventListener(
    private val objectMapper: ObjectMapper,
    private val paymentService: PaymentService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["drive-end"],
        groupId = "payment-service"
    )
    fun driveEndEventListener(record: ConsumerRecord<String, String>) {
        try {
            val message = record.value()
            val event: DriveEndEvent = objectMapper.readValue(message, DriveEndEvent::class.java)
            log.info("Drive-End 이벤트 수신: $event")
            paymentService.executePayment(event)  // processPayment 메서드 호출 추가
        } catch (e: Exception) {
            log.error("결제 이벤트 처리 중 오류 발생: ${record.value()}", e)
            throw e  // 재시도를 위해 예외를 던집니다
        }
    }

    @KafkaListener(
        topics = ["retry-payment"],
        groupId = "payment-service"
    )
    fun paymentRetryEventListener(record: ConsumerRecord<String, String>) {
        try {
            val message = record.value()
            val event: PaymentRetryEvent = objectMapper.readValue(message, PaymentRetryEvent::class.java)
            log.info("Payment Retry 이벤트 수신: $event")
            paymentService.retryPayment(event)  // processPayment 메서드 호출 추가
        } catch (e: Exception) {
            log.error("결제 이벤트 처리 중 오류 발생: ${record.value()}", e)
            throw e  // 재시도를 위해 예외를 던집니다
        }
    }

}
