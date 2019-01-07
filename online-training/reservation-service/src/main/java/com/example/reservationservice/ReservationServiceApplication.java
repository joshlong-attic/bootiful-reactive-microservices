package com.example.reservationservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import io.rsocket.*;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.function.DatabaseClient;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.r2dbc.repository.query.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.stream.Stream;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class ReservationServiceApplication {

	@Bean
	RouterFunction<ServerResponse> routes(ReservationRepository rr) {
		return route(GET("/reservations"), r -> ok().body(rr.findAll(), Reservation.class));
	}

	public static void main(String[] args) {
		SpringApplication.run(ReservationServiceApplication.class, args);
	}
}


@Component
class RSocketServer {

	private final ReservationRepository reservationRepository;
	private final TcpServerTransport tcpServerTransport = TcpServerTransport.create(7000);
	private final ObjectMapper objectMapper;

	RSocketServer(ReservationRepository reservationRepository, ObjectMapper objectMapper) {
		this.reservationRepository = reservationRepository;
		this.objectMapper = objectMapper;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void serve() throws Exception {

		SocketAcceptor socketAcceptor = (setup, requestRS) -> {

			RSocket reply = new AbstractRSocket() {

				@Override
				public Flux<Payload> requestStream(Payload ignoreMe) {
					return reservationRepository.findAll()
						.map(RSocketServer.this::to)
						.map(DefaultPayload::create);
				}
			};

			return Mono.just(reply);
		};

		RSocketFactory
			.receive()
			.acceptor(socketAcceptor)
			.transport(this.tcpServerTransport)
			.start()
			.subscribe();
	}

	private String to(Reservation r) {
		try {
			return this.objectMapper.writeValueAsString(r);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}

@Configuration
class WebSocketConfiguration {

	private final GreetingsService greetingsService;

	WebSocketConfiguration(GreetingsService greetingsService) {
		this.greetingsService = greetingsService;
	}

	@Bean
	WebSocketHandlerAdapter wsha() {
		return new WebSocketHandlerAdapter();
	}

	private Flux<WebSocketMessage> doGreet(WebSocketSession wss) {
		return Flux.from(this.greetingsService.greet())
			.map(wss::textMessage);
	}

	@Bean
	WebSocketHandler wsh() {
		return webSocketSession -> webSocketSession.send(doGreet(webSocketSession));
	}

	@Bean
	HandlerMapping hm() {
		SimpleUrlHandlerMapping simpleUrlHandlerMapping = new SimpleUrlHandlerMapping();
		simpleUrlHandlerMapping.setOrder(10);
		simpleUrlHandlerMapping.setUrlMap(Collections.singletonMap("/ws/greetings", this.wsh()));
		return simpleUrlHandlerMapping;
	}
}

@Configuration
@EnableR2dbcRepositories
class R2dbcConfig extends AbstractR2dbcConfiguration {

	private final PostgresqlConnectionFactory pgcf = new PostgresqlConnectionFactory(
		PostgresqlConnectionConfiguration.builder().username("orders").password("0rd3rz").host("localhost").database("orders").build()
	);

	@Override
	public ConnectionFactory connectionFactory() {
		return this.pgcf;
	}
}

@Component
@Log4j2
class DbClientListener {

	private final DatabaseClient databaseClient;

	DbClientListener(DatabaseClient databaseClient) {
		this.databaseClient = databaseClient;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void dbc() throws Exception {
		this.databaseClient.select().from("reservation").as(Reservation.class).fetch().all().subscribe(log::info);
	}
}


/*
@RestController
class ReservationRestController {

	private final ReservationRepository reservationRepository;

	ReservationRestController(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	@GetMapping("/reservations")
	Flux<Reservation> get() {
		return this.reservationRepository.findAll();
	}

	@DeleteMapping("/reservations")
	Flux<Reservation> delete () {
		return this.reservationRepository.findAll();
	}
}
*/
@Service
class GreetingsService {

	Publisher<String> greet() {
		return Flux
			.fromStream(Stream.generate(() -> "Hello world @ " + Instant.now().toString()))
			.delayElements(Duration.ofSeconds(1))
			.log();
	}
}

@RestController
class SseController {

	SseController(GreetingsService greetingsService) {
		this.greetingsService = greetingsService;
	}

	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE, value = "/sse")
	Publisher<String> sse() {
		return this.greetingsService.greet();
	}

	private final GreetingsService greetingsService;
}

@Component
@Log4j2
class Listener {

	private final ReservationRepository reservationRepository;

	Listener(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void go() throws Exception {

		this.reservationRepository
			.deleteAll()
			.thenMany(Flux.just("A", "B", "C", "D")
				.map(name -> new Reservation(null, name))
				.flatMap(this.reservationRepository::save))
			.thenMany(this.reservationRepository.findAll())
			.subscribe(log::info);

	}
}

interface ReservationRepository extends ReactiveCrudRepository<Reservation, String> {

	@Query("select * from reservation where name = $1 ")
	Flux<Reservation> findByName(String name);
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {

	@Id
	private Integer id;
	private String name;
}