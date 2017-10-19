package com.example.reservationclient;

import lombok.Data;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.Routes;
import org.springframework.cloud.netflix.hystrix.HystrixCommands;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import static org.springframework.cloud.gateway.handler.predicate.RoutePredicates.path;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
@EnableDiscoveryClient
public class ReservationClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationClientApplication.class, args);
    }

    @Bean
    DiscoveryClientRouteDefinitionLocator gatewayRoutes(DiscoveryClient client) {
        return new DiscoveryClientRouteDefinitionLocator(client);
    }

    @Bean
    RouteLocator gateway() {
        return Routes
                .locator()
                .route("rate_limited_route")
                .predicate(path("/edge-reservations"))
                .uri("lb://reservation-service/reservations")
                .build();
    }

    @Bean
    WebClient client(LoadBalancerExchangeFilterFunction lb) {
        return WebClient.builder().filter(lb).build();
    }


    @Bean
    RouterFunction<?> routes(WebClient client) {
        return route(GET("/reservations/names"),
                r -> {
                    Flux<String> res = client
                            .get()
                            .uri("http://reservation-service/reservations")
                            .retrieve()
                            .bodyToFlux(Reservation.class)
                            .map(Reservation::getReservationName);
                    Flux<String> publisher = HystrixCommands
                            .from(res)
                            .commandName("reservation-names")
                            .fallback(Flux.just("EEK!"))
                            .eager()
                            .toFlux();
                    return ServerResponse.ok().body(publisher, String.class);
                });
    }
}

@Data
class Reservation {
    private String reservationName, id;
}