package com.example.reservationclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rsocket.Payload;
import io.rsocket.RSocketFactory;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import java.io.IOException;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class ReservationClientApplication {


	@Bean
	@LoadBalanced
	WebClient webClient(WebClient.Builder builder) {
		return builder.build();
	}

	@Bean
	RouterFunction<ServerResponse> routes(ReservationClient client) {
		return route(GET("/reservations/names"),
			request -> {
/*
				Flux<String> stringFlux = client
					.get()
					.uri("http://reservation-service/reservations")
					.retrieve()
					.bodyToFlux(Reservation.class)
					.map(Reservation::getReservationName);*/

				Flux<String> allReservations = client.getAllReservations()
					.map(r -> r.getReservationName());

				/*Publisher<String> stringPublisher = HystrixCommands
					.from(stringFalux)
					.eager()
					.commandName("names")
					.fallback(Flux.just("EEK!"))
					.build();

				*/
				return ServerResponse.ok().body(allReservations, String.class);
			});
	}

	public static void main(String[] args) {
		SpringApplication.run(ReservationClientApplication.class, args);
	}

	@Bean
	MapReactiveUserDetailsService authentication() {
		return new MapReactiveUserDetailsService(
			User.withDefaultPasswordEncoder()
				.username("jlong").password("pw").roles("USER").build(),
			User.withDefaultPasswordEncoder()
				.username("rwinch").password("pw").roles("USER", "ADMIN").build()
		);
	}

	@Bean
	SecurityWebFilterChain authorization(ServerHttpSecurity http) {
		http.httpBasic();
		http.csrf().disable();
		http
			.authorizeExchange()
			.pathMatchers("/proxy").authenticated()
			.anyExchange().permitAll();
		return http.build();
	}

	@Bean
	RedisRateLimiter redisRateLimiter() {
		return new RedisRateLimiter(5, 7);
	}

	@Bean
	RouteLocator gateway(RouteLocatorBuilder rlb) {
		return
			rlb
				.routes()
				.route(
					rSpec -> rSpec
						.host("*.foo.com").and().path("/proxy")
						.filters(
							gatewayFilterSpec ->
								gatewayFilterSpec
									.addResponseHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
									.setPath("/reservations")
									.requestRateLimiter(
										config -> config
											.setRateLimiter(redisRateLimiter())
									)
						)
						.uri("lb://reservation-service")
				)
				.build();
	}

}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {

	private String id;
	private String reservationName;
}


@Component
class ReservationClient {

	private final ObjectMapper objectMapper;

	private final TcpClientTransport localhost =
		TcpClientTransport.create("localhost", 7000);

	ReservationClient(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public Flux<Reservation> getAllReservations() {

		return RSocketFactory
			.connect()
			.transport(localhost)
			.start()
			.flatMapMany(socket ->
				socket
					.requestStream(DefaultPayload.create(new byte[0]))
					.map(Payload::getDataUtf8)
					.map(this::from)
					.doFinally(signal -> socket.dispose())
			);
	}

	private Reservation from(String obj) {
		try {
			return this.objectMapper
				.readValue(obj, Reservation.class);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
