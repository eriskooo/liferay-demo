package com.example.tasks.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tasks.config.TaskProperties;
import com.example.tasks.task.dto.CreateTaskRequest;
import com.example.tasks.task.dto.UpdateTaskRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

	@Mock
	private TaskRepository taskRepository;

	private TaskService taskService;

	@BeforeEach
	void setUp() {
		taskService = new TaskService(taskRepository, new TaskProperties(10, 100, new TaskProperties.Security(false)));
	}

	@Test
	@DisplayName("Bez filtru načte všechny úkoly s výchozí velikostí stránky")
	void should_findAllWithDefaultSize_whenNoFilter() {
		when(taskRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

		taskService.findAll(null, 0, null);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(taskRepository).findAll(pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
	}

	@Test
	@DisplayName("S filtrem done použije finder findByDone")
	void should_useFinder_whenDoneFilterGiven() {
		when(taskRepository.findByDone(eq(true), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(new Task("a"))));

		assertThat(taskService.findAll(true, 0, 5).getContent()).hasSize(1);
	}

	@Test
	@DisplayName("Záporné číslo stránky převede na první stránku")
	void should_useFirstPage_whenPageNegative() {
		when(taskRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

		taskService.findAll(null, -3, null);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(taskRepository).findAll(pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isZero();
	}

	@Test
	@DisplayName("Vrátí úkol, když existuje")
	void should_returnTask_whenExists() {
		Task task = new Task("a");
		when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

		assertThat(taskService.getById(1L)).isSameAs(task);
	}

	@Test
	@DisplayName("Vyhodí TaskNotFoundException, když úkol neexistuje")
	void should_throwNotFound_whenTaskMissing() {
		when(taskRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> taskService.getById(99L)).isInstanceOf(TaskNotFoundException.class);
	}

	@Test
	@DisplayName("Při vytvoření ořízne mezery v názvu")
	void should_trimTitle_whenCreating() {
		when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Task created = taskService.create(new CreateTaskRequest("  New task  "));

		assertThat(created.getTitle()).isEqualTo("New task");
		assertThat(created.isDone()).isFalse();
	}

	@Test
	@DisplayName("Aktualizace změní název i stav")
	void should_updateTitleAndDone_whenTaskExists() {
		when(taskRepository.findById(1L)).thenReturn(Optional.of(new Task("old")));

		Task updated = taskService.update(1L, new UpdateTaskRequest("new", true));

		assertThat(updated.getTitle()).isEqualTo("new");
		assertThat(updated.isDone()).isTrue();
	}

	@Test
	@DisplayName("Toggle přepne stav existujícího úkolu")
	void should_toggleDone_whenTaskExists() {
		when(taskRepository.findById(1L)).thenReturn(Optional.of(new Task("a")));

		assertThat(taskService.toggleDone(1L).isDone()).isTrue();
	}

	@Test
	@DisplayName("Toggle neexistujícího úkolu vyhodí TaskNotFoundException")
	void should_throwNotFound_whenTogglingMissingTask() {
		when(taskRepository.findById(5L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> taskService.toggleDone(5L)).isInstanceOf(TaskNotFoundException.class);
	}

	@Test
	@DisplayName("Smazání neexistujícího úkolu nic nesmaže a vyhodí výjimku")
	void should_notDelete_whenTaskMissing() {
		when(taskRepository.findById(5L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> taskService.delete(5L)).isInstanceOf(TaskNotFoundException.class);
		verify(taskRepository, never()).delete(any());
	}

	@Test
	@DisplayName("Smaže existující úkol")
	void should_delete_whenTaskExists() {
		Task task = new Task("a");
		when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

		taskService.delete(1L);

		verify(taskRepository).delete(task);
	}

}
