package com.ddakta.matching.domain.entity

import com.ddakta.domain.base.BaseEntity
import com.ddakta.matching.domain.enum.RideEvent
import com.ddakta.matching.domain.enum.RideStatus
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "ride_state_transitions")
class RideStateTransition(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    var ride: Ride,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val fromStatus: RideStatus,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val toStatus: RideStatus,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val event: RideEvent,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val metadata: Map<String, Any>? = null
) : BaseEntity() {
    
    companion object {
        private val objectMapper = jacksonObjectMapper()
    }
    
    fun getMetadataAsString(): String? {
        return metadata?.let { objectMapper.writeValueAsString(it) }
    }
}