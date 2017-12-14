package com.example.reservationservice

import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.Input
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.beans
import org.springframework.core.env.Environment
import org.springframework.core.env.get
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux

@SpringBootApplication
class ReservationServiceApplication

@Configuration
@EnableBinding(Sink::class)
class Processor(val rr: ReservationRepository) {

	@StreamListener
	fun go(@Input(Sink.INPUT) incoming: Flux<String>) {
		incoming
				.map { it.toUpperCase() }
				.flatMap { rr.save(Reservation(reservationName = it)) }
				.subscribe()
	}
}

fun main(args: Array<String>) {
	SpringApplicationBuilder()
			.initializers(beans {
				bean {
					ApplicationRunner {
						val repo = ref<ReservationRepository>()
						val names = Flux.just("Mario", "Mia", "Tam Mie", "Josh")
								.map { Reservation(reservationName = it) }
								.flatMap { repo.save(it) }
						repo
								.deleteAll()
								.thenMany(names)
								.thenMany(repo.findAll())
								.subscribe { println(it) }
					}
				}
				bean {
					val ref = ref<ReservationRepository>()
					val env = ref<Environment>()
					router {
						GET("/message") { ok().body(Flux.just(env["message"])) }
						GET("/reservations") { ok().body(ref.findAll()) }
					}
				}
			})
			.sources(ReservationServiceApplication::class.java)
			.run(*args)
}

interface ReservationRepository : ReactiveMongoRepository<Reservation, String>

@Document
data class Reservation(@Id var id: String? = null, var reservationName: String? = null)