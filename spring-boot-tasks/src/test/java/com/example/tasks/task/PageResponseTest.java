package com.example.tasks.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tasks.task.dto.PageResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

	@Test
	@DisplayName("Namapuje obsah a metadata stránky")
	void should_mapContentAndMetadata_whenPageGiven() {
		PageImpl<Integer> page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 5);

		PageResponse<String> response = PageResponse.from(page, String::valueOf);

		assertThat(response.content()).containsExactly("1", "2");
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(5);
		assertThat(response.totalPages()).isEqualTo(3);
	}

	@Test
	@DisplayName("Prázdná stránka dá prázdný obsah")
	void should_returnEmptyContent_whenPageEmpty() {
		PageResponse<String> response = PageResponse.from(new PageImpl<Integer>(List.of()), String::valueOf);

		assertThat(response.content()).isEmpty();
		assertThat(response.totalElements()).isZero();
	}

}
