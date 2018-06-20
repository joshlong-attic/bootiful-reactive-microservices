package com.example.reservationclient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.netflix.hystrix.HystrixCommands;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class ReservationClientApplication {

		@Bean
		RouterFunction<ServerResponse> routes(WebClient client) {
				return route(GET("/reservations/names"), r -> {

						Publisher<String> map = client
							.get()
							.uri("http://reservation-service/reservations")
							.retrieve()
							.bodyToFlux(Reservation.class)
							.map(Reservation::getEmail);

						Publisher<String> fallback =
							HystrixCommands
								.from(map)
								.fallback(Mono.just("EEK!"))
								.commandName("fallback")
								.eager()
								.build();

						return ServerResponse.ok().body(fallback, String.class);
				});
		}

		@Bean
		MapReactiveUserDetailsService authentication() {
				return new MapReactiveUserDetailsService(
					User.withDefaultPasswordEncoder().username("user").password("pw").roles("USER").build()
				);
		}
	
		@Bean
		SecurityWebFilterChain authorization(ServerHttpSecurity security) {
				//@formatter:off
				return security
					.csrf().disable()
					.httpBasic()
					.and()
					.authorizeExchange()
							.pathMatchers("/proxy").authenticated()
							.anyExchange().permitAll()
					.and()
					.build();
				//@formatter:on
		}

		@Bean
		RedisRateLimiter redisRateLimiter() {
				return new RedisRateLimiter(5, 7);
		}

	
	
	
  @Bean
  WebClient client(LoadBalancerExchangeFilterFunction eff) {
    return WebClient.builder().filter(eff).build();
  }
	
	
	

		@Bean
		RouteLocator gateway(RouteLocatorBuilder rlb) {
				return
					rlb
						.routes()
						.route(r -> r.path("/proxy")
							.filters(f -> f
								.setPath("/reservations")
								.requestRateLimiter(c -> c.setRateLimiter(redisRateLimiter()))
							)
							.uri("lb://reservation-service"))
						.build();
		}

		public static void main(String[] args) {
				SpringApplication.run(ReservationClientApplication.class, args);
		}
}

@AllArgsConstructor
@NoArgsConstructor
@Data
class Reservation {
		@Id
		private String id;
		private String email;
}
