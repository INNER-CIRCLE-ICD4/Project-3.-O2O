package com.ddakta.auth.controller

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MainController {
    @GetMapping("/")
    fun index(): String {
        val username: String = SecurityContextHolder.getContext().authentication.name
        val role: String = SecurityContextHolder.getContext().authentication.authorities.first().authority
        return "$username:$role"
    }
}
