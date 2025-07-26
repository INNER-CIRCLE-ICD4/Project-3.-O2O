package com.ddakta.auth.service

import com.ddakta.auth.oauth.OAuth2Response
import com.ddakta.auth.oauth.OAuth2ResponseFactory
import com.ddakta.auth.oauth.OAuth2UserDetails
import com.ddakta.auth.dto.LoginUserRequest
import com.ddakta.auth.entity.User
import com.ddakta.auth.entity.UserDto
import com.ddakta.auth.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class OAuth2UserService(
    private val userRepository: UserRepository,
): DefaultOAuth2UserService() {

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)

        val registrationId = userRequest.clientRegistration.registrationId
        val oAuth2Response: OAuth2Response = OAuth2ResponseFactory.of(registrationId, oAuth2User.attributes)

        val userId = oAuth2Response.getProviderId()
        val loginRequest = LoginUserRequest(
            userId = userId,
            name = oAuth2Response.getName(),
            email = oAuth2Response.getEmail(),
            oAuthType = registrationId
        )

        val user = checkRegisterUser(loginRequest)

        return OAuth2UserDetails(user)
    }

        @Transactional
    fun checkRegisterUser(request: LoginUserRequest): UserDto {

        val user: User = when(request.oAuthType.lowercase()) {
            "naver" -> {
                userRepository.findByNaverId(request.userId)
            }
            "google" -> {
                userRepository.findByGoogleId(request.userId)
            }
            else -> {null}
        }?: User.register(request)

        userRepository.save(user)
        return UserDto.fromUser(user)
    }

}



