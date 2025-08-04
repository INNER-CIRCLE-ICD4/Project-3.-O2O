package com.ddakta.matching.cache

import org.springframework.cache.interceptor.KeyGenerator
import java.lang.reflect.Method

class CustomKeyGenerator : KeyGenerator {
    override fun generate(target: Any, method: Method, vararg params: Any?): Any {
        return buildString {
            append(target.javaClass.simpleName)
            append(".")
            append(method.name)
            append(":")
            params.forEach { param ->
                append(param?.hashCode() ?: "null")
                append("-")
            }
        }
    }
}