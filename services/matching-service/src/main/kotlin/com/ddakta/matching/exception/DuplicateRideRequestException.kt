package com.ddakta.matching.exception

import org.springframework.http.HttpStatus

class DuplicateRideRequestException(message: String) : MatchingException(
    errorCode = "DUPLICATE_RIDE_REQUEST",
    message = message,
    httpStatus = HttpStatus.CONFLICT
)