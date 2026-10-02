package com.example.task.web.command;

import com.example.task.model.Task;
import com.example.task.service.TaskLocalService;
import com.example.task.web.constants.TaskPortletKeys;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.portlet.JSONPortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCResourceCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCResourceCommand;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.WebKeys;

import java.util.List;

import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * RESOURCE phase: vrací JSON bez vykreslení stránky (AJAX z JSP).
 * Je vázaná na portlet a jeho stránku (p_l_id, p_p_id v URL) i na session -
 * proto není vhodná jako veřejné API. Ve Spring Boot: GET /api/tasks (JSON).
 */
@Component(
	property = {
		"javax.portlet.name=" + TaskPortletKeys.TASK,
		"mvc.command.name=" + TaskPortletKeys.MVC_RESOURCE_TASKS_JSON
	},
	service = MVCResourceCommand.class
)
public class TasksJsonMVCResourceCommand extends BaseMVCResourceCommand {

	@Override
	protected void doServeResource(
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)resourceRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		List<Task> tasks = _taskLocalService.getGroupTasks(
			themeDisplay.getScopeGroupId(), 0,
			ViewTasksMVCRenderCommand.getPageSize(
				resourceRequest.getPreferences()));

		JSONArray jsonArray = _jsonFactory.createJSONArray();

		tasks.forEach(
			task -> jsonArray.put(
				JSONUtil.put(
					"createDate", String.valueOf(task.getCreateDate().toInstant())
				).put(
					"done", task.isDone()
				).put(
					"id", task.getTaskId()
				).put(
					"title", task.getTitle()
				)));

		JSONPortletResponseUtil.writeJSON(
			resourceRequest, resourceResponse, jsonArray);
	}

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private TaskLocalService _taskLocalService;

}