package com.ddakta.matching.service

import com.ddakta.matching.domain.entity.MatchingRequest
import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.dto.internal.MatchingResult
import java.util.*

interface MatchingService {
    fun processMatchingBatch(): List<MatchingResult>
    
    fun createMatchingRequest(ride: Ride): MatchingRequest
    
    fun cancelMatchingRequest(rideId: UUID)
    
    fun getActiveMatchingRequests(limit: Int = 100): List<MatchingRequest>
    
    fun handleMatchingTimeout(rideId: UUID)
    
    fun retryMatching(rideId: UUID): MatchingResult?
}