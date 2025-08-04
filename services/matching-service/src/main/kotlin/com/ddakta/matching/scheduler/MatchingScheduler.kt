package com.ddakta.matching.scheduler

import com.ddakta.matching.config.MatchingProperties
import com.ddakta.matching.domain.repository.MatchingRequestRepository
import com.ddakta.matching.service.MatchingService
import com.ddakta.matching.service.SurgePriceService
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class MatchingScheduler(
    private val matchingService: MatchingService,
    private val surgePriceService: SurgePriceService,
    private val matchingRequestRepository: MatchingRequestRepository,
    private val matchingProperties: MatchingProperties
) {

    private val logger = KotlinLogging.logger {}

    /**
     * 1초마다 배치 매칭 처리
     */
    @Scheduled(fixedDelayString = "\${matching.batch.interval:1000}")
    fun processMatchingBatch() {
        try {
            val startTime = System.currentTimeMillis()

            val results = matchingService.processMatchingBatch()

            if (results.isNotEmpty()) {
                val processingTime = System.currentTimeMillis() - startTime
                val successCount = results.count { it.success }

                logger.info {
                    "Processed ${results.size} matching requests in ${processingTime}ms " +
                    "(success: $successCount, failed: ${results.size - successCount})"
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing matching batch" }
        }
    }

    /**
     * 30초마다 타임아웃된 매칭 요청 처리
     */
    @Scheduled(fixedDelay = 30000)
    fun handleExpiredMatchingRequests() {
        try {
            val expiredRequests = matchingRequestRepository.findExpiredRequests(LocalDateTime.now())

            if (expiredRequests.isNotEmpty()) {
                logger.info { "Processing ${expiredRequests.size} expired matching requests" }

                expiredRequests.forEach { request ->
                    try {
                        matchingService.handleMatchingTimeout(request.rideId)
                    } catch (e: Exception) {
                        logger.error(e) { "Error handling timeout for ride ${request.rideId}" }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing expired matching requests" }
        }
    }

    /**
     * 5분마다 서지 가격 업데이트
     */
    @Scheduled(fixedDelay = 300000)
    fun updateSurgePrices() {
        try {
            logger.debug { "Updating surge prices" }

            // 활성 H3 지역 조회 (최근 30분간 요청이 있었던 지역)
            val activeH3Indexes = matchingRequestRepository.findActiveH3Indexes(
                LocalDateTime.now().minusMinutes(30)
            )

            if (activeH3Indexes.isNotEmpty()) {
                logger.info { "Updating surge prices for ${activeH3Indexes.size} active areas" }

                surgePriceService.batchUpdateSurgeMultipliers(activeH3Indexes)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error updating surge prices" }
        }
    }

    /**
     * 1시간마다 오래된 매칭 요청 정리
     */
    @Scheduled(cron = "0 0 * * * *")
    fun cleanupOldMatchingRequests() {
        try {
            val cutoffTime = LocalDateTime.now().minusDays(7)
            val deletedCount = matchingRequestRepository.deleteOldRequests(cutoffTime)

            if (deletedCount > 0) {
                logger.info { "Cleaned up $deletedCount old matching requests" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error cleaning up old matching requests" }
        }
    }
}
