package com.ddakta.matching.controller

import com.ddakta.matching.dto.internal.MatchingResult
import com.ddakta.matching.dto.response.MatchingRequestResponseDto
import com.ddakta.matching.dto.response.SurgePriceResponseDto
import com.ddakta.matching.service.MatchingService
import com.ddakta.matching.service.SurgePriceService
import com.ddakta.utils.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.*

@Tag(name = "Matching Management", description = "매칭 관리 API")
@RestController
@RequestMapping("/api/v1/matching")
@Validated
class MatchingController(
    private val matchingService: MatchingService,
    private val surgePriceService: SurgePriceService
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Operation(summary = "매칭 재시도", description = "실패한 운행에 대해 매칭을 재시도합니다")
    @PostMapping("/rides/{rideId}/retry")
    fun retryMatching(
        @Parameter(description = "운행 ID", required = true)
        @PathVariable rideId: UUID
    ): ApiResponse<MatchingResult?> {
        logger.info { "Retrying matching for ride: $rideId" }
        
        val result = matchingService.retryMatching(rideId)
        
        return if (result != null && result.success) {
            ApiResponse.success(
                data = result,
                message = "매칭이 성공적으로 완료되었습니다"
            )
        } else {
            ApiResponse.success(
                data = result,
                message = "매칭 가능한 드라이버가 없습니다"
            )
        }
    }
    
    @Operation(summary = "매칭 요청 취소", description = "진행 중인 매칭 요청을 취소합니다")
    @DeleteMapping("/rides/{rideId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelMatchingRequest(
        @Parameter(description = "운행 ID", required = true)
        @PathVariable rideId: UUID
    ) {
        logger.info { "Cancelling matching request for ride: $rideId" }
        
        matchingService.cancelMatchingRequest(rideId)
    }
    
    @Operation(summary = "활성 매칭 요청 조회", description = "현재 활성화된 매칭 요청 목록을 조회합니다")
    @GetMapping("/active")
    fun getActiveMatchingRequests(
        @Parameter(description = "조회 개수", required = false)
        @RequestParam(defaultValue = "100") limit: Int
    ): ApiResponse<List<MatchingRequestResponseDto>> {
        logger.debug { "Getting active matching requests (limit: $limit)" }
        
        val requests = matchingService.getActiveMatchingRequests(limit)
        val responses = requests.map { MatchingRequestResponseDto.from(it) }
        
        return ApiResponse.success(
            data = responses,
            message = "${responses.size}개의 활성 매칭 요청을 조회했습니다"
        )
    }
    
    @Operation(summary = "매칭 타임아웃 처리", description = "타임아웃된 매칭 요청을 처리합니다")
    @PostMapping("/rides/{rideId}/timeout")
    fun handleMatchingTimeout(
        @Parameter(description = "운행 ID", required = true)
        @PathVariable rideId: UUID
    ): ApiResponse<Unit> {
        logger.info { "Handling matching timeout for ride: $rideId" }
        
        matchingService.handleMatchingTimeout(rideId)
        
        return ApiResponse.success(
            data = Unit,
            message = "매칭 타임아웃이 처리되었습니다"
        )
    }
    
    @Operation(summary = "지역별 서지 가격 조회", description = "특정 지역의 현재 서지 가격을 조회합니다")
    @GetMapping("/surge/{h3Index}")
    fun getSurgePrice(
        @Parameter(description = "H3 인덱스", required = true)
        @PathVariable h3Index: String
    ): ApiResponse<SurgePriceResponseDto> {
        logger.debug { "Getting surge price for area: $h3Index" }
        
        val multiplier = surgePriceService.getCurrentSurgeMultiplier(h3Index)
        
        return ApiResponse.success(
            data = SurgePriceResponseDto(
                h3Index = h3Index,
                multiplier = multiplier,
                isActive = multiplier > 1.0
            )
        )
    }
    
    @Operation(summary = "여러 지역 서지 가격 조회", description = "여러 지역의 서지 가격을 한번에 조회합니다")
    @PostMapping("/surge/batch")
    fun getBatchSurgePrices(
        @Parameter(description = "H3 인덱스 목록", required = true)
        @RequestBody h3Indexes: List<String>
    ): ApiResponse<Map<String, Double>> {
        logger.debug { "Getting surge prices for ${h3Indexes.size} areas" }
        
        val prices = surgePriceService.getSurgePricesInArea(h3Indexes)
        
        return ApiResponse.success(
            data = prices,
            message = "${prices.size}개 지역의 서지 가격을 조회했습니다"
        )
    }
    
    @Operation(summary = "서지 가격 업데이트", description = "특정 지역의 서지 가격을 수동으로 업데이트합니다 (관리자 전용)")
    @PutMapping("/surge/{h3Index}")
    fun updateSurgePrice(
        @Parameter(description = "H3 인덱스", required = true)
        @PathVariable h3Index: String,
        @Parameter(description = "서지 배수", required = true)
        @RequestParam multiplier: Double,
        @Parameter(description = "관리자 ID", required = true)
        @RequestHeader("X-Admin-Id") adminId: UUID
    ): ApiResponse<SurgePriceResponseDto> {
        logger.info { "Admin $adminId updating surge price for $h3Index to $multiplier" }
        
        val surgePrice = surgePriceService.updateSurgeMultiplier(h3Index, multiplier)
        
        return ApiResponse.success(
            data = SurgePriceResponseDto(
                h3Index = surgePrice.h3Index,
                multiplier = surgePrice.surgeMultiplier.toDouble(),
                isActive = surgePrice.isActive(),
                effectiveFrom = surgePrice.effectiveFrom,
                effectiveTo = surgePrice.effectiveTo
            ),
            message = "서지 가격이 업데이트되었습니다"
        )
    }
    
    @Operation(summary = "배치 매칭 실행", description = "수동으로 배치 매칭을 실행합니다 (관리자 전용)")
    @PostMapping("/batch/execute")
    fun executeMatchingBatch(
        @Parameter(description = "관리자 ID", required = true)
        @RequestHeader("X-Admin-Id") adminId: UUID
    ): ApiResponse<List<MatchingResult>> {
        logger.info { "Admin $adminId executing manual batch matching" }
        
        val results = matchingService.processMatchingBatch()
        
        val successCount = results.count { it.success }
        val failureCount = results.size - successCount
        
        return ApiResponse.success(
            data = results,
            message = "배치 매칭 완료 - 성공: $successCount, 실패: $failureCount"
        )
    }
}