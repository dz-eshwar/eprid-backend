package com.rorapps.eprid

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class EpridApplication

fun main(args: Array<String>) {
    runApplication<EpridApplication>(*args)
}
