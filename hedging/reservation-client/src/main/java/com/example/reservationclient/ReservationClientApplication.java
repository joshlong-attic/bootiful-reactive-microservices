package com.example.reservationclient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
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

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class ReservationClientApplication {

	@Bean
	MapReactiveUserDetailsService authentication() {

		UserDetails jlong = User.withDefaultPasswordEncoder()
			.password("pw").username("jlong").roles("USER").build();

		UserDetails rwinch = User.withDefaultPasswordEncoder()
			.password("pw").username("rwinch").roles("USER", "ADMIN").build();

		return new MapReactiveUserDetailsService(jlong, rwinch);
	}

	@Bean
	SecurityWebFilterChain authorization(ServerHttpSecurity http) {
		http.csrf().disable();
		http.httpBasic();
		http.authorizeExchange()
			.pathMatchers("/proxy").authenticated()
			.anyExchange().permitAll();
		return http.build();
	}

	@Bean
	RouteLocator gateway(RouteLocatorBuilder rlb) {
		return rlb
			.routes()
			.route(s -> s.host("*.foo.tw").and().path("/proxy")
				.filters(f -> f
					.setPath("/reservations")
					.addResponseHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
					.requestRateLimiter(rl -> rl.setRateLimiter(this.redisRateLimiter()))
				)
				.uri("lb://reservation-service/")
			)
			.build();
	}

	@Bean
		//@LoadBalanced
	WebClient webClient(WebClient.Builder builder) {
		return builder.build();
	}

	@Bean
	RedisRateLimiter redisRateLimiter() {
		return new RedisRateLimiter(5, 7);
	}

	@Bean
	RouterFunction<ServerResponse> routes(ReservationClient client) {
		return route(GET("/reservations/names"), request -> {

			Flux<String> map = client
				.getAllReservations()
				.map(Reservation::getReservationName);

			return ServerResponse.ok().body(map, String.class);
		});
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

@Component
@Log4j2
class ReservationClient {

	private final WebClient webClient;
	private final DiscoveryClient client;

	ReservationClient(WebClient webClient, DiscoveryClient client) {
		this.webClient = webClient;
		this.client = client;
	}

	Flux<Reservation> getAllReservations() {
		List<ServiceInstance> instances = this.client.getInstances("reservation-service");
		int max = 2;
		int min = Math.min(max, instances.size());
		List<Flux<Reservation>> collect = instances
			.subList(0, min)
			.stream()
			.map(this::call)
			.collect(Collectors.toList());
		return Flux.first(collect);
	}

	private String uri(ServiceInstance si) {
		return "http://" + si.getHost() + ':' + si.getPort() + "/reservations";
	}

	private Flux<Reservation> call(ServiceInstance si) {
		return this.webClient
			.get()
			.uri(uri(si))
			.retrieve()
			.bodyToFlux(Reservation.class)
			.doOnSubscribe((s) -> log.info("gonna send a request to " + si.getHost() + ':' + si.getPort()))
			.doOnComplete(() -> log.info("finished processing request to " + si.getHost() + ':' + si.getPort()));
	}
}