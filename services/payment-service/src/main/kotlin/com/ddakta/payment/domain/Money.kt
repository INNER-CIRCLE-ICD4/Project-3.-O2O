package com.ddakta.payment.domain

import jakarta.persistence.Embeddable
import java.math.BigDecimal

@Embeddable
class Money(
    val value: BigDecimal
) {
    constructor(value: Int) : this(BigDecimal(value))

    companion object {
        val ZERO = Money(BigDecimal.ZERO)
    }

    operator fun plus(money: Money): Money {
        return Money(this.value.add(money.value))
    }
    operator fun minus(money: Money): Money {
        return Money(this.value.subtract(money.value))
    }

}
