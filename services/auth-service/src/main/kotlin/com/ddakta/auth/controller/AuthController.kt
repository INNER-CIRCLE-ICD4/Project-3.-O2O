package com.ddakta.auth.controller

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    @Value("\${client.web.successUrl}")
    val successUrl: String
) {

    @GetMapping("/logout")
    fun logout(response: HttpServletResponse) {
        val deleteCookie = Cookie("Authorization", null).apply {
            path = "/"
            maxAge = 0
            isHttpOnly = true
        }
        response.addCookie(deleteCookie)
        response.sendRedirect(successUrl)
    }
}
