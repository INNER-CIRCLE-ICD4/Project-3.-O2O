package com.ddakta.matching.service

import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.CancellationReason
import com.ddakta.matching.domain.enum.RideStatus
import com.ddakta.matching.dto.request.RideRequestDto
import com.ddakta.matching.dto.request.RideStatusUpdateDto
import com.ddakta.matching.dto.response.RideResponseDto
import java.util.*

interface RideService {
    fun createRide(request: RideRequestDto): RideResponseDto
    
    fun getRide(rideId: UUID): RideResponseDto
    
    fun updateRideStatus(
        rideId: UUID,
        statusUpdate: RideStatusUpdateDto,
        actorId: UUID
    ): RideResponseDto
    
    fun cancelRide(
        rideId: UUID,
        reason: CancellationReason,
        cancelledBy: UUID
    ): RideResponseDto
    
    fun getActiveRideForPassenger(passengerId: UUID): RideResponseDto?
    
    fun getActiveRideForDriver(driverId: UUID): RideResponseDto?
    
    fun getRideHistory(
        userId: UUID,
        isDriver: Boolean,
        limit: Int = 20,
        offset: Int = 0
    ): List<RideResponseDto>
    
    fun updateRideRating(
        rideId: UUID,
        rating: Int,
        isPassengerRating: Boolean,
        raterId: UUID
    ): RideResponseDto
    
    fun assignDriver(rideId: UUID, driverId: UUID): Ride
    
    fun completeRide(
        rideId: UUID,
        distance: Int,
        duration: Int,
        driverId: UUID
    ): RideResponseDto
}