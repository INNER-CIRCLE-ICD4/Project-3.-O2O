package com.ddakta.utils.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val success: Boolean = true,
    val data: T? = null,
    val message: String? = null,
    val timestamp: Instant = Instant.now()
) {
    companion object {
        fun <T> success(data: T, message: String? = null): ApiResponse<T> {
            return ApiResponse(success = true, data = data, message = message)
        }
        
        fun <T> error(message: String, data: T? = null): ApiResponse<T> {
            return ApiResponse(success = false, data = data, message = message)
        }
        
        fun success(message: String): ApiResponse<Unit> {
            return ApiResponse(success = true, message = message)
        }
    }
}