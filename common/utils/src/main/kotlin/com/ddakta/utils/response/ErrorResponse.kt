package com.ddakta.utils.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val error: ErrorDetail,
    val timestamp: Instant = Instant.now()
)

data class ErrorDetail(
    val code: String,
    val message: String,
    val details: List<FieldError>? = null,
    val traceId: String? = null
)

data class FieldError(
    val field: String,
    val message: String,
    val rejectedValue: Any? = null
)