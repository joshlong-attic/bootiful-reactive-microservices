package com.example.reservationservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
@EnableBinding(Sink.class)
public class ReservationServiceApplication {

	private final ReservationRepository reservationRepository;

	public ReservationServiceApplication(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	@StreamListener
	public void incoming(@Input(Sink.INPUT) Flux<String> names) {
		names
				.map(x -> new Reservation(null, x))
				.flatMap(this.reservationRepository::save)
				.subscribe(x -> System.out.println("saved " + x.getReservationName() + " with ID# " + x.getId()));
	}

	@Bean
	ApplicationRunner run() {
		return args ->
				reservationRepository
						.deleteAll()
						.thenMany(Flux
								.just("A", "B", "C")
								.map(x -> new Reservation(null, x))
								.flatMap(reservationRepository::save))
						.thenMany(reservationRepository.findAll())
						.subscribe(System.out::println);
	}

	@Bean
	RouterFunction<ServerResponse> routes(ReservationRepository rr) {
		return route(GET("/reservations"), req -> ok().body(rr.findAll(), Reservation.class));
	}

	public static void main(String[] args) {
		SpringApplication.run(ReservationServiceApplication.class, args);
	}
}

interface ReservationRepository extends ReactiveMongoRepository<Reservation, String> {
}

@Data
@Document
@AllArgsConstructor
@NoArgsConstructor
class Reservation {

	@Id
	private String id;

	private String reservationName;
}