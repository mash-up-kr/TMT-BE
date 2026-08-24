package com.tmt.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication(scanBasePackages = ["com.tmt"])
class TmtBootstrapApplication

fun main(args: Array<String>) {
    runApplication<TmtBootstrapApplication>(*args)
}
