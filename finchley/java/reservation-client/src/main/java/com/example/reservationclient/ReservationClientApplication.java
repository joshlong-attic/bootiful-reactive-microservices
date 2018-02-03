package com.example.reservationclient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.netflix.hystrix.HystrixCommands;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@EnableBinding(Source.class)
@SpringBootApplication
public class ReservationClientApplication {

	@Bean
	WebClient client(LoadBalancerExchangeFilterFunction function) {
		return WebClient.builder().filter(function).build();
	}

	@Bean
	MapReactiveUserDetailsService authentication() {
		return new MapReactiveUserDetailsService(User.withDefaultPasswordEncoder()
				.username("user")
				.password("password")
				.roles("USER")
				.build());
	}

	@Bean
	SecurityWebFilterChain authorization(ServerHttpSecurity httpSecurity) {
		return httpSecurity
				.authorizeExchange().pathMatchers("/rl").authenticated()
				.anyExchange().permitAll()
				.and()
				.httpBasic()
				.and()
				.csrf().disable()
				.build();
	}

	@Bean
	RouterFunction<?> routerFunction(WebClient client, Source src) {
		//@formatter:off
		return
				route(GET("/reservations/names"), request -> {
					Flux<String> names = client
							.get()
							.uri("http://reservation-service/reservations")
							.retrieve()
							.bodyToFlux(Reservation.class)
							.map(Reservation::getReservationName);

					Publisher<String> circuit =
							HystrixCommands
									.from(names)
									.fallback(Flux.just("EEK!"))
									.commandName("reservation-names")
									.eager()
									.build();

					return ServerResponse.ok().body(circuit, String.class);
				})
				.andRoute(POST("/reservations"), request -> {
					Flux<Boolean> ok = request
							.bodyToFlux(Reservation.class)
							.map(x -> MessageBuilder.withPayload(x.getReservationName()).build())
							.map(msg -> src.output().send(msg));
					return ServerResponse.ok().body(ok, Boolean.class);
				});
		//@formatter:on
	}

	@Bean
	RouteLocator routeLocator(RouteLocatorBuilder rlb,
	                          RequestRateLimiterGatewayFilterFactory rlf) {
		GatewayFilter gatewayFilter = rlf.apply(RedisRateLimiter.args(2, 4));
		return rlb
				.routes()
				.route(spec ->
						spec.path("/rl")
								.filter(gatewayFilter)
								.setPath("/reservations")
								.uri("lb://reservation-service"))
				.route(spec ->
						spec
								.path("/lb")
								.setPath("/reservations")
								.uri("lb://reservation-service"))
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
	private String id;
	private String reservationName;
}