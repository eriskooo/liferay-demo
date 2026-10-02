package com.example.greeting.impl;

import com.example.greeting.api.GreetingService;

import org.osgi.service.component.annotations.Component;

/**
 * Výchozí implementace registrovaná přes Declarative Services.
 *
 * service.ranking=100 - pokud se objeví služba s vyšším rankingem
 * (greeting-impl-alt), konzumenti s GREEDY referencí přepnou na ni.
 */
@Component(property = "service.ranking:Integer=100", service = GreetingService.class)
public class DefaultGreetingService implements GreetingService {

	@Override
	public String greet(String name) {
		return "Hello, " + name + "!";
	}

}