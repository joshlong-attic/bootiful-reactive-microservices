package com.example.reservationservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.*;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class ReservationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReservationServiceApplication.class, args);
	}

	@Bean
	RouterFunction<ServerResponse> routes(
		ReservationRepository rr,
		Environment env) {
		return route(GET("/reservations"),
			request -> ok().body(rr.findAll(), Reservation.class))
			.andRoute(GET("/message"), request -> ok().syncBody(env.getProperty("message")));
	}

}


@Component
class Runner implements ApplicationRunner {

	private final ReservationRepository reservationRepository;

	Runner(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {

		Flux<String> stringFlux = Flux
			.just("Josh", "Rada", "Jason", "Ming",
				"Kieran", "Rudrakshi", "Sean", "Sumi");

		Flux<Reservation> flux = stringFlux
			.map(name -> new Reservation(null, name))
			.flatMap(this.reservationRepository::save);

		this.reservationRepository
			.deleteAll()
			.thenMany(flux)
			.thenMany(this.reservationRepository.findAll())
			.subscribe(System.out::println);


	}
}

interface ReservationRepository extends ReactiveMongoRepository<Reservation,
	String> {

}



@Log4j2
@Component
class ReservationService
	implements ApplicationListener<ApplicationReadyEvent> {

	private final ReservationRepository reservationRepository;
	private final ObjectMapper mapper;

	ReservationService(ReservationRepository reservationRepository, ObjectMapper mapper) {
		this.reservationRepository = reservationRepository;
		this.mapper = mapper;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {

		SocketAcceptor sa =
			(connectionSetupPayload, rSocket) -> Mono.just(new AbstractRSocket() {
				@Override
				public Flux<Payload> requestStream(Payload payload) {
					return
						reservationRepository
							.findAll()
							.map(x -> {
								try {
									return mapper.writeValueAsString(x);
								}
								catch (JsonProcessingException e) {
									throw new RuntimeException(e);
								}
							})
							.map(DefaultPayload::create);
				}
			});

		RSocketFactory
			.receive()
			.acceptor(sa)
			.transport(TcpServerTransport.create("localhost", 7000))
			.start()
			.onTerminateDetach()
			.subscribe();
	}
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