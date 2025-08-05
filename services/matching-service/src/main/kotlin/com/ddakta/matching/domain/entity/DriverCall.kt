package com.ddakta.matching.domain.entity

import com.ddakta.domain.base.BaseEntity
import com.ddakta.matching.domain.enum.DriverCallStatus
import com.ddakta.matching.domain.vo.Location
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "driver_calls")
class DriverCall(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    val ride: Ride,

    @Column(nullable = false)
    val driverId: UUID,

    @Column(nullable = false)
    val sequenceNumber: Int,

    @Column(nullable = false)
    val expiresAt: LocalDateTime,

    val estimatedArrivalSeconds: Int? = null,

    val estimatedFare: BigDecimal? = null,

    val distanceToPickupMeters: Int? = null,

    @AttributeOverrides(
        AttributeOverride(name = "latitude", column = Column(name = "driver_latitude")),
        AttributeOverride(name = "longitude", column = Column(name = "driver_longitude")),
        AttributeOverride(name = "address", column = Column(name = "driver_address")),
        AttributeOverride(name = "h3Index", column = Column(name = "driver_h3"))
    )
    @Embedded
    val driverLocation: Location? = null
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DriverCallStatus = DriverCallStatus.PENDING
        private set

    val offeredAt: LocalDateTime = LocalDateTime.now()

    var respondedAt: LocalDateTime? = null
        private set

    fun accept() {
        require(status == DriverCallStatus.PENDING) {
            "Cannot accept call in status $status"
        }
        require(!isExpired()) {
            "Cannot accept expired call"
        }
        this.status = DriverCallStatus.ACCEPTED
        this.respondedAt = LocalDateTime.now()
    }

    fun reject() {
        require(status == DriverCallStatus.PENDING) {
            "Cannot reject call in status $status"
        }
        this.status = DriverCallStatus.REJECTED
        this.respondedAt = LocalDateTime.now()
    }

    fun expire() {
        require(status == DriverCallStatus.PENDING) {
            "Cannot expire call in status $status"
        }
        this.status = DriverCallStatus.EXPIRED
        this.respondedAt = LocalDateTime.now()
    }

    fun cancel() {
        require(status == DriverCallStatus.PENDING) {
            "Cannot cancel call in status $status"
        }
        this.status = DriverCallStatus.CANCELLED
        this.respondedAt = LocalDateTime.now()
    }

    fun isExpired(): Boolean {
        return LocalDateTime.now().isAfter(expiresAt)
    }

    fun isPending(): Boolean {
        return status == DriverCallStatus.PENDING && !isExpired()
    }
}