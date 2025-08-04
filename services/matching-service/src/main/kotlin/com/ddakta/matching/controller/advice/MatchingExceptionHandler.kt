package com.ddakta.matching.controller.advice

import com.ddakta.matching.exception.*
import com.ddakta.utils.response.ApiResponse
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import java.time.LocalDateTime

@RestControllerAdvice
class MatchingExceptionHandler {
    
    private val logger = KotlinLogging.logger {}
    
    @ExceptionHandler(RideNotFoundException::class)
    fun handleRideNotFoundException(
        ex: RideNotFoundException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Ride not found: ${ex.message}" }
        
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(
                ApiResponse.error(
                    message = ex.message ?: "운행을 찾을 수 없습니다"
                )
            )
    }
    
    @ExceptionHandler(NoAvailableDriverException::class)
    fun handleNoAvailableDriverException(
        ex: NoAvailableDriverException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.info { "No available drivers: ${ex.message}" }
        
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                ApiResponse.error(
                    message = ex.message ?: "이용 가능한 드라이버가 없습니다"
                )
            )
    }
    
    @ExceptionHandler(InvalidRideStateException::class)
    fun handleInvalidRideStateException(
        ex: InvalidRideStateException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Invalid ride state: ${ex.message}" }
        
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                ApiResponse.error(
                    message = ex.message ?: "잘못된 운행 상태입니다"
                )
            )
    }
    
    @ExceptionHandler(InvalidRideStateTransitionException::class)
    fun handleInvalidRideStateTransitionException(
        ex: InvalidRideStateTransitionException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Invalid ride state transition: ${ex.message}" }
        
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                ApiResponse.error(
                    message = ex.message ?: "잘못된 운행 상태 전환입니다"
                )
            )
    }
    
    @ExceptionHandler(MatchingTimeoutException::class)
    fun handleMatchingTimeoutException(
        ex: MatchingTimeoutException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.info { "Matching timeout: ${ex.message}" }
        
        return ResponseEntity
            .status(HttpStatus.REQUEST_TIMEOUT)
            .body(
                ApiResponse.error(
                    message = ex.message ?: "매칭 시간이 초과되었습니다"
                )
            )
    }
    
    @ExceptionHandler(DriverCallNotFoundException::class)
    fun handleDriverCallNotFoundException(
        ex: DriverCallNotFoundException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Driver call not found: ${ex.message}" }
        
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(
                ApiResponse.error(
                    message = ex.message ?: "드라이버 호출을 찾을 수 없습니다"
                )
            )
    }
    
    @ExceptionHandler(DriverCallExpiredException::class)
    fun handleDriverCallExpiredException(
        ex: DriverCallExpiredException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.info { "Driver call expired: ${ex.message}" }
        
        return ResponseEntity
            .status(HttpStatus.GONE)
            .body(
                ApiResponse.error(
                    message = ex.message ?: "드라이버 호출이 만료되었습니다"
                )
            )
    }
    
    @ExceptionHandler(InvalidDriverCallStateException::class)
    fun handleInvalidDriverCallStateException(
        ex: InvalidDriverCallStateException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Invalid driver call state: ${ex.message}" }
        
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                ApiResponse.error(
                    message = ex.message ?: "잘못된 드라이버 호출 상태입니다"
                )
            )
    }
    
    @ExceptionHandler(RideAlreadyMatchedException::class)
    fun handleRideAlreadyMatchedException(
        ex: RideAlreadyMatchedException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Ride already matched: ${ex.message}" }
        
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                ApiResponse.error(
                    message = ex.message ?: "운행이 이미 매칭되었습니다"
                )
            )
    }
    
    @ExceptionHandler(DuplicateRideRequestException::class)
    fun handleDuplicateRideRequestException(
        ex: DuplicateRideRequestException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Duplicate ride request: ${ex.message}" }
        
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                ApiResponse.error(
                    message = ex.message ?: "중복된 운행 요청입니다"
                )
            )
    }
    
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        val errors = ex.bindingResult.fieldErrors
            .associate { fieldError: FieldError ->
                fieldError.field to (fieldError.defaultMessage ?: "Invalid value")
            }
        
        logger.warn { "Validation failed: $errors" }
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ApiResponse.error(
                    message = "입력값 검증에 실패했습니다: $errors"
                )
            )
    }
    
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Illegal argument: ${ex.message}" }
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ApiResponse.error(
                    message = ex.message ?: "잘못된 요청입니다"
                )
            )
    }
    
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.error(ex) { "Unexpected error occurred" }
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ApiResponse.error(
                    message = "서버 오류가 발생했습니다"
                )
            )
    }
    
    /**
     * WebSocket 메시지 처리 중 발생한 예외 처리
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    fun handleWebSocketException(ex: Exception): Map<String, Any> {
        logger.error(ex) { "WebSocket error occurred" }
        
        return mapOf(
            "error" to true,
            "errorCode" to (ex::class.simpleName ?: "UNKNOWN_ERROR"),
            "message" to (ex.message ?: "WebSocket 처리 중 오류가 발생했습니다"),
            "timestamp" to LocalDateTime.now()
        )
    }
}