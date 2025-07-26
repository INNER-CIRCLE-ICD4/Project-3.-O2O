package com.ddakta.auth.oauth

import com.ddakta.auth.entity.UserDto
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

class OAuth2UserDetails(private val user: UserDto): OAuth2User {
    override fun getName(): String = user.name

    override fun getAttributes(): Map<String?, Any?>? {
        return null;
    }

    override fun getAuthorities(): Collection<GrantedAuthority?>? {
        return listOf(SimpleGrantedAuthority(user.role))
    }

    fun getUsername() = user.username
    fun getEmail() = user.email
}
