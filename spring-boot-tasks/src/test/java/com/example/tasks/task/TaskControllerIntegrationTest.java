package com.example.tasks.task;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tasks.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end přes HTTP vrstvu + skutečná PostgreSQL (Testcontainers) + Flyway */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TaskControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("Vytvoří úkol, vrátí 201 s Location a lze ho načíst")
	void should_createAndFetchTask_whenRequestValid() throws Exception {
		long id = createTask("Integration task");

		mockMvc.perform(get("/api/tasks/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Integration task"))
				.andExpect(jsonPath("$.done").value(false));
	}

	@Test
	@DisplayName("Prázdný název vrátí 400 ProblemDetail")
	void should_return400_whenTitleBlank() throws Exception {
		mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	@DisplayName("Chybějící body vrátí 400")
	void should_return400_whenBodyMissing() throws Exception {
		mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Neexistující úkol vrátí 404 ProblemDetail")
	void should_return404_whenTaskMissing() throws Exception {
		mockMvc.perform(get("/api/tasks/{id}", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Task not found"));
	}

	@Test
	@DisplayName("Toggle přepne done na true")
	void should_toggleDone_whenTaskExists() throws Exception {
		long id = createTask("To toggle");

		mockMvc.perform(patch("/api/tasks/{id}/toggle", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.done").value(true));
	}

	@Test
	@DisplayName("PUT aktualizuje název i stav")
	void should_updateTask_whenRequestValid() throws Exception {
		long id = createTask("Before");

		mockMvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"After\",\"done\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("After"))
				.andExpect(jsonPath("$.done").value(true));
	}

	@Test
	@DisplayName("DELETE vrátí 204 a úkol pak neexistuje")
	void should_deleteTask_whenExists() throws Exception {
		long id = createTask("To delete");

		mockMvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Seznam respektuje velikost stránky a filtr done")
	void should_listWithPagingAndFilter_whenParamsGiven() throws Exception {
		createTask("Paging 1");
		createTask("Paging 2");

		mockMvc.perform(get("/api/tasks").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)));

		mockMvc.perform(get("/api/tasks").param("done", "true").param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].done").value(everyItem(is(true))));
	}

	@Test
	@DisplayName("OpenAPI dokument je dostupný")
	void should_exposeOpenApi_whenRequested() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/tasks']").exists());
	}

	private long createTask(String title) throws Exception {
		String body = mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"" + title + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

}
