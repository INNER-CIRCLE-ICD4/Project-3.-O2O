package com.ddakta.payment.service

import com.ddakta.payment.entity.Payment

interface PGService {
    fun processPayment(payment: Payment): String
}
