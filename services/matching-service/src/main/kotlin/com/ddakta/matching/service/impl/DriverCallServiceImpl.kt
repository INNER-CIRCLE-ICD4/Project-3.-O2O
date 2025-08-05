package com.ddakta.matching.service.impl

import com.ddakta.matching.domain.entity.DriverCall
import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.DriverCallStatus
import com.ddakta.matching.domain.enum.RideEvent
import com.ddakta.matching.domain.repository.DriverCallRepository
import com.ddakta.matching.domain.repository.RideRepository
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.dto.request.RideStatusUpdateDto
import com.ddakta.matching.dto.response.DriverCallResponseDto
import com.ddakta.matching.event.producer.RideEventProducer
import com.ddakta.matching.exception.DriverCallExpiredException
import com.ddakta.matching.exception.DriverCallNotFoundException
import com.ddakta.matching.exception.InvalidDriverCallStateException
import com.ddakta.matching.service.DriverCallService
import com.ddakta.matching.service.RideService
import mu.KotlinLogging
import org.redisson.api.RedissonClient
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@Service
@Transactional
class DriverCallServiceImpl(
    private val driverCallRepository: DriverCallRepository,
    private val rideRepository: RideRepository,
    private val rideService: RideService,
    private val rideEventProducer: RideEventProducer,
    private val messagingTemplate: SimpMessagingTemplate,
    private val redissonClient: RedissonClient
) : DriverCallService {

    private val logger = KotlinLogging.logger {}

    companion object {
        const val DRIVER_RESPONSE_LOCK_KEY = "driver:response:"
        const val DRIVER_ACCEPTANCE_RATE_KEY = "driver:acceptance:"
        const val ACCEPTANCE_RATE_WINDOW_DAYS = 30L
    }

    override fun createDriverCall(
        rideId: UUID,
        driverId: UUID,
        estimatedArrival: Int?,
        estimatedFare: BigDecimal?
    ): DriverCall {
        val ride = rideRepository.findById(rideId)
            .orElseThrow { IllegalArgumentException("Ride not found: $rideId") }
        val driverCall = DriverCall(
            ride = ride,
            driverId = driverId,
            sequenceNumber = 1, // 기본값, 실제로는 계산 필요
            expiresAt = LocalDateTime.now().plusSeconds(30), // 30초 타임아웃
            estimatedArrivalSeconds = estimatedArrival,
            estimatedFare = estimatedFare
        )

        val saved = driverCallRepository.save(driverCall)

        // WebSocket으로 드라이버에게 알림
        notifyDriver(saved)

        logger.info { "Created driver call ${saved.id} for ride $rideId and driver $driverId" }

        return saved
    }
    
    override fun createDriverCalls(
        ride: Ride,
        drivers: List<AvailableDriver>,
        pickupLocation: Location
    ): List<DriverCall> {
        val driverCalls = mutableListOf<DriverCall>()
        
        drivers.forEachIndexed { index, driver ->
            val driverCall = DriverCall(
                ride = ride,
                driverId = driver.driverId,
                sequenceNumber = index + 1,
                expiresAt = LocalDateTime.now().plusSeconds(30),
                estimatedArrivalSeconds = driver.estimatedArrivalMinutes?.let { it * 60 },
                estimatedFare = driver.estimatedFare,
                distanceToPickupMeters = driver.distanceToPickupMeters?.toInt()
            )
            
            val saved = driverCallRepository.save(driverCall)
            driverCalls.add(saved)
            
            // WebSocket으로 드라이버에게 알림
            notifyDriver(saved)
        }
        
        logger.info { "Created ${driverCalls.size} driver calls for ride ${ride.id}" }
        
        return driverCalls
    }
    
    override fun expireOldCalls() {
        val expiredCount = driverCallRepository.expireOldCalls()
        logger.info { "Expired $expiredCount old driver calls" }
    }

    override fun acceptCall(callId: UUID, driverId: UUID): DriverCallResponseDto {
        val lock = redissonClient.getFairLock("$DRIVER_RESPONSE_LOCK_KEY$callId")

        if (!lock.tryLock(100, 5000, TimeUnit.MILLISECONDS)) {
            throw InvalidDriverCallStateException("Failed to acquire lock for driver call response")
        }

        try {
            val driverCall = driverCallRepository.findByIdWithLock(callId)
                ?: throw DriverCallNotFoundException(callId)

            // 드라이버 확인
            if (driverCall.driverId != driverId) {
                throw InvalidDriverCallStateException("Driver mismatch for call $callId")
            }

            // 만료 확인
            if (driverCall.isExpired()) {
                throw DriverCallExpiredException("Driver call $callId has expired")
            }

            // 상태 확인
            if (driverCall.status != DriverCallStatus.PENDING) {
                throw InvalidDriverCallStateException(
                    "Cannot accept call in status: ${driverCall.status}"
                )
            }

            // 수락 처리
            driverCall.accept()
            val savedCall = driverCallRepository.save(driverCall)

            // 운행에 드라이버 할당
            val ride = rideService.assignDriver(driverCall.ride.id!!, driverId)

            // 같은 운행의 다른 드라이버 호출 취소
            cancelOtherCallsForRide(driverCall.ride.id!!, callId)

            // 운행 상태 업데이트
            rideService.updateRideStatus(
                driverCall.ride.id!!,
                RideStatusUpdateDto(
                    event = RideEvent.ASSIGN_DRIVER,
                    metadata = mapOf(
                        "driverId" to driverId.toString(),
                        "callId" to callId.toString()
                    )
                ),
                driverId
            )

            // 수락률 업데이트
            updateDriverAcceptanceRate(driverId, true)

            // WebSocket 알림
            notifyAcceptance(savedCall)

            logger.info { "Driver $driverId accepted call $callId for ride ${driverCall.ride.id}" }

            return DriverCallResponseDto.from(savedCall)

        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    override fun rejectCall(callId: UUID, driverId: UUID): DriverCallResponseDto {
        val driverCall = driverCallRepository.findByIdWithLock(callId)
            ?: throw DriverCallNotFoundException(callId)

        // 드라이버 확인
        if (driverCall.driverId != driverId) {
            throw InvalidDriverCallStateException("Driver mismatch for call $callId")
        }

        // 상태 확인
        if (driverCall.status != DriverCallStatus.PENDING) {
            throw InvalidDriverCallStateException(
                "Cannot reject call in status: ${driverCall.status}"
            )
        }

        // 거절 처리
        driverCall.reject()
        val savedCall = driverCallRepository.save(driverCall)

        // 수락률 업데이트
        updateDriverAcceptanceRate(driverId, false)

        // WebSocket 알림
        notifyRejection(savedCall)

        logger.info { "Driver $driverId rejected call $callId" }

        return DriverCallResponseDto.from(savedCall)
    }

    override fun expireDriverCall(callId: UUID): DriverCall {
        val driverCall = driverCallRepository.findByIdWithLock(callId)
            ?: throw DriverCallNotFoundException(callId)

        if (driverCall.status != DriverCallStatus.PENDING) {
            return driverCall
        }

        // 만료 처리
        driverCall.expire()
        val savedCall = driverCallRepository.save(driverCall)

        // 수락률 업데이트 (무응답은 거절로 처리)
        updateDriverAcceptanceRate(driverCall.driverId, false)

        // WebSocket 알림
        notifyExpiration(savedCall)

        logger.info { "Driver call $callId expired" }

        return savedCall
    }

    override fun getActiveCallsForDriver(driverId: UUID): List<DriverCall> {
        return driverCallRepository.findActiveCallsByDriverId(
            driverId,
            LocalDateTime.now()
        )
    }

    override fun getCallsForRide(rideId: UUID): List<DriverCall> {
        return driverCallRepository.findByRideId(rideId)
    }

    override fun cancelAllCallsForRide(rideId: UUID) {
        val calls = driverCallRepository.findPendingCallsByRideId(rideId)

        calls.forEach { call ->
            if (call.status == DriverCallStatus.PENDING) {
                call.cancel()
                driverCallRepository.save(call)

                // WebSocket 알림
                notifyCancellation(call)
            }
        }

        logger.info { "Cancelled ${calls.size} pending calls for ride $rideId" }
    }
    
    override fun cancelPendingCallsForRide(rideId: UUID, excludeDriverId: UUID?) {
        val calls = driverCallRepository.findPendingCallsByRideId(rideId)
            .filter { excludeDriverId == null || it.driverId != excludeDriverId }

        calls.forEach { call ->
            if (call.status == DriverCallStatus.PENDING) {
                call.cancel()
                driverCallRepository.save(call)

                // WebSocket 알림
                notifyCancellation(call)
            }
        }

        logger.info { "Cancelled ${calls.size} pending calls for ride $rideId (excluding driver $excludeDriverId)" }
    }

    override fun getDriverAcceptanceRate(driverId: UUID): Double {
        val startDate = LocalDateTime.now().minusDays(ACCEPTANCE_RATE_WINDOW_DAYS)
        val stats = driverCallRepository.getDriverCallStats(driverId, startDate)

        return if (stats.totalCalls > 0) {
            stats.acceptedCalls.toDouble() / stats.totalCalls.toDouble()
        } else {
            1.0 // 신규 드라이버는 100%로 시작
        }
    }

    private fun cancelOtherCallsForRide(rideId: UUID, acceptedCallId: UUID) {
        val otherCalls = driverCallRepository.findPendingCallsByRideId(rideId)
            .filter { it.id != acceptedCallId }

        otherCalls.forEach { call ->
            call.cancel()
            driverCallRepository.save(call)

            // WebSocket으로 취소 알림
            notifyCancellation(call)
        }

        logger.info { "Cancelled ${otherCalls.size} other calls for ride $rideId" }
    }

    private fun updateDriverAcceptanceRate(driverId: UUID, accepted: Boolean) {
        try {
            val key = "$DRIVER_ACCEPTANCE_RATE_KEY$driverId"
            val value = if (accepted) "1" else "0"

            // Redis에 최근 응답 기록 (리스트 구조로 저장)
            redissonClient.getList<String>(key).apply {
                add(value)
                // 최대 1000개까지만 보관
                if (size > 1000) {
                    removeAt(0)
                }
            }
        } catch (e: Exception) {
            logger.warn { "Failed to update acceptance rate for driver $driverId: ${e.message}" }
        }
    }

    private fun notifyDriver(driverCall: DriverCall) {
        val notification = mapOf(
            "type" to "DRIVER_CALL",
            "callId" to driverCall.id,
            "rideId" to driverCall.ride.id!!,
            "estimatedArrival" to driverCall.estimatedArrivalSeconds,
            "estimatedFare" to driverCall.estimatedFare,
            "expiresAt" to driverCall.expiresAt
        )

        messagingTemplate.convertAndSendToUser(
            driverCall.driverId.toString(),
            "/queue/driver-calls",
            notification
        )
    }

    private fun notifyAcceptance(driverCall: DriverCall) {
        val notification = mapOf(
            "type" to "CALL_ACCEPTED",
            "callId" to driverCall.id,
            "rideId" to driverCall.ride.id!!,
            "driverId" to driverCall.driverId
        )

        // 승객에게 알림
        messagingTemplate.convertAndSend(
            "/topic/ride/${driverCall.ride.id}",
            notification
        )
    }

    private fun notifyRejection(driverCall: DriverCall) {
        val notification = mapOf(
            "type" to "CALL_REJECTED",
            "callId" to driverCall.id,
            "driverId" to driverCall.driverId
        )

        messagingTemplate.convertAndSend(
            "/topic/ride/${driverCall.ride.id}",
            notification
        )
    }

    private fun notifyExpiration(driverCall: DriverCall) {
        val notification = mapOf(
            "type" to "CALL_EXPIRED",
            "callId" to driverCall.id,
            "driverId" to driverCall.driverId
        )

        messagingTemplate.convertAndSendToUser(
            driverCall.driverId.toString(),
            "/queue/driver-calls",
            notification
        )
    }

    private fun notifyCancellation(driverCall: DriverCall) {
        val notification = mapOf(
            "type" to "CALL_CANCELLED",
            "callId" to driverCall.id,
            "rideId" to driverCall.ride.id!!
        )

        messagingTemplate.convertAndSendToUser(
            driverCall.driverId.toString(),
            "/queue/driver-calls",
            notification
        )
    }
}
