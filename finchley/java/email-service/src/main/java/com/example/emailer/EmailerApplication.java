package com.example.emailer;

import com.sendgrid.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.function.Function;

@SpringBootApplication
public class EmailerApplication {

		private final Log log = LogFactory.getLog(getClass());

		public EmailerApplication(@Value("${message}") String msg) {
				log.info("config server message: " + msg);
		}

		@Bean
		WebClient client(LoadBalancerExchangeFilterFunction eff) {
				return WebClient.builder().filter(eff).build();
		}

		@Bean
		Function<EmailRequest, Boolean> email(WebClient client, SendGrid sg) {
				return input -> {
						return
							client
								.get()
								.uri("http://reservation-service/reservations")
								.retrieve()
								.bodyToFlux(Reservation.class)
								.filter(x -> x.getId().equals(input.getReservationId()))
								.flatMap(r -> {
										try {

												Email from = new Email("spring-tips@joshlong.com");
												String subject = "Bootiful Riff";
												Email to = new Email(r.getEmail());
												Content content = new Content("text/plain", "Hello world!");
												Mail mail = new Mail(from, subject, to, content);
												Request request1 = new Request();
												request1.setMethod(Method.POST);
												request1.setEndpoint("mail/send");
												request1.setBody(mail.build());
												Response response = sg.api(request1);
												return Flux.just(true); // (response.getStatusCode() >= 200 && response.getStatusCode() < 300));
										}
										catch (Exception e) {
												throw new RuntimeException(e);
										}
								})
								.blockFirst();
				};
		}


		public static void main(String[] args) {
				SpringApplication.run(EmailerApplication.class, args);
		}
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class EmailRequest {
		private String reservationId;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {
		private String id, email;
}