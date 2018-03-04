package com.example.reservationclient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.netflix.hystrix.HystrixCommands;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;
import java.util.function.Function;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
@EnableCircuitBreaker
@EnableBinding(Source.class)
public class ReservationClientApplication {

	@Bean
	WebClient webClient(LoadBalancerExchangeFilterFunction eff) {
		return WebClient.builder().filter(eff).build();
	}

	@Bean
	RouterFunction<ServerResponse> routes(
			Source src,
			WebClient client) {

		return
				route(POST("/reservations"), req -> ServerResponse.ok().body(
						req
								.bodyToFlux(Reservation.class)
								.map(Reservation::getReservationName)
								.map(x -> MessageBuilder.withPayload(x).build())
								.map(x -> src.output().send(x)), Boolean.class))
						.andRoute(GET("/reservations/names"), req -> {

							Publisher<String> names = client
									.get()
									.uri("http://reservation-service/reservations")
									.retrieve()
									.bodyToFlux(Reservation.class)
									.map(Reservation::getReservationName)
								  .onErrorResume(Exception.class, e -> Flux.just("couldn't load instances!!")) ;

							Publisher<String> fallbackNames = HystrixCommands
									.from(names)
									.eager()
									.commandName("reservation-service-names")
									.fallback(Flux.just("EEK!"))
									.build();
							return ServerResponse.ok().body(fallbackNames, String.class);
						});
	}

	@Bean
	RouteLocator gateway(RouteLocatorBuilder rlb) {
		return rlb
				.routes()
				.route(spec ->
						spec
								.path("/lb")
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