package com.example.emailer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

import java.util.function.Function;

@SpringBootApplication
public class EmailerApplication {
/*
		@Bean
		WebClient client(LoadBalancerExchangeFilterFunction eff) {
				return WebClient.builder().filter(eff).build();
		}*/

		@Bean
		@LoadBalanced
		RestTemplate restTemplate() {
				return new RestTemplate();
		}

		@Bean
		Function<Flux<EmailRequest>, Flux<EmailResponse>> email(
			DiscoveryClient client,
			RestTemplate restTemplate) {

				return in -> {

						client
							.getInstances("reservation-service")
							.forEach(r -> System.out.println(r.toString()));

						return Flux.just(new EmailResponse("", true));
				};
		}

		public static void main(String[] args) {
				SpringApplication.run(EmailerApplication.class, args);
		}
}


@Data
@AllArgsConstructor
@NoArgsConstructor
class EmailResponse {
		private String reservationId;
		private boolean sent = true;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class EmailRequest {
		private String reservationId;
}