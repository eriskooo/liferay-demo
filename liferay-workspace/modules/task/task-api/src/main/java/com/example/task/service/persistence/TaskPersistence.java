/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.example.task.service.persistence;

import com.example.task.exception.NoSuchTaskException;
import com.example.task.model.Task;

import com.liferay.portal.kernel.service.persistence.BasePersistence;

import org.osgi.annotation.versioning.ProviderType;

/**
 * The persistence interface for the task service.
 *
 * <p>
 * Caching information and settings can be found in <code>portal.properties</code>
 * </p>
 *
 * @author Brian Wing Shun Chan
 * @see TaskUtil
 * @generated
 */
@ProviderType
public interface TaskPersistence extends BasePersistence<Task> {

	/*
	 * NOTE FOR DEVELOPERS:
	 *
	 * Never modify or reference this interface directly. Always use {@link TaskUtil} to access the task persistence. Modify <code>service.xml</code> and rerun ServiceBuilder to regenerate this interface.
	 */

	/**
	 * Returns all the tasks where done = &#63;.
	 *
	 * @param done the done
	 * @return the matching tasks
	 */
	public java.util.List<Task> findByDone(boolean done);

	/**
	 * Returns a range of all the tasks where done = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>TaskModelImpl</code>.
	 * </p>
	 *
	 * @param done the done
	 * @param start the lower bound of the range of tasks
	 * @param end the upper bound of the range of tasks (not inclusive)
	 * @return the range of matching tasks
	 */
	public java.util.List<Task> findByDone(boolean done, int start, int end);

	/**
	 * Returns an ordered range of all the tasks where done = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>TaskModelImpl</code>.
	 * </p>
	 *
	 * @param done the done
	 * @param start the lower bound of the range of tasks
	 * @param end the upper bound of the range of tasks (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of matching tasks
	 */
	public java.util.List<Task> findByDone(
		boolean done, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<Task>
			orderByComparator);

	/**
	 * Returns an ordered range of all the tasks where done = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>TaskModelImpl</code>.
	 * </p>
	 *
	 * @param done the done
	 * @param start the lower bound of the range of tasks
	 * @param end the upper bound of the range of tasks (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @param useFinderCache whether to use the finder cache
	 * @return the ordered range of matching tasks
	 */
	public java.util.List<Task> findByDone(
		boolean done, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<Task>
			orderByComparator,
		boolean useFinderCache);

	/**
	 * Returns the first task in the ordered set where done = &#63;.
	 *
	 * @param done the done
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching task
	 * @throws NoSuchTaskException if a matching task could not be found
	 */
	public Task findByDone_First(
			boolean done,
			com.liferay.portal.kernel.util.OrderByComparator<Task>
				orderByComparator)
		throws NoSuchTaskException;

	/**
	 * Returns the first task in the ordered set where done = &#63;.
	 *
	 * @param done the done
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching task, or <code>null</code> if a matching task could not be found
	 */
	public Task fetchByDone_First(
		boolean done,
		com.liferay.portal.kernel.util.OrderByComparator<Task>
			orderByComparator);

	/**
	 * Returns the last task in the ordered set where done = &#63;.
	 *
	 * @param done the done
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching task
	 * @throws NoSuchTaskException if a matching task could not be found
	 */
	public Task findByDone_Last(
			boolean done,
			com.liferay.portal.kernel.util.OrderByComparator<Task>
				orderByComparator)
		throws NoSuchTaskException;

	/**
	 * Returns the last task in the ordered set where done = &#63;.
	 *
	 * @param done the done
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching task, or <code>null</code> if a matching task could not be found
	 */
	public Task fetchByDone_Last(
		boolean done,
		com.liferay.portal.kernel.util.OrderByComparator<Task>
			orderByComparator);

	/**
	 * Returns the tasks before and after the current task in the ordered set where done = &#63;.
	 *
	 * @param taskId the primary key of the current task
	 * @param done the done
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the previous, current, and next task
	 * @throws NoSuchTaskException if a task with the primary key could not be found
	 */
	public Task[] findByDone_PrevAndNext(
			long taskId, boolean done,
			com.liferay.portal.kernel.util.OrderByComparator<Task>
				orderByComparator)
		throws NoSuchTaskException;

	/**
	 * Removes all the tasks where done = &#63; from the database.
	 *
	 * @param done the done
	 */
	public void removeByDone(boolean done);

	/**
	 * Returns the number of tasks where done = &#63;.
	 *
	 * @param done the done
	 * @return the number of matching tasks
	 */
	public int countByDone(boolean done);

	/**
	 * Returns all the tasks where groupId = &#63;.
	 *
	 * @param groupId the group ID
	 * @return the matching tasks
	 */
	public java.util.List<Task> findByGroupId(long groupId);

	/**
	 * Returns a range of all the tasks where groupId = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>TaskModelImpl</code>.
	 * </p>
	 *
	 * @param groupId the group ID
	 * @param start the lower bound of the range of tasks
	 * @param end the upper bound of the range of tasks (not inclusive)
	 * @return the range of matching tasks
	 */
	public java.util.List<Task> findByGroupId(long groupId, int start, int end);

	/**
	 * Returns an ordered range of all the tasks where groupId = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>TaskModelImpl</code>.
	 * </p>
	 *
	 * @param groupId the group ID
	 * @param start the lower bound of the range of tasks
	 * @param end the upper bound of the range of tasks (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of matching tasks
	 */
	public java.util.List<Task> findByGroupId(
		long groupId, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<Task>
			orderByComparator);

	/**
	 * Returns an ordered range of all the tasks where groupId = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>TaskModelImpl</code>.
	 * </p>
	 *
	 * @param groupId the group ID
	 * @param start the lower bound of the range of tasks
	 * @param end the upper bound of the range of tasks (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @param useFinderCache whether to use the finder cache
	 * @return the ordered range of matching tasks
	 */
	public java.util.List<Task> findByGroupId(
		long groupId, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<Task>
			orderByComparator,
		boolean useFinderCache);

	/**
	 * Returns the first task in the ordered set where groupId = &#63;.
	 *
	 * @param groupId the group ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching task
	 * @throws NoSuchTaskException if a matching task could not be found
	 */
	public Task findByGroupId_First(
			long groupId,
			com.liferay.portal.kernel.util.OrderByComparator<Task>
				orderByComparator)
		throws NoSuchTaskException;

	/**
	 * Returns the first task in the ordered set where groupId = &#63;.
	 *
	 * @param groupId the group ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching task, or <code>null</code> if a matching task could not be found
	 */
	public Task fetchByGroupId_First(
		long groupId,
		com.liferay.portal.kernel.util.OrderByComparator<Task>
			orderByComparator);

	/**
	 * Returns the last task in the ordered set where groupId = &#63;.
	 *
	 * @param groupId the group ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching task
	 * @throws NoSuchTaskException if a matching task could not be found
	 */
	public Task findByGroupId_Last(
			long groupId,
			com.liferay.portal.kernel.util.OrderByComparator<Task>
				orderByComparator)
		throws NoSuchTaskException;

	/**
	 * Returns the last task in the ordered set where groupId = &#63;.
	 *
	 * @param groupId the group ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching task, or <code>null</code> if a matching task could not be found
	 */
	public Task fetchByGroupId_Last(
		long groupId,
		com.liferay.portal.kernel.util.OrderByComparator<Task>
			orderByComparator);

	/**
	 * Returns the tasks before and after the current task in the ordered set where groupId = &#63;.
	 *
	 * @param taskId the primary key of the current task
	 * @param groupId the group ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the previous, current, and next task
	 * @throws NoSuchTaskException if a task with the primary key could not be found
	 */
	public Task[] findByGroupId_PrevAndNext(
			long taskId, long groupId,
			com.liferay.portal.kernel.util.OrderByComparator<Task>
				orderByComparator)
		throws NoSuchTaskException;

	/**
	 * Removes all the tasks where groupId = &#63; from the database.
	 *
	 * @param groupId the group ID
	 */
	public void removeByGroupId(long groupId);

	/**
	 * Returns the number of tasks where groupId = &#63;.
	 *
	 * @param groupId the group ID
	 * @return the number of matching tasks
	 */
	public int countByGroupId(long groupId);

	/**
	 * Caches the task in the entity cache if it is enabled.
	 *
	 * @param task the task
	 */
	public void cacheResult(Task task);

	/**
	 * Caches the tasks in the entity cache if it is enabled.
	 *
	 * @param tasks the tasks
	 */
	public void cacheResult(java.util.List<Task> tasks);

	/**
	 * Creates a new task with the primary key. Does not add the task to the database.
	 *
	 * @param taskId the primary key for the new task
	 * @return the new task
	 */
	public Task create(long taskId);

	/**
	 * Removes the task with the primary key from the database. Also notifies the appropriate model listeners.
	 *
	 * @param taskId the primary key of the task
	 * @return the task that was removed
	 * @throws NoSuchTaskException if a task with the primary key could not be found
	 */
	public Task remove(long taskId) throws NoSuchTaskException;

	public Task updateImpl(Task task);

	/**
	 * Returns the task with the primary key or throws a <code>NoSuchTaskException</code> if it could not be found.
	 *
	 * @param taskId the primary key of the task
	 * @return the task
	 * @throws NoSuchTaskException if a task with the primary key could not be found
	 */
	public Task findByPrimaryKey(long taskId) throws NoSuchTaskException;

	/**
	 * Returns the task with the primary key or returns <code>null</code> if it could not be found.
	 *
	 * @param taskId the primary key of the task
	 * @return the task, or <code>null</code> if a task with the primary key could not be found
	 */
	public Task fetchByPrimaryKey(long taskId);

	/**
	 * Returns all the tasks.
	 *
	 * @return the tasks
	 */
	public java.util.List<Task> findAll();

	/**
	 * Returns a range of all the tasks.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>TaskModelImpl</code>.
	 * </p>
	 *
	 * @param start the lower bound of the range of tasks
	 * @param end the upper bound of the range of tasks (not inclusive)
	 * @return the range of tasks
	 */
	public java.util.List<Task> findAll(int start, int end);

	/**
	 * Returns an ordered range of all the tasks.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>TaskModelImpl</code>.
	 * </p>
	 *
	 * @param start the lower bound of the range of tasks
	 * @param end the upper bound of the range of tasks (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of tasks
	 */
	public java.util.List<Task> findAll(
		int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<Task>
			orderByComparator);

	/**
	 * Returns an ordered range of all the tasks.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>TaskModelImpl</code>.
	 * </p>
	 *
	 * @param start the lower bound of the range of tasks
	 * @param end the upper bound of the range of tasks (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @param useFinderCache whether to use the finder cache
	 * @return the ordered range of tasks
	 */
	public java.util.List<Task> findAll(
		int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<Task>
			orderByComparator,
		boolean useFinderCache);

	/**
	 * Removes all the tasks from the database.
	 */
	public void removeAll();

	/**
	 * Returns the number of tasks.
	 *
	 * @return the number of tasks
	 */
	public int countAll();

}