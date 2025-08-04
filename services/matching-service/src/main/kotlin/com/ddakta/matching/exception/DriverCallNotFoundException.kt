package com.ddakta.matching.exception

import org.springframework.http.HttpStatus
import java.util.*

class DriverCallNotFoundException(callId: UUID) : MatchingException(
    errorCode = "DRIVER_CALL_NOT_FOUND",
    message = "Driver call not found: $callId",
    httpStatus = HttpStatus.NOT_FOUND
)