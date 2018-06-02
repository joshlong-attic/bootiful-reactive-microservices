package com.example.emailer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import java.util.function.Function;

@SpringBootApplication
public class EmailerApplication {

		private final Log log = LogFactory.getLog(getClass());

		private final RestTemplate restTemplate = new RestTemplate();

		@Bean
		Function<String, String> email(Environment env) {
				return input -> {
/*

						String json = this.restTemplate.getForObject("http://localhost:8888/email-service/default", String.class);
						this.log.info("json: " + json);
*/

						String msgFromConfigService = env.getProperty("message");
						this.log.info("message: " + msgFromConfigService);
						return input;
				};
		}

		public static void main(String[] args) {
				SpringApplication.run(EmailerApplication.class, args);
		}
}