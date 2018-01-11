package com.example.reservationservice

import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.Input
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.context.support.beans
import org.springframework.core.env.Environment
import org.springframework.core.env.get
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux

@SpringBootApplication
@EnableBinding(Sink::class)
class ReservationServiceApplication


class Processor(val repo: ReservationRepository) {

	@StreamListener
	fun incoming(@Input(Sink.INPUT) incomingNames: Flux<String>) {
		incomingNames
				.map { Reservation(reservationName = it) }
				.flatMap { repo.save(it) }
				.subscribe { println("new reservation ${it.reservationName}.") }
	}
}

fun main(args: Array<String>) {

	SpringApplicationBuilder()
			.sources(ReservationServiceApplication::class.java)
			.initializers(beans {
				bean {
					Processor(ref())
				}
				bean {
					val repo = ref<ReservationRepository>()
					val env = ref<Environment>()
					router {
						GET("/reservations") { ServerResponse.ok().body(repo.findAll()) }
						GET("/message") { ServerResponse.ok().body(Flux.just(env["message"])) }
					}
				}
				bean {
					ApplicationRunner {
						val repo = ref<ReservationRepository>()
						repo
								.deleteAll()
								.thenMany(Flux.just("Josh", "Karsten", "Mark",
										"Stephan", "Matthias", "Johannes")
										.map { Reservation(reservationName = it) }
										.flatMap { repo.save(it) })
								.thenMany(repo.findAll())
								.subscribe { println(it) }
					}
				}
			})
			.run(*args)
}

interface ReservationRepository : ReactiveMongoRepository<Reservation, String>

@Document
data class Reservation(@Id val id: String? = null, val reservationName: String? = null)
