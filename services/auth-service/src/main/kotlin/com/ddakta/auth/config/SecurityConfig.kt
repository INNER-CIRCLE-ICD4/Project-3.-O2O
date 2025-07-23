package com.ddakta.auth.config

import com.ddakta.auth.filter.JwtFilter
import com.ddakta.auth.oauth.OAuth2LoginSuccessHandler
import com.ddakta.auth.service.JwtService
import com.ddakta.auth.service.OAuth2UserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration

@Configuration
@EnableWebSecurity
class SecurityConfig(
    val userService: OAuth2UserService,
    val loginSuccessHandler: OAuth2LoginSuccessHandler,
    val jwtService: JwtService
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests { it.
                requestMatchers("/","/login", "logout").permitAll()
                .anyRequest().authenticated()
            }
            .addFilterBefore(JwtFilter(jwtService), UsernamePasswordAuthenticationFilter::class.java)
            .oauth2Login { oAuth -> oAuth
                .userInfoEndpoint { it.userService (userService) }
                .successHandler (loginSuccessHandler)
            }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .cors { corsCustomizer ->
                corsCustomizer.configurationSource { request ->
                    CorsConfiguration().apply {
                        allowedOrigins = listOf("http://localhost:8081")
                        allowedMethods = listOf("*")
                        allowCredentials = true
                        allowedHeaders = listOf("*")
                        maxAge = 3600L
                        exposedHeaders = listOf("Set-Cookie", "Authorization")
                    }
                }
            }
            .build()
    }

}
