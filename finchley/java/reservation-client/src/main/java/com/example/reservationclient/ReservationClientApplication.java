package com.example.reservationclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableBinding(Source.class)
public class ReservationClientApplication {

	@Bean
	RouteLocator gateway(RouteLocatorBuilder rlb) {
		return rlb
				.routes()
				.route(spec -> spec
						.path("/reservations")
						.filters(fs -> fs.setPath("/reservations"))
						.uri("lb://reservation-service/"))
				.build();
	}

	public static void main(String[] args) {
		SpringApplication.run(ReservationClientApplication.class, args);
	}
}
