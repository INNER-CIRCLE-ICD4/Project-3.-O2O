package com.ddakta.payment.controller

import com.ddakta.payment.dto.PGWebHookResponse
import com.ddakta.payment.service.PaymentService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

@RestController
class PGWebHookListener(
    private val paymentService: PaymentService,
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/api/v1/payments/webhook")
    fun receiveWebHook(pgData: PGWebHookResponse) {
        log.info("Payment Webhook received: {}", pgData)

        // 멱등성 체크: 동일한 웹훅을 중복 처리하지 않도록 방지
        val webhookKey = "webhook:processed:${pgData.data.transactionId ?: pgData.data.cancellationId}"
        val isNew = redisTemplate.opsForValue().setIfAbsent(webhookKey, "1", 1, TimeUnit.DAYS)

        if (isNew == false) {
            log.warn("이미 처리된 웹훅입니다. key: $webhookKey")
            return
        }

        when (pgData.type) {
            "Transaction.Paid" -> paymentService.paymentSuccess(pgData.data.paymentId)
            "Transaction.Failed" -> paymentService.paymentFail(pgData.data.paymentId)
            "Transaction.Cancelled" -> paymentService.cancelPayment(pgData.data.paymentId)
            else -> log.warn("처리되지 않은 웹훅 타입입니다: ${pgData.type}")
        }
    }
}
