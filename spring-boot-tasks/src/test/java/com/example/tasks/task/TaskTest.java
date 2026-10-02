package com.example.tasks.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskTest {

	@Test
	@DisplayName("Nový úkol není hotový a má datum vytvoření")
	void should_beNotDoneWithCreateDate_whenCreated() {
		Task task = new Task("Learn OSGi");

		assertThat(task.isDone()).isFalse();
		assertThat(task.getCreateDate()).isNotNull();
	}

	@Test
	@DisplayName("Dvojí přepnutí vrátí původní stav")
	void should_toggleBackAndForth_whenToggledTwice() {
		Task task = new Task("Learn OSGi");

		task.toggleDone();
		assertThat(task.isDone()).isTrue();

		task.toggleDone();
		assertThat(task.isDone()).isFalse();
	}

}
