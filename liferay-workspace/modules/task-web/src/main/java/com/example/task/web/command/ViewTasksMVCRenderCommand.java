package com.example.task.web.command;

import com.example.task.model.Task;
import com.example.task.service.TaskLocalService;
import com.example.task.web.constants.TaskPortletKeys;

import com.liferay.portal.kernel.portlet.bridges.mvc.MVCRenderCommand;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.WebKeys;

import java.util.List;

import javax.portlet.PortletMode;
import javax.portlet.PortletPreferences;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * RENDER phase: jen čte data a předá je JSP. Nesmí měnit stav - portál ji
 * volá opakovaně (každé načtení stránky, i po akci jiného portletu).
 *
 * mvc.command.name=/ = výchozí render, když URL neobsahuje mvcRenderCommandName.
 * Ve Spring Boot odpovídá: GET /api/tasks?size=N
 */
@Component(
	property = {
		"javax.portlet.name=" + TaskPortletKeys.TASK, "mvc.command.name=/",
		"mvc.command.name=" + TaskPortletKeys.MVC_RENDER_VIEW
	},
	service = MVCRenderCommand.class
)
public class ViewTasksMVCRenderCommand implements MVCRenderCommand {

	/** Načte stránku úkolů aktuálního site a vrátí cestu k JSP */
	@Override
	public String render(
		RenderRequest renderRequest, RenderResponse renderResponse) {

		// Pozor: příkaz "/" MVCPortlet použije ve VŠECH portlet módech
		// a init-param edit-template by se vůbec neuplatnil
		if (PortletMode.EDIT.equals(renderRequest.getPortletMode())) {
			return "/edit.jsp";
		}

		ThemeDisplay themeDisplay = (ThemeDisplay)renderRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		int pageSize = getPageSize(renderRequest.getPreferences());

		List<Task> tasks = _taskLocalService.getGroupTasks(
			themeDisplay.getScopeGroupId(), 0, pageSize);

		renderRequest.setAttribute("tasks", tasks);
		renderRequest.setAttribute(
			"tasksCount",
			_taskLocalService.getGroupTasksCount(
				themeDisplay.getScopeGroupId()));
		renderRequest.setAttribute("pageSize", pageSize);

		return "/view.jsp";
	}

	/** Velikost stránky z PortletPreferences (nastavuje se v EDIT módu) */
	static int getPageSize(PortletPreferences portletPreferences) {
		return GetterUtil.getInteger(
			portletPreferences.getValue(
				TaskPortletKeys.PREF_PAGE_SIZE,
				String.valueOf(TaskPortletKeys.DEFAULT_PAGE_SIZE)),
			TaskPortletKeys.DEFAULT_PAGE_SIZE);
	}

	@Reference
	private TaskLocalService _taskLocalService;

}