package com.example.reservationclient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
@EnableBinding(Source.class)
public class ReservationClientApplication {

	@Bean
	RouterFunction<ServerResponse> routes(Source src) {
		return route(
				POST("/reservations"),
				req ->
						ServerResponse.ok().body(
								req
										.bodyToFlux(Reservation.class)
										.map(Reservation::getReservationName)
										.map(x -> MessageBuilder.withPayload(x).build())
										.map(x -> src.output().send(x)), Boolean.class));
	}

	@Bean
	RouteLocator gateway(RouteLocatorBuilder rlb) {
		return rlb
				.routes()
				.route(spec -> spec
						.path("/reservations")
						.filters(fs -> fs.setPath("/reservations"))
						.uri("lb://reservation-service/"))
				.build();
	}

	public static void main(String[] args) {
		SpringApplication.run(ReservationClientApplication.class, args);
	}
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {

	@Id
	private String id;

	private String reservationName;
}