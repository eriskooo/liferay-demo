package com.example.task.web.command;

import com.example.task.exception.TaskTitleException;
import com.example.task.model.Task;
import com.example.task.service.TaskLocalService;
import com.example.task.web.constants.TaskPortletKeys;

import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCActionCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.util.ParamUtil;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * ACTION phase: změna stavu. Po doběhnutí portál udělá redirect a pak RENDER
 * fázi všech portletů (Post/Redirect/Get) - refresh stránky akci nezopakuje.
 *
 * Parametry jsou namespacované (_com_example_task_web_TaskPortlet_title);
 * ParamUtil namespace řeší sám. Ve Spring Boot: POST /api/tasks {"title": ".."}
 */
@Component(
	property = {
		"javax.portlet.name=" + TaskPortletKeys.TASK,
		"mvc.command.name=" + TaskPortletKeys.MVC_ACTION_ADD
	},
	service = MVCActionCommand.class
)
public class AddTaskMVCActionCommand extends BaseMVCActionCommand {

	@Override
	protected void doProcessAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		String title = ParamUtil.getString(actionRequest, "title");

		// ServiceContext nese userId, companyId, scopeGroupId, oprávnění...
		// = to, co ve Spring Boot dodá SecurityContext + request scope
		ServiceContext serviceContext = ServiceContextFactory.getInstance(
			Task.class.getName(), actionRequest);

		try {
			_taskLocalService.addTask(title, serviceContext);
		}
		catch (TaskTitleException taskTitleException) {
			// Chyba přežije redirect díky session (obdoba flash attributes)
			SessionErrors.add(actionRequest, taskTitleException.getClass());
		}
	}

	@Reference
	private TaskLocalService _taskLocalService;

}