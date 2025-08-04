package com.ddakta.matching.dto.request

import com.ddakta.matching.domain.enum.RideEvent
import jakarta.validation.constraints.NotNull

data class RideStatusUpdateDto(
    @field:NotNull
    val event: RideEvent,
    
    val metadata: Map<String, Any>? = null
)