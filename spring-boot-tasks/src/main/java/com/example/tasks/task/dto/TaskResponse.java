package com.example.tasks.task.dto;

import java.time.Instant;

/** Výstupní DTO - API nevystavuje JPA entitu ani legacy Liferay sloupce */
public record TaskResponse(Long id, String title, boolean done, Instant createDate) {
}
