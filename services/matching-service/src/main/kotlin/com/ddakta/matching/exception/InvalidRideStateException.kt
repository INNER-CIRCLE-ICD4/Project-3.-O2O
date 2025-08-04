package com.ddakta.matching.exception

import org.springframework.http.HttpStatus

class InvalidRideStateException(message: String) : MatchingException(
    errorCode = "INVALID_RIDE_STATE",
    message = message,
    httpStatus = HttpStatus.CONFLICT
)