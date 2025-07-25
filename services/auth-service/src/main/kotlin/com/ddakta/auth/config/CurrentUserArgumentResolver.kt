package com.ddakta.auth.config

import com.ddakta.auth.common.annotation.CurrentUser
import com.ddakta.auth.service.AuthService
import com.ddakta.auth.domain.entity.User
import com.ddakta.auth.security.AuthenticationPrincipal
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserArgumentResolver(
    private val authService: AuthService
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(CurrentUser::class.java) &&
                (parameter.parameterType == User::class.java ||
                 parameter.parameterType == AuthenticationPrincipal::class.java)
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Any? {
        val authentication = SecurityContextHolder.getContext().authentication

        return if (authentication?.principal is AuthenticationPrincipal) {
            val principal = authentication.principal as AuthenticationPrincipal

            when (parameter.parameterType) {
                User::class.java -> authService.getUserById(principal.userId)
                AuthenticationPrincipal::class.java -> principal
                else -> null
            }
        } else {
            null
        }
    }
}
