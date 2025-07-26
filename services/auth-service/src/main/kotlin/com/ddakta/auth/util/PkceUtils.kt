package com.ddakta.auth.utils

import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random

object PkceUtils {

    /** RFC7636 에 따라 43~128자 길이의 무작위 문자열 생성 */
    fun generateCodeVerifier(length: Int = 64): String {
        require(length in 43..128) { "length must be between 43 and 128" }
        val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        return (1..length)
            .map { allowed.random() }
            .joinToString("")
    }

    /** SHA-256 해시 후 URL-safe Base64 인코딩 (패딩 제거) */
    fun generateCodeChallenge(verifier: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }
}
