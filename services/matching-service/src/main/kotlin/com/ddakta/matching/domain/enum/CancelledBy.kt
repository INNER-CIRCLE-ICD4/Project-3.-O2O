package com.ddakta.matching.domain.enum

enum class CancelledBy {
    PASSENGER,  // 승객에 의한 취소
    DRIVER,     // 드라이버에 의한 취소
    SYSTEM,     // 시스템에 의한 취소 (타임아웃 등)
    ADMIN       // 관리자에 의한 취소
}