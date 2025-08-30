package com.ddakta.payment.controller

import com.ddakta.payment.entity.Payment
import com.ddakta.payment.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    @GetMapping("/{userId}")
    fun getPayment(@PathVariable userId: UUID): ResponseEntity<List<Payment>> {
        return ResponseEntity.ok(paymentService.findPaymentsByUserId(userId))
    }

    @PostMapping("/{paymentId}/cancel")
    fun cancelPayment(@PathVariable paymentId: String): ResponseEntity<Unit> {
        paymentService.cancelPayment(paymentId)
        return ResponseEntity.ok().build()
    }
}
