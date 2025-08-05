package com.ddakta.matching.controller

import com.ddakta.matching.config.DlqMessage
import com.ddakta.matching.event.dlq.DlqConsumer
import com.ddakta.utils.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Tag(name = "DLQ Management", description = "Dead Letter Queue 관리 API")
@RestController
@RequestMapping("/api/v1/admin/dlq")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
class DlqManagementController(
    private val dlqConsumer: DlqConsumer
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Operation(
        summary = "DLQ 메시지 조회",
        description = "Dead Letter Queue에 있는 메시지를 조회합니다"
    )
    @GetMapping("/messages")
    fun getDlqMessages(
        @Parameter(description = "원본 토픽 이름") 
        @RequestParam(required = false) originalTopic: String?,
        @Parameter(description = "에러 클래스 필터")
        @RequestParam(required = false) errorClass: String?,
        @Parameter(description = "조회 개수", example = "20")
        @RequestParam(defaultValue = "20") limit: Int,
        @Parameter(description = "페이지 번호", example = "0")
        @RequestParam(defaultValue = "0") page: Int
    ): ApiResponse<DlqMessagesResponse> {
        logger.info { 
            "Fetching DLQ messages - topic: $originalTopic, " +
            "errorClass: $errorClass, limit: $limit, page: $page" 
        }
        
        // In a real implementation, this would query from persistent storage
        val messages = listOf(
            DlqMessageDto(
                id = "dlq-001",
                originalTopic = "ride-requested",
                originalKey = "ride-123",
                errorMessage = "Failed to process ride request",
                errorClass = "com.ddakta.matching.exception.ProcessingException",
                failedAt = LocalDateTime.now().minusHours(1),
                retryCount = 3,
                canReplay = true
            )
        )
        
        return ApiResponse.success(
            data = DlqMessagesResponse(
                messages = messages,
                totalCount = messages.size,
                page = page,
                pageSize = limit,
                hasNext = false
            ),
            message = "DLQ 메시지 조회 성공"
        )
    }
    
    @Operation(
        summary = "DLQ 메시지 재처리",
        description = "특정 DLQ 메시지를 원본 토픽으로 재전송합니다"
    )
    @PostMapping("/messages/{messageId}/replay")
    fun replayDlqMessage(
        @Parameter(description = "DLQ 메시지 ID", required = true)
        @PathVariable messageId: String
    ): ApiResponse<DlqReplayResponse> {
        logger.info { "Replaying DLQ message: $messageId" }
        
        // In a real implementation, fetch the message from storage
        val dlqMessage = DlqMessage(
            originalTopic = "ride-requested",
            originalPartition = 0,
            originalOffset = 1234L,
            originalKey = "ride-123",
            originalValue = mapOf("rideId" to "ride-123"),
            errorMessage = "Processing failed",
            errorClass = "ProcessingException",
            failedAt = System.currentTimeMillis(),
            retryCount = 3
        )
        
        val success = dlqConsumer.replayDlqMessage(dlqMessage)
        
        return if (success) {
            ApiResponse.success(
                data = DlqReplayResponse(
                    messageId = messageId,
                    replayedAt = LocalDateTime.now(),
                    success = true,
                    originalTopic = dlqMessage.originalTopic
                ),
                message = "메시지가 성공적으로 재처리되었습니다"
            )
        } else {
            ApiResponse.error(
                message = "메시지 재처리에 실패했습니다"
            )
        }
    }
    
    @Operation(
        summary = "DLQ 메시지 일괄 재처리",
        description = "특정 토픽의 DLQ 메시지를 일괄로 재처리합니다"
    )
    @PostMapping("/topics/{topicName}/replay-all")
    fun replayAllMessages(
        @Parameter(description = "원본 토픽 이름", required = true)
        @PathVariable topicName: String,
        @Parameter(description = "재처리할 최대 메시지 수", example = "100")
        @RequestParam(defaultValue = "100") limit: Int
    ): ApiResponse<DlqBatchReplayResponse> {
        logger.info { "Replaying all messages for topic: $topicName, limit: $limit" }
        
        val replayedCount = dlqConsumer.replayAllMessages(topicName, limit)
        
        return ApiResponse.success(
            data = DlqBatchReplayResponse(
                topicName = topicName,
                requestedCount = limit,
                replayedCount = replayedCount,
                startedAt = LocalDateTime.now(),
                completedAt = LocalDateTime.now()
            ),
            message = "$replayedCount 개의 메시지가 재처리되었습니다"
        )
    }
    
    @Operation(
        summary = "DLQ 통계 조회",
        description = "Dead Letter Queue 통계를 조회합니다"
    )
    @GetMapping("/stats")
    fun getDlqStats(): ApiResponse<DlqStatsResponse> {
        logger.info { "Fetching DLQ statistics" }
        
        // In a real implementation, aggregate from persistent storage
        val stats = DlqStatsResponse(
            totalMessages = 42,
            messagesByTopic = mapOf(
                "ride-requested" to 15,
                "ride-matched" to 8,
                "driver-location-updated" to 19
            ),
            messagesByError = mapOf(
                "ProcessingException" to 20,
                "TimeoutException" to 12,
                "JsonParseException" to 10
            ),
            oldestMessage = LocalDateTime.now().minusDays(7),
            poisonMessageCount = 5
        )
        
        return ApiResponse.success(
            data = stats,
            message = "DLQ 통계 조회 성공"
        )
    }
    
    @Operation(
        summary = "Poison 메시지 삭제",
        description = "처리 불가능한 poison 메시지를 삭제합니다"
    )
    @DeleteMapping("/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePoisonMessage(
        @Parameter(description = "DLQ 메시지 ID", required = true)
        @PathVariable messageId: String
    ) {
        logger.info { "Deleting poison message: $messageId" }
        
        // In a real implementation, delete from persistent storage
    }
}

data class DlqMessageDto(
    val id: String,
    val originalTopic: String,
    val originalKey: String?,
    val errorMessage: String,
    val errorClass: String,
    val failedAt: LocalDateTime,
    val retryCount: Int,
    val canReplay: Boolean,
    val metadata: Map<String, String> = emptyMap()
)

data class DlqMessagesResponse(
    val messages: List<DlqMessageDto>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int,
    val hasNext: Boolean
)

data class DlqReplayResponse(
    val messageId: String,
    val replayedAt: LocalDateTime,
    val success: Boolean,
    val originalTopic: String
)

data class DlqBatchReplayResponse(
    val topicName: String,
    val requestedCount: Int,
    val replayedCount: Int,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime
)

data class DlqStatsResponse(
    val totalMessages: Int,
    val messagesByTopic: Map<String, Int>,
    val messagesByError: Map<String, Int>,
    val oldestMessage: LocalDateTime?,
    val poisonMessageCount: Int
)