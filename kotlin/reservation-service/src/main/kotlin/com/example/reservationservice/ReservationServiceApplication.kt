package com.example.reservationservice

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.Input
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux

@EnableDiscoveryClient
@SpringBootApplication
class ReservationServiceApplication {

    @Bean
    fun init(rr: ReservationRepository) = ApplicationRunner {
        rr
                .deleteAll()
                .thenMany(Flux.just("Josh", "Eduardo", "Mike")
                        .map { Reservation(reservationName = it) }
                        .flatMap { rr.save(it) })
                .thenMany(rr.findAll())
                .subscribe({ println(it) })
    }

    @Bean
    @RefreshScope
    fun routes(rr: ReservationRepository, @Value("\${message}") msg: String) = router {

        GET("/reservations") { ServerResponse.ok().body(rr.findAll()) }

        GET("/message") { ServerResponse.ok().body(Flux.just(msg)) }
    }


}

@Configuration
@EnableBinding(Sink::class)
class StreamConfig(val rr: ReservationRepository) {

    @StreamListener
    fun processNewReservations(@Input(Sink.INPUT) incoming: Flux<String>) {

        incoming
                .map { it.toUpperCase() }
                .flatMap { rr.save(Reservation(reservationName = it)) }
                .subscribe()
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(ReservationServiceApplication::class.java, *args)
}

interface ReservationRepository : ReactiveMongoRepository<Reservation, String>

@Document
data class Reservation(@Id var id: String? = null, var reservationName: String? = null)