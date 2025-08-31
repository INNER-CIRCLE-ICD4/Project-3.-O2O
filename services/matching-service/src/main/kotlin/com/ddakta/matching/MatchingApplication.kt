package com.ddakta.matching

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableCaching
class MatchingApplication

fun main(args: Array<String>) {
    runApplication<MatchingApplication>(*args)
}
