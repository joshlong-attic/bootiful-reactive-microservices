package com.example.reservationclient

import org.reactivestreams.Publisher
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction
import org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocator
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.cloud.gateway.route.builder.filters
import org.springframework.cloud.gateway.route.builder.routes
import org.springframework.cloud.netflix.hystrix.HystrixCommands
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.messaging.Source
import org.springframework.context.support.beans
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

@SpringBootApplication
@EnableBinding(Source::class)
class ReservationClientApplication

fun main(args: Array<String>) {
	SpringApplicationBuilder()
			.sources(ReservationClientApplication::class.java)
			.initializers(beans {
				bean {
					DiscoveryClientRouteDefinitionLocator(ref())
				}
				bean {
					MapReactiveUserDetailsService(
							User.withDefaultPasswordEncoder()
									.username("user")
									.roles("USER")
									.password("pw")
									.build())
				}
				bean {
					//@formatter:off
					val http = ref <ServerHttpSecurity>()
					http
							.csrf().disable()
							.httpBasic()
							.and()
							.authorizeExchange()
								.pathMatchers("/proxy").authenticated()
								.anyExchange().permitAll()
							.and()
							.build()
					//@formatter:on
				}
				bean {
					val builder = ref<RouteLocatorBuilder>()
					builder
							.routes {
								route {
									val rl = ref<RequestRateLimiterGatewayFilterFactory>()
											.apply(RedisRateLimiter.args(2, 4))
									path("/proxy")
									filters {
										filter(rl)
									}
									uri("lb://reservation-service/reservations")
								}
							}
				}
				bean {
					WebClient.builder().filter(ref<LoadBalancerExchangeFilterFunction>()).build()
				}
				bean {
					router {
						val writesChannel = ref<Source>()
						val client = ref<WebClient>()

						GET("/reservations/names") {
							val names: Publisher<String> = client
									.get()
									.uri("http://reservation-service/reservations")
									.retrieve()
									.bodyToFlux<Reservation>()
									.map { it.reservationName }

							val cb = HystrixCommands
									.from(names)
									.commandName("reservation-names")
									.fallback(Flux.just("EEEK!!"))
									.eager()
									.build()

							ServerResponse.ok().body(cb)
						}
						POST("/reservations") { req ->
							req.bodyToMono<Reservation>()
									.map { it.reservationName }
									.map { MessageBuilder.withPayload(it!!).build() }
									.map { writesChannel.output().send(it) }
									.flatMap { ServerResponse.ok().build() }
						}
					}
				}
			})
			.run(*args)
}

data class Reservation(var id: String? = null, var reservationName: String? = null)