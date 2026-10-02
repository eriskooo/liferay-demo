package com.example.tasks.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Náhrada za TaskPersistence/TaskPersistenceImpl. Finder z service.xml
 * (&lt;finder name="Done"&gt;) = derived query findByDone.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

	/** Stránka úkolů podle stavu */
	Page<Task> findByDone(boolean done, Pageable pageable);

}
