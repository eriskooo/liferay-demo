/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.example.task.service.impl;

import com.example.task.exception.TaskTitleException;
import com.example.task.model.Task;
import com.example.task.service.base.TaskLocalServiceBaseImpl;

import com.liferay.portal.aop.AopService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.service.ServiceContext;

import java.util.Date;
import java.util.List;

import org.osgi.service.component.annotations.Component;

/**
 * Jediné místo pro vlastní business logiku entity Task.
 *
 * Service Builder tuto třídu vygeneruje jen jednou a pak ji nepřepisuje.
 * Po přidání public metody je nutné znovu spustit buildService, aby se
 * metoda propsala do rozhraní TaskLocalService v task-api.
 *
 * Transakce řeší AOP proxy (AopService) - každá public metoda je
 * automaticky transakční (obdoba @Transactional ve Springu).
 */
@Component(
	property = "model.class.name=com.example.task.model.Task",
	service = AopService.class
)
public class TaskLocalServiceImpl extends TaskLocalServiceBaseImpl {

	/** Vytvoří nový úkol v aktuálním site (groupId) přihlášeného uživatele */
	public Task addTask(String title, ServiceContext serviceContext)
		throws PortalException {

		// Validator.isBlank("   ") vrací v GA132 false -> čisté Java String.isBlank
		if ((title == null) || title.isBlank()) {
			throw new TaskTitleException("Title must not be blank");
		}

		// ID negeneruje DB sekvence, ale Liferay Counter (tabulka Counter)
		long taskId = counterLocalService.increment(Task.class.getName());

		Task task = taskPersistence.create(taskId);

		task.setGroupId(serviceContext.getScopeGroupId());
		task.setCompanyId(serviceContext.getCompanyId());
		task.setUserId(serviceContext.getUserId());
		task.setCreateDate(serviceContext.getCreateDate(new Date()));
		task.setTitle(title.trim());
		task.setDone(false);

		return taskPersistence.update(task);
	}

	/** Přepne příznak done; vyhodí NoSuchTaskException, pokud úkol neexistuje */
	public Task toggleDone(long taskId) throws PortalException {
		Task task = taskPersistence.findByPrimaryKey(taskId);

		task.setDone(!task.isDone());

		return taskPersistence.update(task);
	}

	/** Vrátí úkoly podle stavu - používá vygenerovaný finder "Done" */
	public List<Task> getTasksByDone(boolean done) {
		return taskPersistence.findByDone(done);
	}

	/** Vrátí stránku úkolů daného site (start/end = Liferay styl stránkování) */
	public List<Task> getGroupTasks(long groupId, int start, int end) {
		return taskPersistence.findByGroupId(groupId, start, end);
	}

	/** Počet úkolů v daném site */
	public int getGroupTasksCount(long groupId) {
		return taskPersistence.countByGroupId(groupId);
	}

}