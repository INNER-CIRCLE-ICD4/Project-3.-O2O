package com.ddakta.utils.exception

import org.springframework.http.HttpStatus

open class BusinessException(
    val errorCode: String,
    override val message: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST
) : RuntimeException(message)

class ResourceNotFoundException(
    resource: String,
    id: Any,
    errorCode: String = "RESOURCE_NOT_FOUND"
) : BusinessException(
    errorCode = errorCode,
    message = "$resource not found: $id",
    httpStatus = HttpStatus.NOT_FOUND
)

class InvalidStateException(
    message: String,
    errorCode: String = "INVALID_STATE"
) : BusinessException(
    errorCode = errorCode,
    message = message,
    httpStatus = HttpStatus.CONFLICT
)

class UnauthorizedException(
    message: String = "인증이 필요합니다",
    errorCode: String = "UNAUTHORIZED"
) : BusinessException(
    errorCode = errorCode,
    message = message,
    httpStatus = HttpStatus.UNAUTHORIZED
)

class ForbiddenException(
    message: String = "권한이 부족합니다",
    errorCode: String = "FORBIDDEN"
) : BusinessException(
    errorCode = errorCode,
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)