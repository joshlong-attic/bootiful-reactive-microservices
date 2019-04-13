package com.example.rsocketclient;

import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.client.TcpClientTransport;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@SpringBootApplication
public class RsocketClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(RsocketClientApplication.class, args);
	}

}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingsRequest {
	private String name;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingsResponse {
	private String greeting;
}


@RestController
class GreetingsClientRestController {

	private final RSocketRequester requester;

	GreetingsClientRestController(RSocketRequester requester) {
		this.requester = requester;
	}

	@GetMapping(
		produces = MediaType.TEXT_EVENT_STREAM_VALUE,
		value = "/greetings/{name}")
	Flux<GreetingsResponse> greetStream(@PathVariable String name) {
		return this.requester
			.route("greetings")
			.data(new GreetingsRequest(name))
			.retrieveFlux(GreetingsResponse.class);
	}
}


@Configuration
class RSocketConfig {


	@Bean
	RSocketRequester requester(RSocketStrategies rSocketStrategies) {
		return RSocketRequester
			.create(this.rSocket(), MimeTypeUtils.APPLICATION_JSON, rSocketStrategies);
	}

	@Bean
	RSocket rSocket() {
		return RSocketFactory
			.connect()
			.frameDecoder(PayloadDecoder.ZERO_COPY)
			.dataMimeType(MimeTypeUtils.APPLICATION_JSON_VALUE)
			.transport(TcpClientTransport.create(7000))
			.start()
			.block();
	}
}