package com.ddakta.matching.domain.enum

enum class RideEvent(val targetStatus: RideStatus) {
    MATCH_FOUND(RideStatus.MATCHED),
    ASSIGN_DRIVER(RideStatus.DRIVER_ASSIGNED),  // 드라이버 할당 이벤트
    DRIVER_ACCEPTED(RideStatus.DRIVER_ASSIGNED),
    DRIVER_EN_ROUTE(RideStatus.EN_ROUTE_TO_PICKUP),
    DRIVER_ARRIVED(RideStatus.ARRIVED_AT_PICKUP),
    START_TRIP(RideStatus.ON_TRIP),  // 여행 시작
    TRIP_STARTED(RideStatus.ON_TRIP),
    TRIP_COMPLETED(RideStatus.COMPLETED),
    RIDE_CANCELLED(RideStatus.CANCELLED),
    MATCHING_TIMEOUT(RideStatus.FAILED),  // 매칭 타임아웃
    DRIVER_TIMEOUT(RideStatus.FAILED);  // 드라이버 응답 타임아웃

    companion object {
        fun fromStatus(status: RideStatus): RideEvent {
            return when (status) {
                RideStatus.MATCHED -> MATCH_FOUND
                RideStatus.DRIVER_ASSIGNED -> DRIVER_ACCEPTED
                RideStatus.EN_ROUTE_TO_PICKUP -> DRIVER_EN_ROUTE
                RideStatus.ARRIVED_AT_PICKUP -> DRIVER_ARRIVED
                RideStatus.ON_TRIP -> TRIP_STARTED
                RideStatus.COMPLETED -> TRIP_COMPLETED
                RideStatus.CANCELLED -> RIDE_CANCELLED
                else -> throw IllegalArgumentException("No event for status: $status")
            }
        }
    }
}