package com.example.tasks.task;

import com.example.tasks.config.TaskProperties;
import com.example.tasks.task.dto.CreateTaskRequest;
import com.example.tasks.task.dto.UpdateTaskRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logika - náhrada za TaskLocalServiceImpl.
 * Transakce: v Liferay automaticky přes AopService, tady explicitně @Transactional.
 * Závislosti: místo OSGi @Reference konstruktorová injekce Spring DI.
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createDate");

	private final TaskRepository taskRepository;

	private final TaskProperties taskProperties;

	public TaskService(TaskRepository taskRepository, TaskProperties taskProperties) {
		this.taskRepository = taskRepository;
		this.taskProperties = taskProperties;
	}

	/** Vrátí stránku úkolů, volitelně filtrovanou podle done (null = všechny) */
	public Page<Task> findAll(Boolean done, int page, Integer size) {
		Pageable pageable = PageRequest.of(Math.max(page, 0), taskProperties.resolvePageSize(size), NEWEST_FIRST);
		if (done == null) {
			return taskRepository.findAll(pageable);
		}
		return taskRepository.findByDone(done, pageable);
	}

	/** Vrátí úkol podle ID, nebo vyhodí TaskNotFoundException */
	public Task getById(long id) {
		return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
	}

	/** Vytvoří nový nehotový úkol */
	@Transactional
	public Task create(CreateTaskRequest request) {
		return taskRepository.save(new Task(request.title().trim()));
	}

	/** Aktualizuje název a stav úkolu */
	@Transactional
	public Task update(long id, UpdateTaskRequest request) {
		Task task = getById(id);
		task.setTitle(request.title().trim());
		task.setDone(request.done());
		return task;
	}

	/** Přepne done - ekvivalent portletové akce /task/toggle */
	@Transactional
	public Task toggleDone(long id) {
		Task task = getById(id);
		task.toggleDone();
		return task;
	}

	/** Smaže úkol, neexistující ID hlásí jako TaskNotFoundException */
	@Transactional
	public void delete(long id) {
		taskRepository.delete(getById(id));
	}

}
