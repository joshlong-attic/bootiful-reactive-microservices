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
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux


@EnableBinding(Source::class)
@SpringBootApplication
class ReservationClientApplication

fun main(args: Array<String>) {
	SpringApplicationBuilder()
			.sources(ReservationClientApplication::class.java)
			.initializers(beans {
				bean {
					val filter = ref<LoadBalancerExchangeFilterFunction>()
					WebClient.builder().filter(filter).build()
				}
				bean {
					val channel = ref<Source>().output()
					val client = ref<WebClient>()
					router {

						POST("/reservations") { request ->
							val sent: Publisher<Boolean> = request
									.bodyToFlux(Reservation::class.java)
									.map { it.reservationName }
									.map {
										MessageBuilder.withPayload(it).build()
									}
									.map {
										channel.send(it)
									}
							ServerResponse.ok().body(sent)
						}

						GET("/reservations/names") {

							val response: Publisher<String> = client.get()
									.uri("http://reservation-service/reservations")
									.retrieve()
									.bodyToFlux<Reservation>()
									.map { it.reservationName }

							val cb = HystrixCommands.from(response)
									.commandName("resevations-commands")
									.fallback(Flux.just("OH NOES!!!"))
									.eager()
									.build()

							ServerResponse.ok().body(cb)
						}
					}
				}
				bean {
					MapReactiveUserDetailsService(
							User.withDefaultPasswordEncoder()
									.username("user")
									.password("password")
									.roles("USER")
									.build()
					)
				}
				bean {
					val http = ref<ServerHttpSecurity>()
					http.csrf().disable()
							.httpBasic()
							.and()
							.authorizeExchange()
							.pathMatchers("/rl").authenticated()
							.anyExchange().permitAll()
							.and()
							.build()
				}
				bean {
					val rlb = ref<RouteLocatorBuilder>()
					rlb.routes {
						route {
							path("/rl")
							filters {
								filter(ref<RequestRateLimiterGatewayFilterFactory>()
										.apply(RedisRateLimiter.args(3, 6)))
							}
							uri("lb://reservation-service/reservations")
						}
						route {
							path("/lb")
							uri("lb://reservation-service/reservations")
						}
					}
				}
				bean {
					DiscoveryClientRouteDefinitionLocator(ref())
				}
			})
			.run(*args)
}

data class Reservation(val id: String? = null, val reservationName: String? = null)
