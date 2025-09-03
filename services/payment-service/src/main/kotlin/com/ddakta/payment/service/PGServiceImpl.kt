package com.ddakta.payment.service

import com.ddakta.payment.entity.Payment
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 개발 환경용 Mock PG 서비스 구현체
 * 실제 PG사와 연동하지 않고, 모든 결제 요청을 성공으로 가정하고 처리한다.
 */
@Service
class PGServiceImpl(
    // 비동기 웹훅을 시뮬레이션하기 위해 PaymentService에 대한 의존성 추가
    private val paymentService: PaymentService,
) : PGService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun processPayment(payment: Payment): String {
        val mockPgTransactionId = "tx_" + UUID.randomUUID().toString()
        log.info("[Mock PG] 결제 요청 수신. paymentId: ${payment.paymentId}, transactionId: $mockPgTransactionId 생성")

        // 실제 PG라면 여기서 외부 API를 호출하고, PG는 나중에 웹훅을 보낸다.
        // Mock 구현이므로, 즉시 성공했다고 가정하고 시스템에 바로 알린다.
        log.info("[Mock PG] 결제 요청이 즉시 성공했다고 가정하고, 성공 웹훅을 시뮬레이션합니다.")
        payment.updatePaymentId(mockPgTransactionId)
        paymentService.paymentSuccess(mockPgTransactionId)

        return mockPgTransactionId
    }

    override fun cancelPayment(payment: Payment): Boolean {
        log.info("[Mock PG] 결제 취소 요청 수신. paymentId: ${payment.paymentId}")
        // 항상 성공 반환
        log.info("[Mock PG] 결제 취소가 성공적으로 처리되었습니다.")
        return true
    }
}
