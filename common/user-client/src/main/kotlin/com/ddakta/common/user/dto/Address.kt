package com.ddakta.common.user.dto

data class Address(
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String,
    val location: Location? = null
)