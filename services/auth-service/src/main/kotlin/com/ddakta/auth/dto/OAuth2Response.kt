package com.ddakta.auth.dto

interface OAuth2Response {
    fun getProvider(): String
    fun getProviderId(): String
    fun getName(): String
    fun getEmail(): String
}

class OAuth2NaverResponse(
    val attributes: Map<String, Any>
): OAuth2Response {
    val attribute: Map<String,Any> = attributes["response"] as Map<String, Any>


    override fun getProvider(): String  = "naver"

    override fun getProviderId(): String  = attribute["id"] as String

    override fun getName(): String  = attribute["name"] as String

    override fun getEmail(): String  = attribute["email"] as String
}

class OAuth2GoogleResponse(
    val attributes: Map<String, Any>
): OAuth2Response {
    override fun getProvider(): String = "google"

    override fun getProviderId(): String = attributes["sub"] as String

    override fun getName(): String = attributes["name"] as String

    override fun getEmail(): String = attributes["email"] as String
}
