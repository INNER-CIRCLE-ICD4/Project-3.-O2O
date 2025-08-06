package com.ddakta.location.exception

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException) =
        ResponseEntity.status(ex.statusCode).body(mapOf("error" to ex.reason))

    @ExceptionHandler(Exception::class)
    fun handleAll(ex: Exception) =
        ResponseEntity.internalServerError().body(mapOf("error" to "Internal server error"))
}
