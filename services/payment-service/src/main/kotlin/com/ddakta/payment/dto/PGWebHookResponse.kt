package com.ddakta.payment.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer
import java.time.Instant

data class PGWebHookResponse(
    val type: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonDeserialize(using = InstantDeserializer::class)
    val timestamp: Instant,
    val data: PGResponseData
)
data class PGResponseData(
    val stortId: String,
    val paymentId: String,
    val transactionId: String,
    val cancellationId: String? = null
)
