package com.example.reservationservice;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
@RunWith(SpringRunner.class)
@WebFluxTest
public class ReservationRestTest {

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private ReservationRepository reservationRepository;

	@Test
	public void getAllReservation() throws Exception {

		Mockito.when(this.reservationRepository.findAll())
			.thenReturn(Flux.just(new Reservation("1", "Bob")));

		this.webTestClient
			.get()
			.uri("/reservations")
			.exchange()
			.expectStatus().isOk()
			.expectBody().jsonPath("@.[0].name").isEqualTo("Bob");

	}

}
