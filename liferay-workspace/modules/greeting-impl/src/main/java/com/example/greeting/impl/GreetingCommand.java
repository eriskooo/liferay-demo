package com.example.greeting.impl;

import com.example.greeting.api.GreetingService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

/**
 * Konzument GreetingService vystavený jako Gogo příkaz.
 *
 * V Gogo shellu:  greeting:hello Erich   |   greeting:all Erich
 */
@Component(
	property = {
		"osgi.command.function=all", "osgi.command.function=hello",
		"osgi.command.scope=greeting"
	},
	service = Object.class
)
public class GreetingCommand {

	/** Zavolá službu s nejvyšším service.ranking */
	public String hello(String name) {
		return _greetingService.greet(name);
	}

	/** Zavolá všechny aktuálně registrované implementace */
	public String all(String name) {
		return _greetingServices.stream(
		).map(
			service -> service.getClass().getSimpleName() + ": " + service.greet(name)
		).collect(
			Collectors.joining(System.lineSeparator())
		);
	}

	@Reference(
		cardinality = ReferenceCardinality.MULTIPLE,
		policy = ReferencePolicy.DYNAMIC,
		policyOption = ReferencePolicyOption.GREEDY
	)
	protected void addGreetingService(GreetingService greetingService) {
		_greetingServices.add(greetingService);
	}

	protected void removeGreetingService(GreetingService greetingService) {
		_greetingServices.remove(greetingService);
	}

	// DYNAMIC + GREEDY: při registraci služby s vyšším rankingem se reference
	// přepojí za běhu bez restartu komponenty (STATIC by komponentu restartoval,
	// RELUCTANT by novou službu ignoroval). volatile kvůli výměně z jiného vlákna.
	@Reference(
		policy = ReferencePolicy.DYNAMIC,
		policyOption = ReferencePolicyOption.GREEDY
	)
	private volatile GreetingService _greetingService;

	private final List<GreetingService> _greetingServices =
		new CopyOnWriteArrayList<>();

}