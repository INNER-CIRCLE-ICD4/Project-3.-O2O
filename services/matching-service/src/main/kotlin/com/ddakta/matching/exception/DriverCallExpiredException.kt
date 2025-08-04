package com.ddakta.matching.exception

import org.springframework.http.HttpStatus

class DriverCallExpiredException(message: String) : MatchingException(
    errorCode = "DRIVER_CALL_EXPIRED",
    message = message,
    httpStatus = HttpStatus.GONE
)