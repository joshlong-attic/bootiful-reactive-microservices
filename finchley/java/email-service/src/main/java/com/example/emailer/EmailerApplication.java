package com.example.emailer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.function.Function;

@SpringBootApplication
public class EmailerApplication {

		private final Log log = LogFactory.getLog(getClass());

		@Bean
		WebClient client(LoadBalancerExchangeFilterFunction eff) {
				return WebClient.builder().filter(eff).build();
		}

		@Bean
		Function<String, String> email(WebClient client) {
				return input -> {


						// todo send an email w/ sendgrid

						client
							.get()
							.uri("http://reservation-service/reservations")
							.retrieve()
							.bodyToFlux(Reservation.class)
							.subscribe(System.out::println);

						return input;
				};
		}

		public static void main(String[] args) {
				SpringApplication.run(EmailerApplication.class, args);
		}
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {
		private String id, email;
}