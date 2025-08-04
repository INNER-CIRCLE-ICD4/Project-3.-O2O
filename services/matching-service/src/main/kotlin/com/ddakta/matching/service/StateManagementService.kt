package com.ddakta.matching.service

import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.RideEvent
import com.ddakta.matching.domain.enum.RideStatus
import org.springframework.statemachine.StateMachine
import java.util.*

interface StateManagementService {
    fun processStateTransition(ride: Ride, event: RideEvent, actorId: UUID): Ride
    
    fun validateTransition(from: RideStatus, event: RideEvent): Boolean
    
    fun getStateMachine(rideId: UUID): StateMachine<RideStatus, RideEvent>
    
    fun releaseStateMachine(rideId: UUID)
}