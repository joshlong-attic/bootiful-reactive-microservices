package com.example.eurekaservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer

@EnableEurekaServer
@SpringBootApplication
class EurekaServiceApplication

fun main(args: Array<String>) {
    runApplication<EurekaServiceApplication>(*args)
}
