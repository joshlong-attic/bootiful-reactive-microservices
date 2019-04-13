package com.example.reservationservice;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class ReservationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReservationServiceApplication.class, args);
	}

	@Bean
	RouterFunction<ServerResponse> routes(ReservationRepository rr) {
		return route()
			.GET("/reservations", serverRequest -> ok().body(rr.findAll(), Reservation.class))
			.build();
	}
}

@Configuration
class WebsocketConfiguration {

	@Bean
	WebSocketHandlerAdapter webSocketHandlerAdapter() {
		return new WebSocketHandlerAdapter();
	}

	@Bean
	WebSocketHandler webSocketHandler(TimedGreetingsProducer tgp) {
		return webSocketSession -> webSocketSession.send(tgp.greet().map(webSocketSession::textMessage));
	}

	@Bean
	SimpleUrlHandlerMapping simpleUrlHandlerMapping(WebSocketHandler wsh) {
		return new SimpleUrlHandlerMapping() {
			{
				setOrder(10);
				setUrlMap(Map.of("/ws/greetings", wsh));
			}
		};
	}
}

@Component
class TimedGreetingsProducer {

	Flux<String> greet() {
		return this.greet("World");
	}

	Flux<String> greet(String name) {
		return Flux
			.fromStream(Stream.generate(() -> "Hello " + name + " @ " + Instant.now()))
			.delayElements(Duration.ofSeconds(1));
	}
}

@Component
class SampleDataInitializer {

	private final ReservationRepository reservationRepository;

	SampleDataInitializer(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void go() {

		var names = Flux
			.just("Josh", "Madhura", "Dr. Syer", "Cornelia", "Stephane", "Neven", "Olga", "Violetta")
			.map(name -> new Reservation(null, name))
			.flatMap(this.reservationRepository::save);

		this.reservationRepository
			.deleteAll()
			.thenMany(names)
			.thenMany(this.reservationRepository.findAll())
			.subscribe(System.out::println);
	}
}

@Configuration
@EnableR2dbcRepositories
class R2dbcConfig extends AbstractR2dbcConfiguration {

	@Override
	public ConnectionFactory connectionFactory() {
		return new PostgresqlConnectionFactory(
			PostgresqlConnectionConfiguration
				.builder()
				.password("0rd3rs")
				.username("orders")
				.host("localhost")
				.database("orders")
				.applicationName("orders")
				.build()
		);
	}
}


@Controller
class RSocketGreetingService {

	private final TimedGreetingsProducer timedGreetingsProducer;

	RSocketGreetingService(TimedGreetingsProducer timedGreetingsProducer) {
		this.timedGreetingsProducer = timedGreetingsProducer;
	}

	@MessageMapping("greetings")
	Flux<GreetingsResponse> greet(GreetingsRequest name) {
		return this.timedGreetingsProducer
			.greet(name.getName())
			.map(GreetingsResponse::new);
	}
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingsRequest {
	private String name;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingsResponse {
	private String greeting;
}

interface ReservationRepository extends ReactiveCrudRepository<Reservation, Integer> {
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {
	@Id
	private Integer id;
	private String name;
}