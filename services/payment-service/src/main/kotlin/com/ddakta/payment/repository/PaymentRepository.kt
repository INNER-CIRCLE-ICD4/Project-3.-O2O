package com.ddakta.payment.repository

import com.ddakta.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentRepository: JpaRepository<Payment, Long> {
    fun findByPaymentId(paymentId: String): Payment?
    fun findByMatchId(matchId: Long): Payment?
        fun findByUserId(userId: UUID): List<Payment>
}
