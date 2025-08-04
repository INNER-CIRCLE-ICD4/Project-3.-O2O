package com.ddakta.matching.exception

import org.springframework.http.HttpStatus
import java.util.*

class RideNotFoundException(rideId: UUID) : MatchingException(
    errorCode = "RIDE_NOT_FOUND",
    message = "Ride not found: $rideId",
    httpStatus = HttpStatus.NOT_FOUND
)