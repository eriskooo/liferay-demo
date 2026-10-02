package com.example.tasks.task.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Stabilní JSON tvar stránky (Spring Page se nemá serializovat přímo) */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	/** Převede Spring Data Page na DTO s namapovaným obsahem */
	public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(
				page.getContent().stream().map(mapper).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}

}
