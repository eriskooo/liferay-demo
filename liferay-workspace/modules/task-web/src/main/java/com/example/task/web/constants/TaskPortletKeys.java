package com.example.task.web.constants;

/**
 * Konstanty portletu - názvy MVC příkazů jsou "routy" uvnitř portletu
 * (obdoba URL mappingu v @RequestMapping).
 */
public class TaskPortletKeys {

	public static final String TASK = "com_example_task_web_TaskPortlet";

	public static final String MVC_ACTION_ADD = "/task/add";

	public static final String MVC_ACTION_SAVE_PREFERENCES =
		"/task/save_preferences";

	public static final String MVC_ACTION_TOGGLE = "/task/toggle";

	public static final String MVC_RENDER_VIEW = "/task/view";

	public static final String MVC_RESOURCE_TASKS_JSON = "/task/json";

	public static final String PREF_PAGE_SIZE = "pageSize";

	public static final int DEFAULT_PAGE_SIZE = 10;

}
