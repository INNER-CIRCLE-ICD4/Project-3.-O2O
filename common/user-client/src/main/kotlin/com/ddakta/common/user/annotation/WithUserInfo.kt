package com.ddakta.common.user.annotation

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class WithUserInfo(
    val required: Boolean = true
)