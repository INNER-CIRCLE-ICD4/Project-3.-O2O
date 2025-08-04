package com.ddakta.matching.service

import com.ddakta.matching.domain.entity.DriverCall
import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.dto.response.DriverCallResponseDto
import java.math.BigDecimal
import java.util.*

interface DriverCallService {
    // 드라이버 호출 생성 (단일)
    fun createDriverCall(
        rideId: UUID,
        driverId: UUID,
        estimatedArrival: Int?,
        estimatedFare: BigDecimal?
    ): DriverCall
    
    // 드라이버 호출 생성 (배치)
    fun createDriverCalls(
        ride: Ride,
        drivers: List<AvailableDriver>,
        pickupLocation: Location
    ): List<DriverCall>
    
    // 호출 수락
    fun acceptCall(
        driverCallId: UUID,
        driverId: UUID
    ): DriverCallResponseDto
    
    // 호출 거절
    fun rejectCall(
        driverCallId: UUID,
        driverId: UUID
    ): DriverCallResponseDto
    
    // 특정 호출 만료 처리
    fun expireDriverCall(callId: UUID): DriverCall
    
    // 만료된 호출들 일괄 처리
    fun expireOldCalls()
    
    // 운행의 대기 중인 호출 취소
    fun cancelPendingCallsForRide(rideId: UUID, excludeDriverId: UUID? = null)
    
    // 운행의 모든 호출 취소
    fun cancelAllCallsForRide(rideId: UUID)
    
    // 드라이버의 활성 호출 조회 (엔티티 반환)
    fun getActiveCallsForDriver(driverId: UUID): List<DriverCall>
    
    // 운행의 호출 목록 조회 (엔티티 반환)
    fun getCallsForRide(rideId: UUID): List<DriverCall>
    
    // 드라이버 수락률 조회
    fun getDriverAcceptanceRate(driverId: UUID): Double
}