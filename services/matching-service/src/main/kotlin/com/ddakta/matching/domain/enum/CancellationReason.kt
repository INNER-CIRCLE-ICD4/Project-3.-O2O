package com.ddakta.matching.domain.enum

enum class CancellationReason {
    PASSENGER_CANCELLED,     // 승객이 취소함
    DRIVER_CANCELLED,        // 드라이버가 취소함
    NO_DRIVERS_AVAILABLE,    // 이용 가능한 드라이버가 없음
    DRIVER_NOT_RESPONDING,   // 드라이버가 응답하지 않음
    PASSENGER_NOT_RESPONDING, // 승객이 응답하지 않음
    SAFETY_CONCERN,          // 안전상의 우려
    TECHNICAL_ERROR,         // 기술적 오류
    PAYMENT_ISSUE,           // 결제 문제
    OTHER                    // 기타
}