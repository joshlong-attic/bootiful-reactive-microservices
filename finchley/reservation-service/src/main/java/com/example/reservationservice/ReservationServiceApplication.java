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
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.function.DatabaseClient;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class ReservationServiceApplication {

	@Bean
	RouterFunction<ServerResponse> routes(ReservationRepository rr) {
		return route(GET("/reservations"),
			r -> ServerResponse.ok().body(rr.findAll(), Reservation.class));
	}

	public static void main(String[] args) {
		SpringApplication.run(ReservationServiceApplication.class, args);
	}
}

@Configuration
class R2dbcConfiguration {

	@Bean
	ReservationRepository reservationRepository(
		R2dbcRepositoryFactory rff) {
		return rff.getRepository(ReservationRepository.class);
	}

	@Bean
	R2dbcRepositoryFactory r2dbcRepositoryFactory(DatabaseClient dbc) {
		RelationalMappingContext relationalMappingContext =
			new RelationalMappingContext();
		relationalMappingContext.afterPropertiesSet();
		return new R2dbcRepositoryFactory(dbc, relationalMappingContext);
	}

	@Bean
	DatabaseClient dbc(ConnectionFactory cf) {
		return DatabaseClient.create(cf);
	}

	@Bean
	PostgresqlConnectionFactory postgresqlConnectionFactory() {
		PostgresqlConnectionConfiguration postgresqlConnectionConfiguration =
			PostgresqlConnectionConfiguration
				.builder()
				.database("orders")
				.host("localhost")
				.password("0rd3rs")
				.username("orders")
				.build();
		return new PostgresqlConnectionFactory(postgresqlConnectionConfiguration);
	}


}


interface ReservationRepository
	extends ReactiveCrudRepository<Reservation, String> {
}

@Log4j2
@Component
class SampleDataInitializer {

	private final ReservationRepository reservationRepository;

	SampleDataInitializer(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}


	@EventListener(ApplicationReadyEvent.class)
	public void ready() throws Exception {

		Flux<Reservation> data = Flux
			.just("Joe", "Luke", "Josh", "Mark", "Michelle", "Kimly", "Tammie", "Cornelia")
			.map(name -> new Reservation(null, name))
			.flatMap(this.reservationRepository::save);

		this.reservationRepository
			.findAll().flatMap(this.reservationRepository::delete)
			.thenMany(data)
			.thenMany(this.reservationRepository.findAll())
			.subscriberContext(Context.of("a", "b"))
			.subscribe(log::info);

	}
}

@Component
class RSocketServer {

	private final ObjectMapper objectMapper;
	private final ReservationRepository reservationRepository;

	RSocketServer(ObjectMapper objectMapper, ReservationRepository reservationRepository) {
		this.objectMapper = objectMapper;
		this.reservationRepository = reservationRepository;
	}


	String from(Reservation r) {
		try {
			return this.objectMapper.writeValueAsString(r);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void serve() throws Exception {

		RSocket r = new AbstractRSocket() {

			@Override
			public Flux<Payload> requestStream(Payload payload) {
				return reservationRepository.findAll()
					.map(r -> from(r))
					.map(DefaultPayload::create);// todo
			}
		};

		SocketAcceptor socketAcceptor =
			(connectionSetupPayload, rSocket) -> Mono.just(r);


		RSocketFactory
			.receive()
			.acceptor(socketAcceptor)
			.transport(TcpServerTransport.create(7000))
			.start()
			.subscribe();

	}
}


@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {

	@Id
	private Integer id;

	@Column("name")
	private String reservationName;
}
