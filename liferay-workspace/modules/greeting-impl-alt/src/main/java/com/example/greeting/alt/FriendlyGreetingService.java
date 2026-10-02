package com.example.greeting.alt;

import com.example.greeting.api.GreetingService;

import org.osgi.service.component.annotations.Component;

/**
 * Alternativní implementace s vyšším rankingem.
 *
 * Demo: "stop <id>" tohoto bundlu v Gogo shellu -> GreetingCommand
 * se za běhu přepne zpět na DefaultGreetingService, "start <id>" -> zpět sem.
 */
@Component(property = "service.ranking:Integer=200", service = GreetingService.class)
public class FriendlyGreetingService implements GreetingService {

	@Override
	public String greet(String name) {
		return "Ahoj " + name + ", vitaj v OSGi!";
	}

}