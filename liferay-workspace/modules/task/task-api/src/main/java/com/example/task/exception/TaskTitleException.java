/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */
package com.example.task.exception;

import com.liferay.portal.kernel.exception.PortalException;

/**
 * @author Brian Wing Shun Chan
 */
public class TaskTitleException extends PortalException {

	public TaskTitleException() {
	}

	public TaskTitleException(String msg) {
		super(msg);
	}

	public TaskTitleException(String msg, Throwable throwable) {
		super(msg, throwable);
	}

	public TaskTitleException(Throwable throwable) {
		super(throwable);
	}

}