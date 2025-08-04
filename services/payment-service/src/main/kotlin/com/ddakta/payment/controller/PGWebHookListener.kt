package com.ddakta.payment.controller

import com.ddakta.payment.dto.PGWebHookResponse
import com.ddakta.payment.service.PaymentService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PGWebHookListener(
    private val paymentService: PaymentService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/api/v1/payments/webhook")
    fun receiveWebHook(pgData: PGWebHookResponse) {
        log.info("Payment Webhook received: {}", pgData)
        if(pgData.type == "Transaction.Paid"){
            paymentService.paymentSuccess(pgData.data.paymentId)
        }else if (pgData.type == "Transaction.Failed"){
            paymentService.paymentFail(pgData.data.paymentId)
        }
    }
}
