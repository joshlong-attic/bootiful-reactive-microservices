package com.example.configservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.config.server.EnableConfigServer

@EnableConfigServer
@SpringBootApplication
class ConfigServiceApplication

fun main(args: Array<String>) {
    runApplication<ConfigServiceApplication>(*args)
}
