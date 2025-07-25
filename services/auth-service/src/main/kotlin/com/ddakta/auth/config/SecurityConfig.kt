package com.ddakta.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val oAuth2SuccessHandler: OAuth2SuccessHandler,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint
) {
    
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authz ->
                authz
                    .requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/swagger-ui/**",
                        "/api-docs/**",
                        "/swagger-ui.html",
                        "/actuator/health"
                    ).permitAll()
                    .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .authorizationEndpoint { }
                    .redirectionEndpoint { }
                    .successHandler(oAuth2SuccessHandler)
            }
            .exceptionHandling { it.authenticationEntryPoint(jwtAuthenticationEntryPoint) }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        
        return http.build()
    }
    
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
    
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600
        }
        
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}