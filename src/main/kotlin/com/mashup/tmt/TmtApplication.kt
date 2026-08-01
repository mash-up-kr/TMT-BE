package com.mashup.tmt

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TmtApplication

fun main(args: Array<String>) {
    runApplication<TmtApplication>(*args)
}
