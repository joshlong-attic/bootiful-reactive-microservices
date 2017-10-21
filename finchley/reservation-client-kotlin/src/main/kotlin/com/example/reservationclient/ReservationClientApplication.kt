package com.example.reservationclient

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction
import org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocator
import org.springframework.cloud.gateway.handler.predicate.RoutePredicates.path
import org.springframework.cloud.gateway.route.Routes
import org.springframework.cloud.netflix.hystrix.HystrixCommands
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.messaging.Source
import org.springframework.context.annotation.Bean
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux

@EnableBinding(Source::class)
@EnableDiscoveryClient
@SpringBootApplication
class ReservationClientApplication {

    @Bean
    fun client(lb: LoadBalancerExchangeFilterFunction): WebClient = WebClient.builder().filter(lb).build()

    @Bean
    fun routes(client: WebClient, src: Source) = router {

        POST("/reservations") { req ->
            req
                    .bodyToFlux(Reservation::class.java)
                    .map { r -> MessageBuilder.withPayload(r.reservationName).build() }
                    .map { src.output().send(it) }
                    .then(ServerResponse.ok().build())
        }

        GET("/reservations/names") {

            val map: Flux<String> = client
                    .get()
                    .uri("http://reservation-service/reservations")
                    .retrieve()
                    .bodyToFlux(Reservation::class.java)
                    .log()
                    .map { it.reservationName }


            val cb = HystrixCommands
                    .from(map)
                    .commandName("reservation-names")
                    .fallback(Flux.just("EEEK!!"))
                    .eager()
                    .build()

            ServerResponse.ok().body(cb)
        }
    }

    @Bean
    fun gateway() =
            Routes.locator()
                    .route("custom-edge-reservations")
                    .predicate(path("/res"))
                    .uri("lb://reservation-service/reservations")
                    .build()

    @Bean
    fun gatewayRoutes(dc: DiscoveryClient) = DiscoveryClientRouteDefinitionLocator(dc)
}


fun main(args: Array<String>) {
    SpringApplication.run(ReservationClientApplication::class.java, *args)
}

data class Reservation(var id: String? = null, var reservationName: String? = null)