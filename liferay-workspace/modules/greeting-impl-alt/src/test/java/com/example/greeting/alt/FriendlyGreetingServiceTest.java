package com.example.greeting.alt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FriendlyGreetingServiceTest {

	@Test
	@DisplayName("Přátelský pozdrav obsahuje jméno")
	void should_greetFriendly_whenNameGiven() {
		assertEquals(
			"Ahoj Erich, vitaj v OSGi!",
			new FriendlyGreetingService().greet("Erich"));
	}

}