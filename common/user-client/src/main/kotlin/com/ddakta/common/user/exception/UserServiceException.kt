package com.ddakta.common.user.exception

import java.util.UUID

open class UserServiceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class UserNotFoundException(userId: UUID) : UserServiceException("User not found: $userId")

class UnauthorizedException(message: String) : UserServiceException(message)

class UserTypeMismatchException(message: String) : UserServiceException(message)