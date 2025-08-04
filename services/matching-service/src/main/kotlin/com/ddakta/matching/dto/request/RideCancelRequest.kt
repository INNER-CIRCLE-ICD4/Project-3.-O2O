package com.ddakta.matching.dto.request

import com.ddakta.matching.domain.enum.CancellationReason
import jakarta.validation.constraints.NotNull

data class RideCancelRequest(
    @field:NotNull
    val reason: CancellationReason,
    
    val additionalInfo: String? = null
)