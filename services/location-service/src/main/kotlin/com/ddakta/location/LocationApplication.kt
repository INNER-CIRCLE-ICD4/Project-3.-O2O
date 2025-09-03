package com.ddakta.location

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.runApplication
//import org.springframework.cloud.netflix.eureka.EnableEurekaClient

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
//@EnableEurekaClient
class LocationServiceApplication

fun main(args: Array<String>) {
    runApplication<LocationServiceApplication>(*args)
}
