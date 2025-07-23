package com.ddakta.auth.controller

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController {

    @GetMapping("/logout")
    fun logout(response: HttpServletResponse) {
        val successUrl = "http://localhost:8081/"
        val deleteCookie = Cookie("Authorization", null).apply {
            path = "/"
            maxAge = 0
            isHttpOnly = true
        }
        response.addCookie(deleteCookie)
        response.sendRedirect(successUrl)
    }
}
