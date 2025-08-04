package com.ddakta.matching.exception

import org.springframework.http.HttpStatus

class InvalidRideStateTransitionException(message: String) : MatchingException(
    errorCode = "INVALID_RIDE_STATE_TRANSITION",
    message = message,
    httpStatus = HttpStatus.CONFLICT
)