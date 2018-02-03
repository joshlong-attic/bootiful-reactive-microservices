package com.example.reservationservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
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

@Log
@SpringBootApplication
@EnableBinding(Sink.class)
public class ReservationServiceApplication {

	private final ReservationRepository repo;

	public ReservationServiceApplication(ReservationRepository repo) {
		this.repo = repo;
	}

	@StreamListener
	public void incoming(@Input(Sink.INPUT) Flux<String> names) {
		names
				.map(x -> new Reservation(null, x))
				.flatMap(repo::save)
				.subscribe(x -> log.info(x.toString()));
	}

	@Bean
	ApplicationRunner runner() {
		return args -> repo
				.deleteAll()
				.thenMany(
						Flux.just("A", "B", "C", "D")
								.map(n -> new Reservation(null, n))
								.flatMap(repo::save))
				.thenMany(repo.findAll())
				.subscribe(System.out::println);
	}

	@Bean
	RouterFunction routerFunction() {
		return route(GET("/reservations"), request -> ServerResponse.ok().body(repo.findAll(), Reservation.class));
	}

	public static void main(String[] args) {
		SpringApplication.run(ReservationServiceApplication.class, args);
	}
}

interface ReservationRepository extends ReactiveMongoRepository<Reservation, String> {
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document
class Reservation {

	@Id
	private String id;
	private String reservationName;
}