package com.ddakta.matching.exception

import org.springframework.http.HttpStatus

class InvalidDriverCallStateException(message: String) : MatchingException(
    errorCode = "INVALID_DRIVER_CALL_STATE",
    message = message,
    httpStatus = HttpStatus.CONFLICT
)