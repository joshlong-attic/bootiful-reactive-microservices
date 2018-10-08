package com.example.reservationclient

import org.reactivestreams.Publisher
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.cloud.gateway.route.builder.filters
import org.springframework.cloud.gateway.route.builder.routes
import org.springframework.cloud.netflix.hystrix.HystrixCommands
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.messaging.Source
import org.springframework.context.annotation.Bean
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.bodyToMono
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux

@EnableBinding(Source::class)
@SpringBootApplication
class ReservationClientApplication() {

	@Bean
	fun client(eff: LoadBalancerExchangeFilterFunction) = WebClient.builder().filter(eff).build()

	@Bean
	fun routes(client: WebClient, source: Source) = router {
		POST("/reservations") { request ->
			val send: Publisher<Boolean> = request.bodyToMono<Reservation>()
					.map { it.reservationName }
					.map { MessageBuilder.withPayload(it).build() }
					.map { source.output().send(it) }
			ServerResponse.ok().body(send)
		}
		GET("/reservations/names") {
			val names: Publisher<String> = client
					.get()
					.uri("http://reservation-service/reservations")
					.retrieve()
					.bodyToFlux<Reservation>()
					.map { it.reservationName }
			val circuit: Publisher<String> = HystrixCommands
					.from(names)
					.commandName("reservation-names")
					.eager()
					.fallback(Flux.just("EEK!"))
					.build()
			ServerResponse.ok().body(circuit)
		}
	}

	@Bean
	fun authentication() =
			MapReactiveUserDetailsService(User.withDefaultPasswordEncoder()
					.username("user")
					.password("password")
					.roles("USER")
					.build())

	@Bean
	fun authorization(httpSecurity: ServerHttpSecurity) =
			httpSecurity
					.authorizeExchange().pathMatchers("/rl").authenticated()
					.anyExchange().permitAll()
					.and()
					.httpBasic()
					.and()
					.csrf().disable()
					.build()

	@Bean
	fun rateLimiter() = RedisRateLimiter(4, 6)

	@Bean
	fun gateway(rlb: RouteLocatorBuilder, rl: RedisRateLimiter) =
			rlb.routes {
				route {
					path("/rl")
					filters {
						requestRateLimiter({ it.rateLimiter = rl })
						setPath("/reservations")
					}
					uri("lb://reservation-service")
				}
				route {
					path("/lb")
					filters {
						setPath("/reservations")
					}
					uri("lb://reservation-service")
				}
			}
}

fun main(args: Array<String>) {
	runApplication<ReservationClientApplication>(*args)
}


class Reservation(
		val id: String? = null,
		val reservationName: String? = null)