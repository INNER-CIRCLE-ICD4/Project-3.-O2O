package com.ddakta.auth.dto

data class OAuthEndpointsResponse(
    val message: String = "Please use OAuth2 login endpoints",
    val google: String = "/oauth2/authorization/google",
    val apple: String = "/oauth2/authorization/apple"
)