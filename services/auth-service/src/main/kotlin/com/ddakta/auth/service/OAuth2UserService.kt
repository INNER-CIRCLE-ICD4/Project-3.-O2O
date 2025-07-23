package com.ddakta.auth.service

import com.ddakta.auth.oauth.OAuth2Response
import com.ddakta.auth.oauth.OAuth2ResponseFactory
import com.ddakta.auth.oauth.OAuth2UserDetails
import com.ddakta.auth.dto.UserDto
import com.ddakta.auth.entity.User
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

        println(oAuth2User)

        val registrationId = userRequest.clientRegistration.registrationId
        val oAuth2Response: OAuth2Response = OAuth2ResponseFactory.of(registrationId, oAuth2User.attributes)

        // TODO: 네이버/카카오/구글 등 명의가 같은 사용자 어떻게 판단? -> 판단하여 통합계정으로 관리 하도록 변경
        val username = oAuth2Response.getProvider() + "_" + oAuth2Response.getProviderId()
        val userDto: UserDto = UserDto(
            username = username,
            name = oAuth2Response.getName(),
            email = oAuth2Response.getEmail(),
            role = "ROLE_USER"
        )
        checkRegisterUser(userDto)

        return OAuth2UserDetails(userDto)
    }

        @Transactional
    fun checkRegisterUser(request: UserDto): User {
        return userRepository.findByUsername(request.username)?: run{
            val newUser: User = User.register(request)
            userRepository.save(newUser)
        }
    }

}



