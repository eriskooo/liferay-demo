package com.example.tasks.task;

import com.example.tasks.task.dto.CreateTaskRequest;
import com.example.tasks.task.dto.PageResponse;
import com.example.tasks.task.dto.TaskResponse;
import com.example.tasks.task.dto.UpdateTaskRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API - náhrada za MVC*Command třídy portletu.
 * Stateless: žádná portlet session, žádné SessionErrors; stav nese URL + body,
 * identitu JWT token, chyby HTTP status + ProblemDetail.
 */
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "CRUD nad úkoly (migrace z Liferay Task portletu)")
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	/** Seznam úkolů - portlet: MVCRenderCommand "/" a MVCResourceCommand "/task/json" */
	@GetMapping
	@Operation(summary = "Seznam úkolů (stránkovaný)")
	public PageResponse<TaskResponse> list(
			@RequestParam(required = false) Boolean done,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size) {
		return PageResponse.from(taskService.findAll(done, page, size), TaskMapper::toResponse);
	}

	/** Detail úkolu */
	@GetMapping("/{id}")
	@Operation(summary = "Detail úkolu")
	public TaskResponse get(@PathVariable long id) {
		return TaskMapper.toResponse(taskService.getById(id));
	}

	/** Vytvoření - portlet: MVCActionCommand "/task/add" */
	@PostMapping
	@Operation(summary = "Vytvoří úkol")
	public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
		TaskResponse created = TaskMapper.toResponse(taskService.create(request));
		return ResponseEntity.created(URI.create("/api/tasks/" + created.id())).body(created);
	}

	/** Úprava názvu a stavu */
	@PutMapping("/{id}")
	@Operation(summary = "Upraví úkol")
	public TaskResponse update(@PathVariable long id, @Valid @RequestBody UpdateTaskRequest request) {
		return TaskMapper.toResponse(taskService.update(id, request));
	}

	/** Přepnutí stavu - portlet: MVCActionCommand "/task/toggle" */
	@PatchMapping("/{id}/toggle")
	@Operation(summary = "Přepne done")
	public TaskResponse toggle(@PathVariable long id) {
		return TaskMapper.toResponse(taskService.toggleDone(id));
	}

	/** Smazání úkolu */
	@DeleteMapping("/{id}")
	@Operation(summary = "Smaže úkol")
	public ResponseEntity<Void> delete(@PathVariable long id) {
		taskService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
