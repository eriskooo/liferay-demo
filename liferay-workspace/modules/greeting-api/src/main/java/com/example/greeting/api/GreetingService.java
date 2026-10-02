package com.example.greeting.api;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Veřejné API sdílené mezi bundly.
 *
 * Balíček je exportován (bnd.bnd -> Export-Package) s verzí z packageinfo
 * (Export-Package: com.example.greeting.api;version="1.0.0").
 * Čisté bnd by konzumentům dalo Import-Package [1.0,2) a implementacím
 * ProviderType rozhraní [1.0,1.1). Liferay workspace ale horní mez záměrně
 * odstraňuje -> v MANIFEST.MF je jen version="1.0" (tj. >= 1.0).
 */
@ProviderType
public interface GreetingService {

	/** Vrátí pozdrav pro zadané jméno */
	String greet(String name);

}