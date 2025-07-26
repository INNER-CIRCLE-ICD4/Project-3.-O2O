package com.ddakta.domain.user

enum class UserStatus {
    ACTIVE,      // 활성 상태
    INACTIVE,    // 비활성 상태 (일시적 비활성화)
    SUSPENDED,   // 정지 상태 (관리자에 의한 정지)
    DELETED      // 삭제된 상태 (소프트 딜리트)
}