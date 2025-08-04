package com.ddakta.matching.exception

import org.springframework.http.HttpStatus

class NoAvailableDriverException(h3Index: String) : MatchingException(
    errorCode = "NO_AVAILABLE_DRIVERS",
    message = "No available drivers in area: $h3Index",
    httpStatus = HttpStatus.SERVICE_UNAVAILABLE
)