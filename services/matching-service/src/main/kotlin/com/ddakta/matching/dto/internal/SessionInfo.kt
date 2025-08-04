package com.ddakta.matching.dto.internal

import java.util.*

data class SessionInfo(
    val userId: UUID,
    val instanceId: String,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val metadata: Map<String, Any> = emptyMap(),
    val migrationInProgress: Boolean = false
)