package com.example.reservationservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class ReservationServiceApplication {

	@Bean
	RouterFunction<ServerResponse> routes(ReservationRepository rr,
																																							Environment env) {
		return
			route(GET("/reservations"), r -> ok().body(rr.findAll(), Reservation.class))
				.andRoute(GET("/message"), request -> ServerResponse.ok().syncBody(env.getProperty("message")));
	}

	public static void main(String[] args) {
		SpringApplication.run(ReservationServiceApplication.class, args);
	}
}

@Log4j2
@Component
class Initializer {

	private final ReservationRepository reservationRepository;

	Initializer(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void listen() throws Exception {

		Flux<Reservation> saved = Flux.just("A", "B", "C")
			.map(nom -> new Reservation(null, nom))
			.flatMap(this.reservationRepository::save);

		this.reservationRepository
			.deleteAll()
			.thenMany(saved)
			.thenMany(this.reservationRepository.findAll())
			.subscribe(log::info);
	}
}


interface ReservationRepository extends ReactiveMongoRepository<Reservation, String> {
}

@Document
@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {
	@Id
	private String id;
	private String reservationName;
}
