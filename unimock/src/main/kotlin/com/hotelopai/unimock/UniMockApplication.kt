package com.hotelopai.unimock

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class UniMockApplication

fun main(args: Array<String>) {
    runApplication<UniMockApplication>(*args)
}
