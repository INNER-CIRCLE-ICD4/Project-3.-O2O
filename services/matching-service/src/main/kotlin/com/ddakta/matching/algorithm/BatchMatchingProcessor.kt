package com.ddakta.matching.algorithm

import com.ddakta.matching.client.LocationServiceClient
import com.ddakta.matching.config.MatchingProperties
import com.ddakta.matching.domain.entity.MatchingRequest
import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.domain.repository.MatchingRequestRepository
import com.ddakta.matching.domain.repository.RideRepository
import com.ddakta.matching.service.DriverCallService
import mu.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.*

@Component
class BatchMatchingProcessor(
    private val matchingRequestRepository: MatchingRequestRepository,
    private val rideRepository: RideRepository,
    private val locationServiceClient: LocationServiceClient,
    private val hungarianAlgorithm: HungarianAlgorithm,
    private val matchingScoreCalculator: MatchingScoreCalculator,
    private val driverCallService: DriverCallService,
    private val redisTemplate: RedisTemplate<String, String>,
    private val matchingProperties: MatchingProperties
) {

    private val logger = KotlinLogging.logger {}
    private val processingLock = "matching:batch:lock"

    @Scheduled(fixedDelayString = "\${matching.batch.interval}")
    @Transactional
    fun processBatch() {
        // Try to acquire distributed lock
        val lockAcquired = acquireLock()
        if (!lockAcquired) {
            logger.debug { "Failed to acquire batch processing lock" }
            return
        }

        try {
            val batchId = UUID.randomUUID()
            val startTime = System.currentTimeMillis()

            val requests = fetchPendingRequests()
            if (requests.isEmpty()) {
                return
            }

            logger.info { "Processing batch $batchId with ${requests.size} requests" }

            // Mark requests as processing
            markRequestsAsProcessing(requests, batchId)

            // Fetch ride details and nearby drivers for each request
            val requestsWithData = prepareMatchingData(requests)
            if (requestsWithData.isEmpty()) {
                logger.warn { "No valid requests with data to process" }
                return
            }

            // Get all unique drivers
            val allDrivers = requestsWithData
                .flatMap { it.nearbyDrivers }
                .distinctBy { it.driverId }

            if (allDrivers.isEmpty()) {
                logger.warn { "No available drivers found for batch $batchId" }
                failAllRequests(requestsWithData.map { it.request }, "No available drivers")
                return
            }

            // Build cost matrix
            val costMatrix = matchingScoreCalculator.calculateCostMatrix(
                requestsWithData.map { it.request to it.pickupLocation },
                allDrivers
            )

            // Run Hungarian Algorithm
            val assignments = hungarianAlgorithm.findOptimalMatching(costMatrix)

            // Process matching results
            processMatchingResults(requestsWithData, allDrivers, assignments)

            val processingTime = System.currentTimeMillis() - startTime
            logger.info { "Batch $batchId processed in ${processingTime}ms" }

        } catch (e: Exception) {
            logger.error(e) { "Error processing matching batch" }
        } finally {
            releaseLock()
        }
    }

    private fun fetchPendingRequests(): List<MatchingRequest> {
        return matchingRequestRepository.findPendingRequests(
            limit = matchingProperties.batch.size,
            maxAge = Duration.ofSeconds(10)
        )
    }

    private fun markRequestsAsProcessing(requests: List<MatchingRequest>, batchId: UUID) {
        requests.forEach { request ->
            request.startProcessing(batchId)
        }
        matchingRequestRepository.saveAll(requests)
    }

    private fun prepareMatchingData(requests: List<MatchingRequest>): List<RequestWithData> {
        return requests.mapNotNull { request ->
            try {
                val ride = rideRepository.findById(request.rideId).orElse(null)
                if (ride == null) {
                    logger.warn { "Ride ${request.rideId} not found for matching request" }
                    request.fail("Ride not found")
                    matchingRequestRepository.save(request)
                    return@mapNotNull null
                }

                val nearbyDrivers = locationServiceClient.findNearbyDrivers(
                    h3Index = request.pickupH3,
                    radiusKm = matchingProperties.search.radiusKm,
                    limit = matchingProperties.search.maxDrivers
                )

                RequestWithData(
                    request = request,
                    ride = ride,
                    pickupLocation = ride.pickupLocation,
                    nearbyDrivers = nearbyDrivers
                )
            } catch (e: Exception) {
                logger.error(e) { "Error preparing data for request ${request.id}" }
                request.fail("Error preparing matching data: ${e.message}")
                matchingRequestRepository.save(request)
                null
            }
        }
    }

    private fun processMatchingResults(
        requestsWithData: List<RequestWithData>,
        allDrivers: List<AvailableDriver>,
        assignments: IntArray
    ) {
        val driverIndexMap = allDrivers.withIndex().associate { it.value.driverId to it.index }
        val assignedDrivers = mutableSetOf<UUID>()

        requestsWithData.forEachIndexed { requestIndex, requestData ->
            val assignedDriverIndex = assignments[requestIndex]

            if (assignedDriverIndex != -1 && assignedDriverIndex < allDrivers.size) {
                val assignedDriver = allDrivers[assignedDriverIndex]

                // Check if driver is already assigned to another request
                if (assignedDriver.driverId in assignedDrivers) {
                    handleNoDriverAvailable(requestData)
                    return@forEachIndexed
                }

                // Find top 3 drivers for this request
                val eligibleDrivers = requestData.nearbyDrivers
                    .filter { it.driverId !in assignedDrivers }
                    .filter { matchingScoreCalculator.isDriverEligible(it) }
                    .map { driver ->
                        val score = matchingScoreCalculator.calculateScore(
                            requestData.request,
                            driver,
                            requestData.pickupLocation
                        )
                        driver to score
                    }

                val selectedDrivers = matchingScoreCalculator.selectTopDrivers(
                    eligibleDrivers,
                    matchingProperties.driverCall.maxDrivers
                )

                if (selectedDrivers.isNotEmpty()) {
                    // Mark drivers as assigned
                    selectedDrivers.forEach { assignedDrivers.add(it.driverId) }

                    // Create driver calls
                    driverCallService.createDriverCalls(
                        ride = requestData.ride,
                        drivers = selectedDrivers,
                        pickupLocation = requestData.pickupLocation
                    )

                    requestData.request.complete()
                } else {
                    handleNoDriverAvailable(requestData)
                }
            } else {
                handleNoDriverAvailable(requestData)
            }
        }

        // Save all updated requests
        matchingRequestRepository.saveAll(requestsWithData.map { it.request })
    }

    private fun handleNoDriverAvailable(requestData: RequestWithData) {
        logger.warn { "No drivers available for ride ${requestData.ride.id}" }
        requestData.request.fail("No available drivers")
        // Additional logic for notifying passenger could go here
    }

    private fun failAllRequests(requests: List<MatchingRequest>, reason: String) {
        requests.forEach { it.fail(reason) }
        matchingRequestRepository.saveAll(requests)
    }

    private fun acquireLock(): Boolean {
        return redisTemplate.opsForValue().setIfAbsent(
            processingLock,
            Thread.currentThread().name,
            Duration.ofSeconds(5)
        ) ?: false
    }

    private fun releaseLock() {
        redisTemplate.delete(processingLock)
    }

    data class RequestWithData(
        val request: MatchingRequest,
        val ride: Ride,
        val pickupLocation: Location,
        val nearbyDrivers: List<AvailableDriver>
    )
}
