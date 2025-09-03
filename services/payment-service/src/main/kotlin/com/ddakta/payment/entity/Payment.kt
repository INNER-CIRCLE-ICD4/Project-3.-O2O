package com.ddakta.payment.entity

import com.ddakta.domain.base.BaseEntity
import com.ddakta.payment.domain.Money
import com.ddakta.payment.domain.PaymentMethod
import com.ddakta.payment.domain.PaymentStatus
import com.ddakta.payment.event.DriveEndEvent
import jakarta.persistence.*
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.util.*

@Entity
@EnableJpaAuditing
class Payment(

    val userId: UUID,

    val matchId: Long,

    var paymentId: String? = null,

    @Embedded
    @AttributeOverride( name = "value", column = Column(name = "total_price", precision = 19, scale = 2))
    val amount: Money,

    @Enumerated(EnumType.STRING)
    var status: PaymentStatus,

    val payMethod: String,

    ): BaseEntity() {
    companion object {
        fun create(event: DriveEndEvent): Payment {
            return Payment(
                userId = UUID.fromString(event.userId),
                matchId = event.matchId,
                amount = Money(event.amount),
                status = PaymentStatus.PENDING,
                payMethod = event.payMethod,
            )
        }
    }

    fun paySuccess() {
        require(this.status == PaymentStatus.PENDING || this.status == PaymentStatus.FAILED) {"결제대기 혹은 결제실패 된 건만 완료처리 가능합니다."}
        this.status = PaymentStatus.SUCCESS
    }

    fun payFailed() {
        require(this.status == PaymentStatus.PENDING ) {"결제대기건만 실패처리 가능합니다."}
        this.status = PaymentStatus.FAILED
    }

    fun updatePaymentId(paymentId: String) {
        require(this.status == PaymentStatus.PENDING) {"대기상태의 결제만 결제번호가 부여 가능합니다."}
        this.paymentId = paymentId
    }

    fun isFailed(): Boolean {
        return this.status == PaymentStatus.FAILED
    }

    fun isCancelled(): Boolean {
        return this.status == PaymentStatus.CANCELLED
    }

    fun cancel() {
        require(this.status == PaymentStatus.SUCCESS) {"성공한 결제만 취소할 수 있습니다."}
        this.status = PaymentStatus.CANCELLED
    }
}
