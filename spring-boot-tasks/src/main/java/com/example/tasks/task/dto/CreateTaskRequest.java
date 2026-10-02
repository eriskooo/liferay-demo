package com.example.tasks.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Vstup pro POST /api/tasks - validace nahrazuje ruční kontrolu + TaskTitleException */
public record CreateTaskRequest(@NotBlank @Size(max = 255) String title) {
}
