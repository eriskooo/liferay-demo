package com.example.tasks.task;

import com.example.tasks.task.dto.TaskResponse;

/** Mapování entita -> DTO (záměrně ručně, bez MapStruct, ať je demo bez magie) */
public final class TaskMapper {

	private TaskMapper() {
	}

	/** Převede entitu na odpověď API */
	public static TaskResponse toResponse(Task task) {
		return new TaskResponse(task.getId(), task.getTitle(), task.isDone(), task.getCreateDate());
	}

}
