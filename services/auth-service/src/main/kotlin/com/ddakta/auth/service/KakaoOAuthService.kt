package com.ddakta.auth.service

import com.ddakta.auth.config.JwtProperties
import com.ddakta.auth.config.KakaoProperties
import com.ddakta.auth.domain.enums.AuthProvider
import com.ddakta.auth.domain.model.OAuthRequest
import com.ddakta.auth.domain.model.TokenSession
import com.ddakta.auth.dto.AuthResponse
import com.ddakta.auth.dto.KakaoTokenResponse
import com.ddakta.auth.dto.KakaoUserResponse
import com.ddakta.auth.entity.User
import com.ddakta.auth.repository.OAuthRequestRepository
import com.ddakta.auth.repository.TokenSessionRepository
import com.ddakta.auth.repository.UserRepository
import com.ddakta.auth.utils.PkceUtils
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant
import java.util.UUID

@Service
class KakaoOAuthService(
    private val props: KakaoProperties,
    private val oauthRequestRepository: OAuthRequestRepository,
    private val jwtProps: JwtProperties,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val tokenSessionRepository: TokenSessionRepository
) {
    private val webClient = WebClient.builder().build()

    /** 클라이언트에 전달할 인증 URL 생성 */
    fun getAuthorizationUrl(): String {
        val state = UUID.randomUUID().toString()
        val codeVerifier = PkceUtils.generateCodeVerifier()
        val codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier)

        oauthRequestRepository.save(OAuthRequest(state, codeVerifier))

        return UriComponentsBuilder
            .fromHttpUrl(props.authorizeUri)
            .queryParam("response_type", "code")
            .queryParam("client_id", props.clientId)
            .queryParam("redirect_uri", props.redirectUri)
            .queryParam("state", state)
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .build()
            .toUriString()
    }

    /** 콜백에서 받은 code, state 로 로그인 처리 */
    fun login(code: String, state: String): AuthResponse {
        // 1) state 검증 및 codeVerifier 조회
        val oAuthReq = oauthRequestRepository.findById(state)
            .orElseThrow { IllegalArgumentException("Invalid OAuth state") }

        // 2) 토큰 교환
        val tokenRes = webClient.post()
            .uri(props.tokenUri)
            .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                .with("client_id", props.clientId)
                .with("redirect_uri", props.redirectUri)
                .with("code", code)
                .with("code_verifier", oAuthReq.codeVerifier)
            )
            .retrieve()
            .bodyToMono(KakaoTokenResponse::class.java)
            .block()!!

        // 3) 유저 정보 조회
        val userRes = webClient.get()
            .uri(props.userInfoUri)
            .headers { it.setBearerAuth(tokenRes.accessToken) }
            .retrieve()
            .bodyToMono(KakaoUserResponse::class.java)
            .block()!!

        // 4) DB에 회원 저장 또는 조회
        val providerId = userRes.id.toString()
        var user = userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, providerId)
        if (user == null) {
            user = userRepository.save(
                User(
                    providerId = providerId,
                    provider   = AuthProvider.KAKAO,
                    email      = userRes.kakaoAccount.email.orEmpty(),
                    nickname   = userRes.properties.nickname
                )
            )
        }

        val accessToken = jwtTokenProvider.createAccessToken(
            userId = user.id!!,
            roles  = user.roles.toList()
        )
        val refreshToken = jwtTokenProvider.createRefreshToken(user.id)
        tokenSessionRepository.save(TokenSession(refreshToken, user.id, Instant.now()))

        // ↓ 여기서 kakaoProps 가 아니라 jwtProps 에서 꺼내야 합니다.
        return AuthResponse(
            accessToken  = accessToken,
            refreshToken = refreshToken,
            expiresIn    = jwtProps.accessTokenExpirySeconds
        )
    }
}
