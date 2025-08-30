package com.ddakta.payment.event

data class DriveEndEvent(
    val matchId: Long,
    val userId: String,
    val amount: Int,
    val payMethod: String
)
