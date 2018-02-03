package com.example.reservationservice

import org.apache.commons.logging.LogFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.Input
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.context.annotation.Bean
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux

@SpringBootApplication
@EnableBinding(Sink::class)
class ReservationServiceApplication(val repo: ReservationRepository) {

	private val log = LogFactory.getLog(javaClass)

	@StreamListener
	fun incoming(@Input(Sink.INPUT) names: Flux<String>) {
		names
				.map { Reservation(reservationName = it) }
				.flatMap { repo.save(it) }
				.subscribe { println("just saved ${it.id} / ${it.reservationName}") }
	}

	@get:Bean
	val routes = router {
		GET("/reservations") {
			ServerResponse.ok().body(repo.findAll())
		}
	}

	@get:Bean
	val data = ApplicationRunner {
		repo
				.deleteAll()
				.thenMany(
						Flux.just("A", "B", "C", "D")
								.map { Reservation(reservationName = it) }
								.flatMap { repo.save(it) })
				.thenMany(repo.findAll())
				.subscribe {
					log.info("id# ${it.id} reservationName: '${it.reservationName}' ")
				}
	}
}

interface ReservationRepository : ReactiveMongoRepository<Reservation, String>

@Document
data class Reservation(@Id val id: String? = null, val reservationName: String? = null)

fun main(args: Array<String>) {
	runApplication<ReservationServiceApplication>(*args)
}