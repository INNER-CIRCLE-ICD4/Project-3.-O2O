package com.ddakta.domain.user

object UserFixture {
    // PASSENGER 기본 회원
    val PASSENGER = User.create(
        username = "social_123",
        name     = "홍길동",
        email    = "hong@example.com",
        type     = UserType.PASSENGER
    )

    // DRIVER 기본 회원
    val DRIVER = User.create(
        username = "social_999",
        name     = "김기사",
        email    = "kim@example.com",
        type     = UserType.DRIVER
    )

    // ADMIN 기본 회원
    val ADMIN = User.create(
        username = "admin_001",
        name     = "운영자",
        email    = "admin@example.com",
        type     = UserType.ADMIN
    )
}
