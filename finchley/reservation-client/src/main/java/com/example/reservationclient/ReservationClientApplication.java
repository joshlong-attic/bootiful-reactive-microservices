package com.example.reservationclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rsocket.Payload;
import io.rsocket.RSocketFactory;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.netflix.hystrix.HystrixCommands;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
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

	public static void main(String[] args) {
		SpringApplication.run(ReservationClientApplication.class, args);
	}


	@Bean
	MapReactiveUserDetailsService authentication() {
		// @rob_winch became sad :-(
		UserDetails jlong = User.withDefaultPasswordEncoder().username("jlong").password("pw").roles("USER").build();
		UserDetails rwinch = User.withDefaultPasswordEncoder().username("rwinch").password("pw").roles("USER", "ADMIN").build();
		return new MapReactiveUserDetailsService(jlong, rwinch);
	}

	@Bean
	SecurityWebFilterChain authorization(ServerHttpSecurity http) {
		http.httpBasic();
		http.authorizeExchange()
			.pathMatchers("/proxy").authenticated()
			.anyExchange().permitAll();
		http.csrf().disable();
		return http.build();
	}

	@Bean
	RedisRateLimiter redisRateLimiter() {
		return new RedisRateLimiter(5, 7);
	}


	@Bean
	RouterFunction<ServerResponse> adapter(ReservationClient client) {
		return route(GET("/reservations/names"),
			serverRequest -> {

				Flux<String> names = client
					.getAllReservations()
					.map(Reservation::getReservationName);

				Publisher<String> fallback = HystrixCommands
					.from(names)
					.fallback(Flux.just("EEK!"))
					.commandName("names")
					.eager()
					.build();

				return ServerResponse.ok().body(fallback, String.class);
			});
	}

	@Bean
	RouteLocator gateway(RouteLocatorBuilder rlb) {
		return rlb
			.routes()
			.route(
				rSpec ->
					rSpec
						.host("*.foo.ca").and().path("/proxy")
						.filters(fSpec -> fSpec
							.setPath("/reservations")
							.addResponseHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
							.requestRateLimiter(rc -> rc.setRateLimiter(redisRateLimiter()))
						)
						.uri("http://localhost:8080")
			)
			.build();
	}

	@Bean
	WebClient client(WebClient.Builder builder) {
		return builder.build();
	}

}


@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {
	private Integer id;
	private String reservationName;
}
/*

@Component
class ReservationClient {

	private final WebClient webClient;

	ReservationClient(WebClient webClient) {
		this.webClient = webClient;
	}

	Flux<Reservation> getAllReservations() {
		return this.webClient
			.get()
			.uri("http://localhost:8080/reservations")
			.retrieve()
			.bodyToFlux(Reservation.class);
	}
}
*/


@Component
class ReservationClient {


	private final ObjectMapper objectMapper;

	ReservationClient(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	Reservation from(String json) {
		try {
			return this.objectMapper.readValue(json, Reservation.class);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	Flux<Reservation> getAllReservations() {
		return RSocketFactory
			.connect()
			.transport(TcpClientTransport.create(7000))
			.start()
			.flatMapMany(rs -> rs.requestStream(DefaultPayload.create(new byte[0])))
			.map(Payload::getDataUtf8)
			.map(this::from);

	}
}