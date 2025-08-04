package com.ddakta.matching.exception

import org.springframework.http.HttpStatus
import java.util.*

class MatchingTimeoutException(rideId: UUID) : MatchingException(
    errorCode = "MATCHING_TIMEOUT",
    message = "Matching timeout for ride: $rideId",
    httpStatus = HttpStatus.REQUEST_TIMEOUT
)