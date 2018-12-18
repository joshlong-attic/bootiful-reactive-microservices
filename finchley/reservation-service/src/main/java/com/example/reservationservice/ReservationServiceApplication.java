package com.example.reservationservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.RSocketFactory;
import io.rsocket.SocketAcceptor;
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
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.r2dbc.repository.query.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@EnableR2dbcRepositories
@SpringBootApplication
public class ReservationServiceApplication extends AbstractR2dbcConfiguration {

	@Bean
	RouterFunction<ServerResponse> routes(ReservationRepository rr) {
		return route(GET("/reservations"), r -> ok().body(rr.findAll(), Reservation.class));
	}

	public static void main(String[] args) {
		SpringApplication.run(ReservationServiceApplication.class, args);
	}

	@Override
	public ConnectionFactory connectionFactory() {
		PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration
			.builder()
			.host("localhost")
			.password("0rd3rs")
			.username("orders")
			.database("orders")
			.build();
		return new PostgresqlConnectionFactory(config);
	}
}


@Component
class RsocketServer {

	private final ReservationRepository reservationRepository;
	private final ObjectMapper objectMapper;
	private final TcpServerTransport tcp = TcpServerTransport.create(7000);

	RsocketServer(ReservationRepository reservationRepository, ObjectMapper objectMapper) {
		this.reservationRepository = reservationRepository;
		this.objectMapper = objectMapper;
	}

	private String toJson(Reservation r) {
		try {
			return this.objectMapper.writeValueAsString(r);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void serve() throws Exception {

		var abstractRSocket = new AbstractRSocket() {

			@Override
			public Flux<Payload> requestStream(Payload payload) {
				return reservationRepository.findAll()
					.map(RsocketServer.this::toJson)
					.map(DefaultPayload::create);
			}
		};

		SocketAcceptor socketAcceptor = (connectionSetupPayload, rSocket) -> Mono.just(abstractRSocket);

		RSocketFactory
			.receive()
			.acceptor(socketAcceptor)
			.transport(this.tcp)
			.start()
			.subscribe();

	}

}


@RestController
@Log4j2
class ReservationRestController {

	private final ReservationRepository reservationRepository;

	ReservationRestController(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}


	@DeleteMapping("/reservations/{id}")
	Mono<Void> deleteById(@PathVariable Integer id) {
		return this.reservationRepository
			.deleteById(id);
	}

}

@Log4j2
@Component
class SampleDataInitializer {

	private final ReservationRepository reservationRepository;

	SampleDataInitializer(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void writeSampleData() throws Exception {

		Publisher<Reservation> reservationFlux = Flux
			.just("Josh", "Jane", "Tammie", "Brian", "Zhen", "Madhura", "Kimly", "Cornelia")
			.map(name -> new Reservation(null, name))
			.flatMap(this.reservationRepository::save);

		this.reservationRepository
			.deleteAll()
			.thenMany(reservationFlux)
			.thenMany(this.reservationRepository.findAll())
			.subscribe(log::info);
	}
}

interface ReservationRepository extends ReactiveCrudRepository<Reservation, Integer> {

	@Query("select * from reservation where name = $1")
	Flux<Reservation> findByName(String name);
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class Reservation {

	@Id
	private String id;
	private String name;
}


