package com.ddakta.auth.service

import com.ddakta.auth.dto.OAuth2GoogleResponse
import com.ddakta.auth.dto.OAuth2NaverResponse
import com.ddakta.auth.dto.OAuth2Response
import com.ddakta.auth.dto.UserDTO
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class OAuth2UserService: DefaultOAuth2UserService() {
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val user :OAuth2User = super.loadUser(userRequest)

        val registrationId = userRequest.clientRegistration.registrationId
        lateinit var oAuth2Response: OAuth2Response
        when (registrationId) {
            "naver" -> oAuth2Response = OAuth2NaverResponse(user.attributes)
            "google" -> oAuth2Response = OAuth2GoogleResponse(user.attributes)
        }

        val username = oAuth2Response.getProvider() + "_" + oAuth2Response.getProviderId()

        val userDTO: UserDTO = UserDTO(
            username = username,
            name = oAuth2Response.getName(),
            role = "ROLE_USER"
        )

        return CustomOAuth2User(userDTO)
    }
}

class CustomOAuth2User(private val userDTO: UserDTO): OAuth2User {
    override fun getName(): String = userDTO.name
    override fun getAttributes(): Map<String?, Any?>? {
        return null;
    }

    override fun getAuthorities(): Collection<GrantedAuthority?>? {
        return listOf(SimpleGrantedAuthority(userDTO.role))
    }
    fun getUserName() = userDTO.username
}


