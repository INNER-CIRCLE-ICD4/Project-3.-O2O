package com.ddakta.auth.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class KakaoUserResponse(
    val id: Long,
    val properties: Properties,
    @JsonProperty("kakao_account") val kakaoAccount: KakaoAccount
) {
    data class Properties(val nickname: String)
    data class KakaoAccount(val email: String?)
}
