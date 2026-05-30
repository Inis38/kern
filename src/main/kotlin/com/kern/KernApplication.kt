package com.kern

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KernApplication

fun main(args: Array<String>) {
    runApplication<KernApplication>(*args)
}
