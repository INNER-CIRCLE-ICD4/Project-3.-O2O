package com.ddakta.matching.domain.entity

import com.ddakta.domain.base.BaseEntity
import com.ddakta.matching.domain.enum.*
import com.ddakta.matching.domain.vo.Fare
import com.ddakta.matching.domain.vo.Location
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "rides")
class Ride(
    @Column(nullable = false)
    val passengerId: UUID,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "latitude", column = Column(name = "pickup_latitude")),
        AttributeOverride(name = "longitude", column = Column(name = "pickup_longitude")),
        AttributeOverride(name = "address", column = Column(name = "pickup_address")),
        AttributeOverride(name = "h3Index", column = Column(name = "pickup_h3"))
    )
    val pickupLocation: Location,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "latitude", column = Column(name = "dropoff_latitude")),
        AttributeOverride(name = "longitude", column = Column(name = "dropoff_longitude")),
        AttributeOverride(name = "address", column = Column(name = "dropoff_address")),
        AttributeOverride(name = "h3Index", column = Column(name = "dropoff_h3"))
    )
    val dropoffLocation: Location,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "baseFare", column = Column(name = "base_fare")),
        AttributeOverride(name = "surgeMultiplier", column = Column(name = "surge_multiplier")),
        AttributeOverride(name = "totalFare", column = Column(name = "total_fare")),
        AttributeOverride(name = "currency", column = Column(name = "currency"))
    )
    var fare: Fare? = null
) : BaseEntity() {

    var driverId: UUID? = null
        private set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RideStatus = RideStatus.REQUESTED
        private set

    // 차량 유형 (STANDARD, PREMIUM, XL)
    @Column(nullable = false)
    var vehicleType: String = "STANDARD"
        private set

    // 결제 수단 ID
    var paymentMethodId: UUID? = null
        private set

    // 드라이버 도착 예상 시간
    var estimatedArrivalAt: LocalDateTime? = null
        private set

    var distanceMeters: Int? = null
        private set

    var durationSeconds: Int? = null
        private set

    val requestedAt: LocalDateTime = LocalDateTime.now()
    
    var matchedAt: LocalDateTime? = null
        private set
    
    var pickupAt: LocalDateTime? = null
        private set
    
    // 승객 탑승 시간 (pickup과 다름 - pickup은 도착, pickedUpAt은 실제 탑승)
    var pickedUpAt: LocalDateTime? = null
        private set
    
    var startedAt: LocalDateTime? = null
        private set
    
    var completedAt: LocalDateTime? = null
        private set
    
    var cancelledAt: LocalDateTime? = null
        private set

    @Enumerated(EnumType.STRING)
    var cancellationReason: CancellationReason? = null
        private set

    @Enumerated(EnumType.STRING)
    var cancelledBy: CancelledBy? = null
        private set

    var ratingByPassenger: Int? = null
        private set
    
    var ratingByDriver: Int? = null
        private set

    @Version
    var version: Long = 0

    @OneToMany(mappedBy = "ride", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val driverCalls: MutableList<DriverCall> = mutableListOf()

    @OneToMany(mappedBy = "ride", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val stateTransitions: MutableList<RideStateTransition> = mutableListOf()

    fun assignDriver(driverId: UUID) {
        require(status == RideStatus.MATCHED) {
            "Cannot assign driver in status $status"
        }
        this.driverId = driverId
        this.status = RideStatus.DRIVER_ASSIGNED
        this.matchedAt = LocalDateTime.now()
    }
    
    fun arrivedAtPickup() {
        require(status == RideStatus.DRIVER_ASSIGNED) {
            "Cannot arrive at pickup in status $status"
        }
        this.status = RideStatus.ARRIVED_AT_PICKUP
        this.pickupAt = LocalDateTime.now()
    }
    
    fun startTrip() {
        require(status == RideStatus.ARRIVED_AT_PICKUP) {
            "Cannot start trip in status $status"
        }
        this.status = RideStatus.ON_TRIP
        this.startedAt = LocalDateTime.now()
        this.pickedUpAt = LocalDateTime.now()
    }
    
    fun setVehicleType(vehicleType: String) {
        this.vehicleType = vehicleType
    }
    
    fun setPaymentMethod(paymentMethodId: UUID) {
        this.paymentMethodId = paymentMethodId
    }
    
    fun setEstimatedArrival(estimatedArrivalAt: LocalDateTime) {
        this.estimatedArrivalAt = estimatedArrivalAt
    }

    fun updateStatus(newStatus: RideStatus) {
        require(status.canTransitionTo(newStatus)) {
            "Cannot transition from $status to $newStatus"
        }
        
        when (newStatus) {
            RideStatus.ARRIVED_AT_PICKUP -> this.pickupAt = LocalDateTime.now()
            RideStatus.ON_TRIP -> this.startedAt = LocalDateTime.now()
            else -> {}
        }
        
        this.status = newStatus
    }

    fun startRide() {
        require(status == RideStatus.ARRIVED_AT_PICKUP) {
            "Cannot start ride in status $status"
        }
        this.status = RideStatus.ON_TRIP
        this.startedAt = LocalDateTime.now()
    }

    fun completeRide(distance: Int, duration: Int, totalFare: BigDecimal) {
        require(status == RideStatus.ON_TRIP) {
            "Cannot complete ride in status $status"
        }
        this.status = RideStatus.COMPLETED
        this.completedAt = LocalDateTime.now()
        this.distanceMeters = distance
        this.durationSeconds = duration
        this.fare = this.fare?.copy(totalFare = totalFare)
    }

    fun cancel(reason: CancellationReason, cancelledBy: CancelledBy) {
        require(status.isCancellable()) {
            "Cannot cancel ride in status $status"
        }
        this.status = RideStatus.CANCELLED
        this.cancelledAt = LocalDateTime.now()
        this.cancellationReason = reason
        this.cancelledBy = cancelledBy
    }

    fun addDriverCall(driverCall: DriverCall) {
        driverCalls.add(driverCall)
        driverCall.ride = this
    }

    fun recordStateTransition(event: RideEvent, metadata: Map<String, Any>? = null) {
        val transition = RideStateTransition(
            ride = this,
            fromStatus = this.status,
            toStatus = event.targetStatus,
            event = event,
            metadata = metadata
        )
        stateTransitions.add(transition)
    }

    fun updatePassengerRating(rating: Int) {
        require(rating in 1..5) { "Rating must be between 1 and 5" }
        require(status == RideStatus.COMPLETED) { "Can only rate completed rides" }
        this.ratingByPassenger = rating
    }

    fun updateDriverRating(rating: Int) {
        require(rating in 1..5) { "Rating must be between 1 and 5" }
        require(status == RideStatus.COMPLETED) { "Can only rate completed rides" }
        this.ratingByDriver = rating
    }

    fun isActive(): Boolean {
        return status.isActive()
    }
}