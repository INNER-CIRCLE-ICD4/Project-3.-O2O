package com.ddakta.matching.controller

import com.ddakta.matching.dto.response.DriverCallResponseDto
import com.ddakta.matching.dto.response.DriverStatsResponseDto
import com.ddakta.matching.service.DriverCallService
import com.ddakta.utils.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.ExampleObject
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.*

@Tag(name = "Driver Call Management", description = "드라이버 호출 관리 API - 드라이버가 운행 호출을 수락/거절하거나 호출 상태를 확인할 수 있습니다")
@RestController
@RequestMapping("/api/v1/driver-calls")
@Validated
class DriverCallController(
    private val driverCallService: DriverCallService
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Operation(
        summary = "드라이버 호출 수락",
        description = """드라이버가 운행 호출을 수락합니다.
        호출을 수락하면 드라이버는 해당 운행에 배정되며, 승객에게 드라이버 정보와 예상 도착 시간이 전송됩니다.
        한 번 수락한 호출은 취소할 수 없으므로 신중하게 결정해야 합니다."""
    )
    @ApiResponses(
        OpenApiResponse(
            responseCode = "200",
            description = "호출 수락 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = DriverCallResponseDto::class),
                examples = [ExampleObject(
                    name = "Success Response",
                    value = """{
                        "success": true,
                        "data": {
                            "callId": "650e8400-e29b-41d4-a716-446655440001",
                            "rideId": "550e8400-e29b-41d4-a716-446655440000",
                            "driverId": "750e8400-e29b-41d4-a716-446655440002",
                            "status": "ACCEPTED",
                            "estimatedArrivalMinutes": 5,
                            "estimatedFare": 12500,
                            "pickupLocation": {
                                "latitude": 37.5665,
                                "longitude": 126.9780,
                                "address": "서울특별시 중구 세종대로 110"
                            },
                            "createdAt": "2024-01-20T10:30:00",
                            "acceptedAt": "2024-01-20T10:30:30",
                            "expiresAt": "2024-01-20T10:35:00"
                        },
                        "message": "호출을 수락했습니다"
                    }"""
                )]
            )]
        ),
        OpenApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (이미 다른 드라이버가 수락함)",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
                        "success": false,
                        "code": "CALL_ALREADY_ACCEPTED",
                        "message": "이미 다른 드라이버가 호출을 수락했습니다"
                    }"""
                )]
            )]
        ),
        OpenApiResponse(
            responseCode = "404",
            description = "호출을 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
                        "success": false,
                        "code": "DRIVER_CALL_NOT_FOUND",
                        "message": "호출을 찾을 수 없습니다"
                    }"""
                )]
            )]
        ),
        OpenApiResponse(
            responseCode = "410",
            description = "호출 시간 만료",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
                        "success": false,
                        "code": "DRIVER_CALL_EXPIRED",
                        "message": "호출 응답 시간이 만료되었습니다"
                    }"""
                )]
            )]
        )
    )
    @PostMapping("/{callId}/accept")
    fun acceptDriverCall(
        @Parameter(description = "호출 ID", required = true, example = "650e8400-e29b-41d4-a716-446655440001")
        @PathVariable callId: UUID,
        @Parameter(description = "드라이버 ID", required = true, example = "750e8400-e29b-41d4-a716-446655440002")
        @RequestHeader("X-Driver-Id") driverId: UUID
    ): ApiResponse<DriverCallResponseDto> {
        logger.info { "Driver $driverId accepting call $callId" }
        
        val driverCall = driverCallService.acceptCall(callId, driverId)
        
        return ApiResponse.success(
            data = driverCall,
            message = "호출을 수락했습니다"
        )
    }
    
    @Operation(
        summary = "드라이버 호출 거절",
        description = """드라이버가 운행 호출을 거절합니다.
        거절 사유를 포함할 수 있으며, 시스템은 다른 드라이버에게 호출을 전달할 수 있습니다."""
    )
    @ApiResponses(
        OpenApiResponse(
            responseCode = "200",
            description = "호출 거절 성공",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
                        "success": true,
                        "data": {
                            "callId": "650e8400-e29b-41d4-a716-446655440001",
                            "status": "REJECTED",
                            "rejectedAt": "2024-01-20T10:31:00"
                        },
                        "message": "호출을 거절했습니다"
                    }"""
                )]
            )]
        ),
        OpenApiResponse(
            responseCode = "404",
            description = "호출을 찾을 수 없음"
        ),
        OpenApiResponse(
            responseCode = "410",
            description = "호출 시간 만료"
        )
    )
    @PostMapping("/{callId}/reject")
    fun rejectDriverCall(
        @Parameter(description = "호출 ID", required = true, example = "650e8400-e29b-41d4-a716-446655440001")
        @PathVariable callId: UUID,
        @Parameter(description = "드라이버 ID", required = true, example = "750e8400-e29b-41d4-a716-446655440002")
        @RequestHeader("X-Driver-Id") driverId: UUID
    ): ApiResponse<DriverCallResponseDto> {
        logger.info { "Driver $driverId rejecting call $callId" }
        
        val driverCall = driverCallService.rejectCall(callId, driverId)
        
        return ApiResponse.success(
            data = driverCall,
            message = "호출을 거절했습니다"
        )
    }
    
    @Operation(summary = "드라이버의 활성 호출 조회", description = "드라이버에게 할당된 활성 호출 목록을 조회합니다")
    @GetMapping("/drivers/{driverId}/active")
    fun getActiveCallsForDriver(
        @Parameter(description = "드라이버 ID", required = true)
        @PathVariable driverId: UUID
    ): ApiResponse<List<DriverCallResponseDto>> {
        logger.debug { "Getting active calls for driver: $driverId" }
        
        val calls = driverCallService.getActiveCallsForDriver(driverId)
        val responses = calls.map { DriverCallResponseDto.from(it) }
        
        return ApiResponse.success(
            data = responses,
            message = "${responses.size}개의 활성 호출을 조회했습니다"
        )
    }
    
    @Operation(summary = "운행의 드라이버 호출 목록 조회", description = "특정 운행의 모든 드라이버 호출 내역을 조회합니다")
    @GetMapping("/rides/{rideId}")
    fun getCallsForRide(
        @Parameter(description = "운행 ID", required = true)
        @PathVariable rideId: UUID
    ): ApiResponse<List<DriverCallResponseDto>> {
        logger.debug { "Getting driver calls for ride: $rideId" }
        
        val calls = driverCallService.getCallsForRide(rideId)
        val responses = calls.map { DriverCallResponseDto.from(it) }
        
        return ApiResponse.success(
            data = responses,
            message = "${responses.size}개의 드라이버 호출을 조회했습니다"
        )
    }
    
    @Operation(summary = "드라이버 수락률 조회", description = "드라이버의 호출 수락률을 조회합니다")
    @GetMapping("/drivers/{driverId}/acceptance-rate")
    fun getDriverAcceptanceRate(
        @Parameter(description = "드라이버 ID", required = true)
        @PathVariable driverId: UUID
    ): ApiResponse<DriverStatsResponseDto> {
        logger.debug { "Getting acceptance rate for driver: $driverId" }
        
        val acceptanceRate = driverCallService.getDriverAcceptanceRate(driverId)
        
        return ApiResponse.success(
            data = DriverStatsResponseDto(
                driverId = driverId,
                acceptanceRate = acceptanceRate,
                acceptanceRatePercentage = String.format("%.1f%%", acceptanceRate * 100)
            )
        )
    }
    
    @Operation(summary = "드라이버 호출 만료", description = "타임아웃된 드라이버 호출을 만료 처리합니다")
    @PostMapping("/{callId}/expire")
    fun expireDriverCall(
        @Parameter(description = "호출 ID", required = true)
        @PathVariable callId: UUID,
        @Parameter(description = "시스템 ID", required = true)
        @RequestHeader("X-System-Id") systemId: String
    ): ApiResponse<DriverCallResponseDto> {
        logger.info { "System expiring driver call $callId" }
        
        val driverCall = driverCallService.expireDriverCall(callId)
        
        return ApiResponse.success(
            data = DriverCallResponseDto.from(driverCall),
            message = "호출이 만료되었습니다"
        )
    }
    
    @Operation(summary = "운행의 모든 호출 취소", description = "특정 운행의 모든 대기 중인 드라이버 호출을 취소합니다")
    @DeleteMapping("/rides/{rideId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelAllCallsForRide(
        @Parameter(description = "운행 ID", required = true)
        @PathVariable rideId: UUID,
        @Parameter(description = "시스템 ID", required = true)
        @RequestHeader("X-System-Id") systemId: String
    ) {
        logger.info { "System cancelling all calls for ride: $rideId" }
        
        driverCallService.cancelAllCallsForRide(rideId)
    }
}