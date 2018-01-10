package com.example.reservationservice

import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.Input
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.context.support.beans
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux

@SpringBootApplication
@EnableBinding(Sink::class)
class ReservationServiceApplication


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
					Processor(ref())
				}
				bean {
					ApplicationRunner {
						val repo = ref<ReservationRepository>()
						repo
								.deleteAll()
								.thenMany(Flux.just("A", "B", "C", "D")
										.map { Reservation(reservationName = it) }
										.flatMap { repo.save(it) })
								.thenMany(repo.findAll())
								.subscribe { println(it) }

					}
				}
				bean {
					router {
						val repo = ref<ReservationRepository>()

						GET("/reservations") { ok().body(repo.findAll()) }
					}
				}


			})
			.sources(ReservationServiceApplication::class.java)
			.run(*args)

}

interface ReservationRepository : ReactiveMongoRepository<Reservation, String>

data class Reservation(val id: String? = null, val reservationName: String? = null)