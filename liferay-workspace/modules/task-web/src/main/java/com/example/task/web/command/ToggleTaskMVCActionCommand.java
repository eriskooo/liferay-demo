package com.example.task.web.command;

import com.example.task.service.TaskLocalService;
import com.example.task.web.constants.TaskPortletKeys;

import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCActionCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.util.ParamUtil;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * ACTION phase: přepne stav úkolu. Ve Spring Boot: PATCH /api/tasks/{id}/toggle
 */
@Component(
	property = {
		"javax.portlet.name=" + TaskPortletKeys.TASK,
		"mvc.command.name=" + TaskPortletKeys.MVC_ACTION_TOGGLE
	},
	service = MVCActionCommand.class
)
public class ToggleTaskMVCActionCommand extends BaseMVCActionCommand {

	@Override
	protected void doProcessAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		// Demo bez kontroly oprávnění; v reálu ModelResourcePermission.check(..)
		_taskLocalService.toggleDone(ParamUtil.getLong(actionRequest, "taskId"));
	}

	@Reference
	private TaskLocalService _taskLocalService;

}