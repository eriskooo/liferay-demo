package com.example.tasks.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Vstup pro PUT /api/tasks/{id} - kompletní náhrada editovatelných polí */
public record UpdateTaskRequest(@NotBlank @Size(max = 255) String title, @NotNull Boolean done) {
}
