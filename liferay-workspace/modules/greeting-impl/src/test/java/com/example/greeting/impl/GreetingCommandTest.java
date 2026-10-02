package com.example.greeting.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.greeting.api.GreetingService;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GreetingCommandTest {

	@Test
	@DisplayName("Výchozí implementace pozdraví jménem")
	void should_greetByName_whenDefaultService() {
		assertEquals(
			"Hello, Erich!", new DefaultGreetingService().greet("Erich"));
	}

	@Test
	@DisplayName("hello použije aktuálně navázanou službu")
	void should_useBoundService_whenHelloCalled() throws Exception {
		GreetingCommand command = new GreetingCommand();

		// Field injection dělá v OSGi SCR - v testu ji nahradíme reflexí
		Field field = GreetingCommand.class.getDeclaredField(
			"_greetingService");

		field.setAccessible(true);
		field.set(command, (GreetingService)name -> "Hi " + name);

		assertEquals("Hi Erich", command.hello("Erich"));
	}

	@Test
	@DisplayName("all vypíše všechny navázané služby a po unbind je odebere")
	void should_listAllServices_whenMultipleBound() {
		GreetingCommand command = new GreetingCommand();
		GreetingService defaultService = new DefaultGreetingService();

		command.addGreetingService(defaultService);

		assertEquals(
			"DefaultGreetingService: Hello, Erich!", command.all("Erich"));

		command.removeGreetingService(defaultService);

		assertTrue(command.all("Erich").isEmpty());
	}

}