package com.example.tasks.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tasks.task.dto.TaskResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskMapperTest {

	@Test
	@DisplayName("Přenese všechna veřejná pole entity do DTO")
	void should_mapAllFields_whenTaskGiven() {
		Task task = new Task("Write README");
		task.setDone(true);

		TaskResponse response = TaskMapper.toResponse(task);

		assertThat(response.title()).isEqualTo("Write README");
		assertThat(response.done()).isTrue();
		assertThat(response.createDate()).isEqualTo(task.getCreateDate());
		assertThat(response.id()).isNull(); // ještě neuloženo
	}

}
