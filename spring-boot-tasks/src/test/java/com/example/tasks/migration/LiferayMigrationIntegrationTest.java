package com.example.tasks.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tasks.TestcontainersConfiguration;
import com.example.tasks.task.Task;
import com.example.tasks.task.TaskRepository;
import com.example.tasks.task.TaskService;
import com.example.tasks.task.dto.CreateTaskRequest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Ověří Flyway V2: data z Liferay tabulky public.demo_task (init skript Testcontainers)
 * se přenesou do tasks.task se zachováním ID a sekvence pokračuje za nimi.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LiferayMigrationIntegrationTest {

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private TaskService taskService;

	@Test
	@DisplayName("Importuje Liferay úkol se zachováním ID, názvu a legacy referencí")
	void should_importLegacyTask_whenLiferayTableExists() {
		Task imported = taskRepository.findById(101L).orElseThrow();

		assertThat(imported.getTitle()).isEqualTo("Legacy task from Liferay");
		assertThat(imported.isDone()).isFalse();
		assertThat(imported.getGroupId()).isEqualTo(20117L);
		assertThat(imported.getUserId()).isEqualTo(20123L);
		// Liferay timestamp je v UTC -> musí se načíst beze změny hodiny
		assertThat(imported.getCreateDate()).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"));
	}

	@Test
	@DisplayName("Prázdný název a chybějící datum nahradí výchozími hodnotami")
	void should_applyDefaults_whenLegacyValuesMissing() {
		Task imported = taskRepository.findById(102L).orElseThrow();

		assertThat(imported.getTitle()).isEqualTo("(untitled)");
		assertThat(imported.isDone()).isTrue();
		assertThat(imported.getCreateDate()).isNotNull();
	}

	@Test
	@DisplayName("Nový úkol dostane ID vyšší než importovaná data")
	void should_continueSequenceAfterImportedIds_whenCreatingTask() {
		Task created = taskService.create(new CreateTaskRequest("After migration"));

		assertThat(created.getId()).isGreaterThan(102L);
	}

}
