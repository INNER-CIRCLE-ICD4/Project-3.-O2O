package com.ddakta.matching.controller

import com.ddakta.matching.dto.request.RideCancelRequest
import com.ddakta.matching.dto.request.RideRatingRequest
import com.ddakta.matching.dto.request.RideRequestDto
import com.ddakta.matching.dto.request.RideStatusUpdateDto
import com.ddakta.matching.dto.response.RideResponseDto
import com.ddakta.matching.service.RideService
import com.ddakta.utils.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.*
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@Tag(name = "Ride Management", description = "운행 관리 API - 승객의 운행 요청 생성, 조회, 취소 등을 담당합니다")
@RestController
@RequestMapping("/api/v1/rides")
@Validated
class RideController(
    private val rideService: RideService
) {

    private val logger = KotlinLogging.logger {}

    @Operation(
        summary = "운행 요청",
        description = """새로운 운행을 요청합니다.
        운행 요청 시 승객의 위치, 목적지 정보를 포함해야 하며,
        시스템은 자동으로 주변 드라이버를 검색하여 매칭을 시도합니다."""
    )
    @ApiResponses(
        OpenApiResponse(
            responseCode = "201",
            description = "운행 요청 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = RideResponseDto::class),
                examples = [ExampleObject(
                    name = "Success Response",
                    value = """{
                        "success": true,
                        "data": {
                            "rideId": "550e8400-e29b-41d4-a716-446655440000",
                            "passengerId": "123e4567-e89b-12d3-a456-426614174000",
                            "status": "REQUESTED",
                            "pickupLocation": {
                                "latitude": 37.5665,
                                "longitude": 126.9780,
                                "address": "서울특별시 중구 세종대로 110"
                            },
                            "dropoffLocation": {
                                "latitude": 37.5172,
                                "longitude": 127.0473,
                                "address": "서울특별시 강남구 테헤란로 212"
                            },
                            "estimatedFare": {
                                "baseFare": 3800,
                                "distanceFare": 5200,
                                "timeFare": 1800,
                                "surgeMultiplier": 1.0,
                                "totalFare": 10800
                            },
                            "requestedAt": "2024-01-20T10:30:00"
                        },
                        "message": "운행 요청이 성공적으로 생성되었습니다"
                    }"""
                )]
            )]
        ),
        OpenApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (유효하지 않은 위치 정보 등)",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
                        "success": false,
                        "code": "INVALID_LOCATION",
                        "message": "유효하지 않은 위치 정보입니다"
                    }"""
                )]
            )]
        ),
        OpenApiResponse(
            responseCode = "409",
            description = "중복된 운행 요청",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
                        "success": false,
                        "code": "DUPLICATE_RIDE_REQUEST",
                        "message": "이미 진행 중인 운행이 있습니다"
                    }"""
                )]
            )]
        ),
        OpenApiResponse(
            responseCode = "503",
            description = "서비스 일시 중단",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
                        "success": false,
                        "code": "SERVICE_UNAVAILABLE",
                        "message": "현재 서비스를 이용할 수 없습니다"
                    }"""
                )]
            )]
        )
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRide(
        @Parameter(
            description = "운행 요청 정보",
            required = true,
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    name = "Ride Request Example",
                    value = """{
                        "passengerId": "123e4567-e89b-12d3-a456-426614174000",
                        "pickupLocation": {
                            "latitude": 37.5665,
                            "longitude": 126.9780,
                            "address": "서울특별시 중구 세종대로 110",
                            "h3Index": "8830e1d8dffffff"
                        },
                        "dropoffLocation": {
                            "latitude": 37.5172,
                            "longitude": 127.0473,
                            "address": "서울특별시 강남구 테헤란로 212",
                            "h3Index": "8830e1d89ffffff"
                        }
                    }"""
                )]
            )]
        )
        @Valid @RequestBody request: RideRequestDto
    ): ApiResponse<RideResponseDto> {
        logger.info { "Creating ride request for passenger: ${request.passengerId}" }

        val ride = rideService.createRide(request)

        return ApiResponse.success(
            data = ride,
            message = "운행 요청이 성공적으로 생성되었습니다"
        )
    }

    @Operation(
        summary = "운행 조회",
        description = "운행 ID로 운행 정보를 조회합니다. 운행의 현재 상태, 드라이버 정보, 예상 도착 시간 등을 확인할 수 있습니다."
    )
    @ApiResponses(
        OpenApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = RideResponseDto::class)
            )]
        ),
        OpenApiResponse(
            responseCode = "404",
            description = "운행을 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
                        "success": false,
                        "code": "RIDE_NOT_FOUND",
                        "message": "운행을 찾을 수 없습니다"
                    }"""
                )]
            )]
        )
    )
    @GetMapping("/{rideId}")
    fun getRide(
        @Parameter(description = "운행 ID", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable rideId: UUID
    ): ApiResponse<RideResponseDto> {
        logger.debug { "Getting ride: $rideId" }

        val ride = rideService.getRide(rideId)

        return ApiResponse.success(data = ride)
    }

    @Operation(summary = "운행 상태 업데이트", description = "운행 상태를 업데이트합니다")
    @PutMapping("/{rideId}/status")
    fun updateRideStatus(
        @Parameter(description = "운행 ID", required = true)
        @PathVariable rideId: UUID,
        @Valid @RequestBody statusUpdate: RideStatusUpdateDto,
        @Parameter(description = "액터 ID (드라이버 또는 시스템)", required = true)
        @RequestHeader("X-Actor-Id") actorId: UUID
    ): ApiResponse<RideResponseDto> {
        logger.info { "Updating ride $rideId status with event: ${statusUpdate.event}" }

        val ride = rideService.updateRideStatus(rideId, statusUpdate, actorId)

        return ApiResponse.success(
            data = ride,
            message = "운행 상태가 업데이트되었습니다"
        )
    }

    @Operation(
        summary = "운행 취소",
        description = "운행을 취소합니다. 취소 사유를 포함해야 하며, 상태에 따라 취소 수수료가 부과될 수 있습니다."
    )
    @ApiResponses(
        OpenApiResponse(
            responseCode = "200",
            description = "취소 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = RideResponseDto::class),
                examples = [ExampleObject(
                    value = """{
                        "success": true,
                        "data": {
                            "rideId": "550e8400-e29b-41d4-a716-446655440000",
                            "status": "CANCELLED",
                            "cancelledAt": "2024-01-20T10:35:00",
                            "cancelledBy": "PASSENGER",
                            "cancellationReason": "CHANGE_OF_PLANS",
                            "cancellationFee": 0
                        },
                        "message": "운행이 취소되었습니다"
                    }"""
                )]
            )]
        ),
        OpenApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (취소 불가능한 상태)",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
                        "success": false,
                        "code": "INVALID_RIDE_STATE",
                        "message": "현재 상태에서는 운행을 취소할 수 없습니다"
                    }"""
                )]
            )]
        ),
        OpenApiResponse(
            responseCode = "404",
            description = "운행을 찾을 수 없음"
        )
    )
    @PostMapping("/{rideId}/cancel")
    fun cancelRide(
        @Parameter(description = "운행 ID", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable rideId: UUID,
        @Parameter(
            description = "취소 요청 정보",
            required = true,
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
                        "reason": "CHANGE_OF_PLANS",
                        "details": "계획이 변경되었습니다"
                    }"""
                )]
            )]
        )
        @Valid @RequestBody cancelRequest: RideCancelRequest,
        @Parameter(description = "취소 요청자 ID", required = true)
        @RequestHeader("X-User-Id") userId: UUID
    ): ApiResponse<RideResponseDto> {
        logger.info { "Cancelling ride $rideId by user $userId with reason: ${cancelRequest.reason}" }

        val ride = rideService.cancelRide(rideId, cancelRequest.reason, userId)

        return ApiResponse.success(
            data = ride,
            message = "운행이 취소되었습니다"
        )
    }

    @Operation(
        summary = "승객의 활성 운행 조회",
        description = "승객의 현재 활성 운행을 조회합니다. 활성 운행은 REQUESTED, MATCHED, DRIVER_ASSIGNED, IN_PROGRESS 상태의 운행을 의미합니다."
    )
    @ApiResponses(
        OpenApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = RideResponseDto::class)
            )]
        ),
        OpenApiResponse(
            responseCode = "204",
            description = "활성 운행 없음"
        )
    )
    @GetMapping("/passengers/{passengerId}/active")
    fun getActiveRideForPassenger(
        @Parameter(description = "승객 ID", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
        @PathVariable passengerId: UUID
    ): ApiResponse<RideResponseDto?> {
        logger.debug { "Getting active ride for passenger: $passengerId" }

        val ride = rideService.getActiveRideForPassenger(passengerId)

        return if (ride != null) {
            ApiResponse.success(data = ride)
        } else {
            ApiResponse.success(
                data = null,
                message = "활성 운행이 없습니다"
            )
        }
    }

    @Operation(summary = "드라이버의 활성 운행 조회", description = "드라이버의 현재 활성 운행을 조회합니다")
    @GetMapping("/drivers/{driverId}/active")
    fun getActiveRideForDriver(
        @Parameter(description = "드라이버 ID", required = true)
        @PathVariable driverId: UUID
    ): ApiResponse<RideResponseDto?> {
        logger.debug { "Getting active ride for driver: $driverId" }

        val ride = rideService.getActiveRideForDriver(driverId)

        return if (ride != null) {
            ApiResponse.success(data = ride)
        } else {
            ApiResponse.success(
                data = null,
                message = "활성 운행이 없습니다"
            )
        }
    }

    @Operation(summary = "운행 이력 조회", description = "사용자의 운행 이력을 조회합니다")
    @GetMapping("/history")
    fun getRideHistory(
        @Parameter(description = "사용자 ID", required = true)
        @RequestParam userId: UUID,
        @Parameter(description = "드라이버 여부", required = false)
        @RequestParam(defaultValue = "false") isDriver: Boolean,
        @Parameter(description = "조회 개수", required = false)
        @RequestParam(defaultValue = "20") limit: Int,
        @Parameter(description = "오프셋", required = false)
        @RequestParam(defaultValue = "0") offset: Int
    ): ApiResponse<List<RideResponseDto>> {
        logger.debug { "Getting ride history for user: $userId (isDriver: $isDriver)" }

        val history = rideService.getRideHistory(userId, isDriver, limit, offset)

        return ApiResponse.success(
            data = history,
            message = "${history.size}개의 운행 이력을 조회했습니다"
        )
    }

    @Operation(summary = "운행 평가", description = "완료된 운행에 대한 평가를 등록합니다")
    @PostMapping("/{rideId}/ratings")
    fun rateRide(
        @Parameter(description = "운행 ID", required = true)
        @PathVariable rideId: UUID,
        @Valid @RequestBody ratingRequest: RideRatingRequest,
        @Parameter(description = "평가자 ID", required = true)
        @RequestHeader("X-User-Id") raterId: UUID
    ): ApiResponse<RideResponseDto> {
        logger.info { "Rating ride $rideId with ${ratingRequest.rating} stars" }

        val ride = rideService.updateRideRating(
            rideId = rideId,
            rating = ratingRequest.rating,
            isPassengerRating = ratingRequest.isPassengerRating,
            raterId = raterId
        )

        return ApiResponse.success(
            data = ride,
            message = "평가가 등록되었습니다"
        )
    }

    @Operation(summary = "운행 완료", description = "운행을 완료 처리합니다")
    @PostMapping("/{rideId}/complete")
    fun completeRide(
        @Parameter(description = "운행 ID", required = true)
        @PathVariable rideId: UUID,
        @Parameter(description = "주행 거리 (미터)", required = true)
        @RequestParam distance: Int,
        @Parameter(description = "주행 시간 (초)", required = true)
        @RequestParam duration: Int,
        @Parameter(description = "드라이버 ID", required = true)
        @RequestHeader("X-Driver-Id") driverId: UUID
    ): ApiResponse<RideResponseDto> {
        logger.info { "Completing ride $rideId - distance: ${distance}m, duration: ${duration}s" }

        val ride = rideService.completeRide(rideId, distance, duration, driverId)

        return ApiResponse.success(
            data = ride,
            message = "운행이 완료되었습니다"
        )
    }
}
