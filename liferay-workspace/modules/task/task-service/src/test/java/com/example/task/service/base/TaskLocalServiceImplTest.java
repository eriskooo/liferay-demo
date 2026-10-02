package com.example.task.service.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.task.exception.NoSuchTaskException;
import com.example.task.exception.TaskTitleException;
import com.example.task.model.Task;
import com.example.task.service.impl.TaskLocalServiceImpl;
import com.example.task.service.persistence.TaskPersistence;

import com.liferay.counter.kernel.service.CounterLocalService;
import com.liferay.portal.kernel.service.ServiceContext;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test bez OSGi kontejneru. @Reference pole jsou protected v base třídě,
 * proto test leží v balíčku ...service.base a naplní je mocky.
 */
class TaskLocalServiceImplTest {

	@BeforeEach
	void setUp() {
		_service.counterLocalService = _counterLocalService;
		_service.taskPersistence = _taskPersistence;
	}

	@Test
	@DisplayName("Vytvoří úkol s ID z Counteru a daty ze ServiceContextu")
	void should_createTask_whenTitleValid() throws Exception {
		Task task = mock(Task.class);

		when(_counterLocalService.increment(Task.class.getName())).thenReturn(42L);
		when(_taskPersistence.create(42L)).thenReturn(task);
		when(_taskPersistence.update(task)).thenReturn(task);

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setCompanyId(1L);
		serviceContext.setScopeGroupId(2L);
		serviceContext.setUserId(3L);

		assertSame(task, _service.addTask("  Learn OSGi  ", serviceContext));

		verify(task).setTitle("Learn OSGi");
		verify(task).setGroupId(2L);
		verify(task).setCompanyId(1L);
		verify(task).setUserId(3L);
		verify(task).setDone(false);
	}

	@Test
	@DisplayName("Prázdný název vyhodí TaskTitleException a nic neuloží")
	void should_throwTitleException_whenTitleBlank() {
		assertThrows(
			TaskTitleException.class,
			() -> _service.addTask("   ", new ServiceContext()));

		verify(_taskPersistence, never()).update(any());
	}

	@Test
	@DisplayName("Null název vyhodí TaskTitleException")
	void should_throwTitleException_whenTitleNull() {
		assertThrows(
			TaskTitleException.class,
			() -> _service.addTask(null, new ServiceContext()));
	}

	@Test
	@DisplayName("Toggle přepne done z false na true")
	void should_toggleDone_whenTaskExists() throws Exception {
		Task task = mock(Task.class);

		when(task.isDone()).thenReturn(false);
		when(_taskPersistence.findByPrimaryKey(7L)).thenReturn(task);
		when(_taskPersistence.update(task)).thenReturn(task);

		_service.toggleDone(7L);

		verify(task).setDone(true);
	}

	@Test
	@DisplayName("Toggle neexistujícího úkolu vyhodí NoSuchTaskException")
	void should_throwNoSuchTask_whenTaskMissing() throws Exception {
		when(
			_taskPersistence.findByPrimaryKey(99L)
		).thenThrow(
			new NoSuchTaskException()
		);

		assertThrows(NoSuchTaskException.class, () -> _service.toggleDone(99L));
	}

	@Test
	@DisplayName("Vrátí úkoly podle done přes finder")
	void should_returnTasksByDone_whenFinderCalled() {
		Task task = mock(Task.class);

		when(_taskPersistence.findByDone(true)).thenReturn(List.of(task));

		assertEquals(List.of(task), _service.getTasksByDone(true));
		assertTrue(_service.getTasksByDone(false).isEmpty());
	}

	@Test
	@DisplayName("Stránka a počet úkolů site delegují na persistence")
	void should_delegatePagingAndCount_whenGroupGiven() {
		when(_taskPersistence.findByGroupId(5L, 0, 10)).thenReturn(List.of());
		when(_taskPersistence.countByGroupId(5L)).thenReturn(0);

		assertTrue(_service.getGroupTasks(5L, 0, 10).isEmpty());
		assertEquals(0, _service.getGroupTasksCount(5L));
		assertFalse(_service.getGroupTasksCount(5L) > 0);
	}

	private final CounterLocalService _counterLocalService = mock(
		CounterLocalService.class);
	private final TaskLocalServiceImpl _service = new TaskLocalServiceImpl();
	private final TaskPersistence _taskPersistence = mock(
		TaskPersistence.class);

}