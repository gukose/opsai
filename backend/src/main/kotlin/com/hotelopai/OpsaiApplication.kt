package com.hotelopai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableScheduling
class OpsaiApplication

fun main(args: Array<String>) {
    runApplication<OpsaiApplication>(*args)
}
