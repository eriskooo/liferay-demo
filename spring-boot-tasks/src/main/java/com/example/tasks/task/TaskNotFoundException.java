package com.example.tasks.task;

/** Úkol neexistuje - obdoba NoSuchTaskException ze Service Builderu (ta je checked) */
public class TaskNotFoundException extends RuntimeException {

	public TaskNotFoundException(long id) {
		super("Task " + id + " not found");
	}

}
