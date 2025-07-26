package com.ddakta.auth.config

import com.ddakta.auth.oauth.OAuth2LoginSuccessHandler
import com.ddakta.auth.security.CustomUserDetailsService
import com.ddakta.auth.service.JwtService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter


@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtService: JwtService,
    private val oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
    private val userDetailsService: CustomUserDetailsService
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .formLogin { form ->
                form.loginProcessingUrl("/api/auth/login")
                    .permitAll()
            }
            .userDetailsService(userDetailsService)
            .authorizeHttpRequests { authz ->
                authz.requestMatchers("/api/auth/**").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2.successHandler(oAuth2LoginSuccessHandler)
            }
            .addFilterBefore(JwtFilter(jwtService), UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }
}
