package com.example.tasks.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskPropertiesTest {

	private final TaskProperties properties = new TaskProperties(10, 100, new TaskProperties.Security(false));

	@Test
	@DisplayName("Vrátí výchozí velikost stránky, když není zadaná")
	void should_returnDefault_whenSizeIsNull() {
		assertThat(properties.resolvePageSize(null)).isEqualTo(10);
	}

	@Test
	@DisplayName("Vrátí zadanou velikost v povoleném rozsahu")
	void should_returnRequested_whenWithinRange() {
		assertThat(properties.resolvePageSize(25)).isEqualTo(25);
	}

	@Test
	@DisplayName("Ořízne velikost na maximum")
	void should_clampToMax_whenSizeTooLarge() {
		assertThat(properties.resolvePageSize(1000)).isEqualTo(100);
	}

	@Test
	@DisplayName("Vrátí 1 pro nulovou nebo zápornou velikost")
	void should_returnOne_whenSizeIsZeroOrNegative() {
		assertThat(properties.resolvePageSize(0)).isEqualTo(1);
		assertThat(properties.resolvePageSize(-5)).isEqualTo(1);
	}

}
