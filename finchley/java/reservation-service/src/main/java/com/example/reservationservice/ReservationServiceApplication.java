package com.example.reservationservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
public class ReservationServiceApplication {

		@Bean
		RouterFunction<ServerResponse> routes(ReservationRepository rr) {
				return route(GET("/reservations"), req -> ok().body(rr.findAll(), Reservation.class));
		}

		@Bean
		ApplicationRunner run(ReservationRepository rr) {
				return args ->
					rr
						.deleteAll()
						.thenMany(Flux.just("josh@joshlong.com", "B@b.com", "C@.com", "D@d.com")
							.map(x -> new Reservation(null, x))
							.flatMap(rr::save))
						.thenMany(rr.findAll())
						.subscribe(System.out::println);
		}

		public static void main(String[] args) {
				SpringApplication.run(ReservationServiceApplication.class, args);
		}
}

interface ReservationRepository extends ReactiveMongoRepository<Reservation, String> {
}

@Document
@AllArgsConstructor
@NoArgsConstructor
@Data
class Reservation {
		@Id
		private String id;
		private String email;
}