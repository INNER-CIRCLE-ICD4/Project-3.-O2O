package com.ddakta.matching.exception

import org.springframework.http.HttpStatus

class RideAlreadyMatchedException(message: String) : MatchingException(
    errorCode = "RIDE_ALREADY_MATCHED",
    message = message,
    httpStatus = HttpStatus.CONFLICT
)