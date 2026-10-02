package com.example.task.web.command;

import com.example.task.web.constants.TaskPortletKeys;

import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCActionCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.util.ParamUtil;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletMode;
import javax.portlet.PortletPreferences;

import org.osgi.service.component.annotations.Component;

/**
 * Uloží PortletPreferences z EDIT módu. Preferences jsou per instance portletu
 * na stránce (tabulka PortletPreferences/PortletPreferenceValue), store() je
 * povolen jen v ACTION fázi.
 *
 * Ve Spring Boot: globální nastavení -> application.yml / @ConfigurationProperties,
 * per-uživatel/per-widget nastavení -> vlastní tabulka + endpoint, nebo frontend.
 */
@Component(
	property = {
		"javax.portlet.name=" + TaskPortletKeys.TASK,
		"mvc.command.name=" + TaskPortletKeys.MVC_ACTION_SAVE_PREFERENCES
	},
	service = MVCActionCommand.class
)
public class SavePreferencesMVCActionCommand extends BaseMVCActionCommand {

	@Override
	protected void doProcessAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		int pageSize = ParamUtil.getInteger(
			actionRequest, TaskPortletKeys.PREF_PAGE_SIZE,
			TaskPortletKeys.DEFAULT_PAGE_SIZE);

		PortletPreferences portletPreferences = actionRequest.getPreferences();

		portletPreferences.setValue(
			TaskPortletKeys.PREF_PAGE_SIZE,
			String.valueOf(Math.max(1, pageSize)));
		portletPreferences.store();

		// Po uložení zpět do VIEW módu
		actionResponse.setPortletMode(PortletMode.VIEW);
	}

}