package com.ddakta.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "kakao.oauth")
data class KakaoProperties @ConstructorBinding constructor(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val authorizeUri: String,
    val tokenUri: String,
    val userInfoUri: String
)
