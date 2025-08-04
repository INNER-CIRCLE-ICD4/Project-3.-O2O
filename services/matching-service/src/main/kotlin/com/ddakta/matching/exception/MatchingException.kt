package com.ddakta.matching.exception

import com.ddakta.utils.exception.BusinessException
import org.springframework.http.HttpStatus
import java.util.*

open class MatchingException(
    errorCode: String,
    message: String,
    httpStatus: HttpStatus = HttpStatus.BAD_REQUEST
) : BusinessException(errorCode, message, httpStatus)

class RideNotFoundException(rideId: UUID) : MatchingException(
    errorCode = "RIDE_NOT_FOUND",
    message = "Ride not found: $rideId",
    httpStatus = HttpStatus.NOT_FOUND
)

class NoAvailableDriverException(h3Index: String) : MatchingException(
    errorCode = "NO_AVAILABLE_DRIVERS",
    message = "No available drivers in area: $h3Index",
    httpStatus = HttpStatus.SERVICE_UNAVAILABLE
)

class InvalidRideStateException(message: String) : MatchingException(
    errorCode = "INVALID_RIDE_STATE",
    message = message,
    httpStatus = HttpStatus.CONFLICT
)

class InvalidRideStateTransitionException(message: String) : MatchingException(
    errorCode = "INVALID_RIDE_STATE_TRANSITION",
    message = message,
    httpStatus = HttpStatus.CONFLICT
)

class MatchingTimeoutException(rideId: UUID) : MatchingException(
    errorCode = "MATCHING_TIMEOUT",
    message = "Matching timeout for ride: $rideId",
    httpStatus = HttpStatus.REQUEST_TIMEOUT
)

class DriverCallNotFoundException(callId: UUID) : MatchingException(
    errorCode = "DRIVER_CALL_NOT_FOUND",
    message = "Driver call not found: $callId",
    httpStatus = HttpStatus.NOT_FOUND
)

class DriverCallExpiredException(message: String) : MatchingException(
    errorCode = "DRIVER_CALL_EXPIRED",
    message = message,
    httpStatus = HttpStatus.GONE
)

class InvalidDriverCallStateException(message: String) : MatchingException(
    errorCode = "INVALID_DRIVER_CALL_STATE",
    message = message,
    httpStatus = HttpStatus.CONFLICT
)

class RideAlreadyMatchedException(message: String) : MatchingException(
    errorCode = "RIDE_ALREADY_MATCHED",
    message = message,
    httpStatus = HttpStatus.CONFLICT
)

class DuplicateRideRequestException(message: String) : MatchingException(
    errorCode = "DUPLICATE_RIDE_REQUEST",
    message = message,
    httpStatus = HttpStatus.CONFLICT
)

class EventPublishException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)