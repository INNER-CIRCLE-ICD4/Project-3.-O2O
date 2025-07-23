package com.ddakta.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
//@EnableDiscoveryClient
class AuthService

fun main(args: Array<String>) {
    runApplication<AuthService>(*args)
}

