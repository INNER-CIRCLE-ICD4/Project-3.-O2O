package com.ddakta.payment.exception

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class PaymentControllerAdvice {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<String> {
        return ResponseEntity.badRequest().body("잘못된 요청: ${ex.message}")
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<String> {
        return ResponseEntity.internalServerError().body("서버 에러 발생: ${ex.message}")
    }

}
