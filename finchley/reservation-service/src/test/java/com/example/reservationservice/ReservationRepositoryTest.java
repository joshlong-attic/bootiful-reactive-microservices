package com.example.reservationservice;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
@DataMongoTest
@RunWith(SpringRunner.class)
public class ReservationRepositoryTest {

	@Autowired
	private ReservationRepository reservationRepository;

	@Test
	public void persist() throws Exception {

		Flux<Reservation> map = Flux.just("A", "B", "B")
			.map(x -> new Reservation(null, x))
			.flatMap(r -> this.reservationRepository.save(r));

		Flux<Reservation> reservationFlux =
			this.reservationRepository
			.deleteAll()
			.thenMany(map)
			.thenMany(this.reservationRepository.findByName("B"));

		StepVerifier
			.create(reservationFlux)
			.expectNextCount(2)
			.verifyComplete();


	}
}
