package com.ddakta.payment.service

import com.ddakta.payment.entity.Payment
import com.ddakta.payment.event.DriveEndEvent
import com.ddakta.payment.event.PaymentEventProvider
import com.ddakta.payment.event.PaymentFailedEvent
import com.ddakta.payment.event.PaymentRetryEvent
import com.ddakta.payment.event.PaymentSuccessEvent
import com.ddakta.payment.repository.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.IllegalStateException
import java.util.UUID

@Service
@Transactional
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentEventProvider: PaymentEventProvider,
    private val pgService: PGService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 결제라인 생성 후 요청
    fun executePayment(event: DriveEndEvent) {
        validatePayment(event)
        log.info("결제 라인 생성 ... 주문ID=${event.matchId}, 금액=${event.amount}")
        val payment = paymentRepository.save(Payment.create(event))

        log.info("결제 요청 전송 ... 주문ID=${event.matchId}, 금액=${event.amount}")
        val paymentId = pgService.processPayment(payment)
        updatePaymentId(paymentId)
    }

    // 결제 검증 후 재요청
    fun retryPayment(event: PaymentRetryEvent) {
        val payment = getPaymentOrThrow(event.paymentId)

        pgService.processPayment(payment)
    }

    //PG 요청 시 응답받은 식별 ID update
    fun updatePaymentId(paymentId: String) {
        val payment = getPaymentOrThrow(paymentId)
        payment.updatePaymentId(paymentId)
        paymentRepository.save(payment)
    }

    // 결제 성공 시 DB 업데이트
    fun paymentSuccess(paymentId: String) {
        val payment = getPaymentOrThrow(paymentId)
        payment.paySuccess()
        paymentRepository.save(payment)
        paymentEventProvider.paymentSuccess(PaymentSuccessEvent(
            matchId = payment.matchId,
            userId = payment.userId.toString(),
            amount = payment.amount.value.toInt()
        ))
    }

    // 결제 실패 시 DB 업데이트 & 매칭서비스에 이벤트 발행
    fun paymentFail(paymentId: String) {
        val payment = getPaymentOrThrow(paymentId)
        require(payment.isFailed()) {"상태가 올바르지 않습니다."}
        payment.payFailed();
        paymentRepository.save(payment)
        paymentEventProvider.paymentFailed(PaymentFailedEvent(
            matchId = payment.matchId,
            userId = payment.userId.toString(),
            amount = payment.amount.value.toInt(),
            payMethod = payment.payMethod,
            reason = "결제실패"
        ))
    }

    @Transactional(readOnly = true)
    fun getPaymentOrThrow(paymentId: String): Payment {
        return paymentRepository.findByPaymentId(paymentId) ?: throw IllegalArgumentException("유효한 결제가 아닙니다.")
    }

    @Transactional(readOnly = true)
    fun validatePayment(event: DriveEndEvent) {
        require(event.amount <= 0) { "결제 금액은 0보다 커야 합니다" }
        paymentRepository.findByMatchId(event.matchId)?.run {
            throw IllegalStateException("이미 요청 완료된 건입니다.")
        }
    }

    fun findPaymentsByUserId(userId: UUID): List<Payment> {
        return paymentRepository.findByUserId(userId)
    }
}
