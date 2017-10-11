package com.example.reservationclient;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixObservableCommand;
import lombok.Data;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import reactor.core.publisher.Flux;
import rx.Observable;
import rx.RxReactiveStreams;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

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
    WebClient client(LoadBalancerExchangeFilterFunction lb) {
        return WebClient.builder().filter(lb).build();
    }

    private static Publisher<String> circuitBreak(Publisher<String> results) {

        HystrixObservableCommand<String> command = new HystrixObservableCommand<String>(
                HystrixCommandGroupKey.Factory.asKey("fetch-names")) {

            @Override
            protected Observable<String> construct() {
                return RxReactiveStreams.toObservable(results);
            }

            @Override
            protected Observable<String> resumeWithFallback() {
                return RxReactiveStreams.toObservable(Flux.just("EEK"));
            }
        };

        return RxReactiveStreams.toPublisher(command.observe());
    }

    private static Flux<String> fetch(WebClient client) {
        return client
                .get()
                .uri("http://reservation-service/reservations")
                .retrieve()
                .bodyToFlux(Reservation.class)
                .map(Reservation::getReservationName);
    }

    @Bean
    RouterFunction<?> routes(WebClient client) {
        return route(GET("/reservations/names"), r -> ok().body(circuitBreak(fetch(client)), String.class));
    }
}

@Data
class Reservation {
    private String reservationName, id;
}