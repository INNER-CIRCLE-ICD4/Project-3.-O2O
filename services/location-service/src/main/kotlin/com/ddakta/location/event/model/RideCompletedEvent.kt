package com.ddakta.location.event.model

import java.util.UUID

/**
 * 결제가 완료되었을 때 발생하는 이벤트
 * matching-service에서 발행하고, location-service에서 구독하여 운행 종료 후의 후속 처리를 담당한다.
 */
data class RideCompletedEvent(
    val rideId: UUID,
    val driverId: UUID,
    val completedAt: Long
)
