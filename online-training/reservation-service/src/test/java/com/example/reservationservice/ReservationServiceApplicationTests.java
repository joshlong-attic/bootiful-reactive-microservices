package com.example.reservationservice;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.function.Predicate;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ReservationServiceApplicationTests {

	@Autowired
	private ReservationRepository reservationRepository;

	@Test
	public void contextLoads() {


		Flux<Reservation> twoBOrNotTwoB = this.reservationRepository
			.deleteAll()
			.thenMany(Flux.just("A", "B", "B")
				.map(name -> new Reservation(null, name))
				.flatMap(this.reservationRepository::save))
			.thenMany(this.reservationRepository.findByName("B"))
			.log();

		StepVerifier
			.create(twoBOrNotTwoB)
			.expectNextCount(2)
			.verifyComplete();


	}

}

