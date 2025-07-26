package com.ddakta.common.user.annotation

import com.ddakta.common.user.cache.UserCacheService
import com.ddakta.common.user.dto.PassengerInfo
import com.ddakta.common.user.dto.RiderInfo
import com.ddakta.common.user.dto.UserInfo
import com.ddakta.common.user.exception.UnauthorizedException
import com.ddakta.common.user.exception.UserNotFoundException
import com.ddakta.common.user.exception.UserTypeMismatchException
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID
import javax.crypto.SecretKey
import jakarta.servlet.http.HttpServletRequest

@Component
class UserInfoArgumentResolver(
    private val userCacheService: UserCacheService,
    @Value("\${ddakta.jwt.secret}") private val jwtSecret: String
) : HandlerMethodArgumentResolver {
    
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }
    
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(WithUserInfo::class.java) &&
               UserInfo::class.java.isAssignableFrom(parameter.parameterType)
    }
    
    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Any? {
        val request = webRequest.nativeRequest as HttpServletRequest
        val userId = extractUserIdFromToken(request) 
            ?: throw UnauthorizedException("User ID not found in token")
        
        val annotation = parameter.getParameterAnnotation(WithUserInfo::class.java)!!
        val userInfo = userCacheService.getUserInfo(userId)
        
        if (userInfo == null && annotation.required) {
            throw UserNotFoundException(userId)
        }
        
        // 타입 체크
        userInfo?.let {
            when {
                parameter.parameterType == RiderInfo::class.java && it !is RiderInfo -> {
                    throw UserTypeMismatchException("Expected RiderInfo but found ${it::class.simpleName}")
                }
                parameter.parameterType == PassengerInfo::class.java && it !is PassengerInfo -> {
                    throw UserTypeMismatchException("Expected PassengerInfo but found ${it::class.simpleName}")
                }
                else -> {
                    // Type is valid or no specific type check needed
                }
            }
        }
        
        return userInfo
    }
    
    private fun extractUserIdFromToken(request: HttpServletRequest): UUID? {
        val authHeader = request.getHeader("Authorization") ?: return null
        
        if (!(authHeader as String).startsWith("Bearer ")) {
            return null
        }
        
        val token = authHeader.substring(7)
        
        return try {
            val claims: Claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .body
            
            claims.get("userId", String::class.java)?.let { UUID.fromString(it) }
        } catch (e: Exception) {
            null
        }
    }
}