package com.ddakta.auth.oauth

sealed class OAuth2Response {
    abstract fun getProvider(): String
    abstract fun getProviderId(): String
    abstract fun getName(): String
    abstract fun getEmail(): String

    class OAuth2NaverResponse(val attributes: Map<String, Any>): OAuth2Response() {
        private val attribute: Map<String, String> = (attributes.toMap()["response"] as? Map<*, *>)
            ?.mapNotNull { (key, value) ->
                if (key is String && value is String) key to value
                else null
            }?.toMap() ?: throw RuntimeException("attribute not found")

        override fun getProvider(): String  = "naver"
        override fun getProviderId(): String  = attribute["id"] as String
        override fun getName(): String  = attribute["name"] as String
        override fun getEmail(): String  = attribute["email"] as String
    }

    class OAuth2GoogleResponse(val attributes: Map<String, Any>): OAuth2Response() {
        override fun getProvider(): String = "google"
        override fun getProviderId(): String = attributes["sub"] as String
        override fun getName(): String = attributes["name"] as String
        override fun getEmail(): String = attributes["email"] as String
    }
}

object OAuth2ResponseFactory {
    fun of(registrationId: String, attributes: Map<String, Any>): OAuth2Response {
        return when (registrationId.lowercase()) {
            "naver" -> OAuth2Response.OAuth2NaverResponse(attributes)
            "google" -> OAuth2Response.OAuth2GoogleResponse(attributes)
            else -> throw IllegalArgumentException("Unsupported provider: $registrationId")
        }
    }
}
