package com.example.tasks.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Konfigurace aplikace - náhrada za PortletPreferences portletu.
 * PortletPreferences byly per instance portletu na stránce a měnil je admin v UI;
 * tady jde o globální konfiguraci (application.yml / env proměnné).
 */
@Validated
@ConfigurationProperties("tasks")
public record TaskProperties(
		@DefaultValue("10") @Min(1) int defaultPageSize,
		@DefaultValue("100") @Min(1) int maxPageSize,
		@DefaultValue Security security) {

	/** Přepínač mezi otevřeným demem a OAuth2 resource serverem */
	public record Security(@DefaultValue("false") boolean enabled) {
	}

	/** Vrátí velikost stránky omezenou na rozsah 1..maxPageSize, null = výchozí */
	public int resolvePageSize(Integer requested) {
		if (requested == null) {
			return defaultPageSize;
		}
		return Math.clamp(requested, 1, maxPageSize);
	}

}
