package com.ddakta.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.util.UUID

class PaymentMethod(

    @Column(nullable = false)
    val cardCompany: String,

    @Column(nullable = false)
    val cardNumberMasked: String,  // 예: "****-****-****-1234"

    @Column(nullable = false)
    val isDefault: Boolean = false,

    @Column(nullable = false)
    val userId: UUID

)
