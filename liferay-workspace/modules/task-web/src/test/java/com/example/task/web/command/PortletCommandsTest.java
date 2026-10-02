package com.example.task.web.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.task.web.constants.TaskPortletKeys;

import com.liferay.portal.kernel.util.Props;
import com.liferay.portal.kernel.util.PropsUtil;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletMode;
import javax.portlet.PortletPreferences;
import javax.portlet.RenderRequest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortletCommandsTest {

	@BeforeAll
	static void setUpClass() {
		// ParamUtil při inicializaci čte portal.properties přes PropsUtil -
		// mimo portál je nutné podstrčit mock (běžný trik v Liferay unit testech)
		PropsUtil.setProps(mock(Props.class));
	}

	@Test
	@DisplayName("Velikost stránky se čte z PortletPreferences")
	void should_readPageSize_whenPreferenceSet() {
		assertEquals(5, ViewTasksMVCRenderCommand.getPageSize(_prefs("5")));
	}

	@Test
	@DisplayName("Nečíselná preference vrátí výchozí velikost")
	void should_returnDefault_whenPreferenceInvalid() {
		assertEquals(
			TaskPortletKeys.DEFAULT_PAGE_SIZE,
			ViewTasksMVCRenderCommand.getPageSize(_prefs("abc")));
	}

	@Test
	@DisplayName("V EDIT módu render vrátí edit.jsp")
	void should_returnEditJsp_whenEditMode() {
		RenderRequest renderRequest = mock(RenderRequest.class);

		when(renderRequest.getPortletMode()).thenReturn(PortletMode.EDIT);

		assertEquals(
			"/edit.jsp",
			new ViewTasksMVCRenderCommand().render(renderRequest, null));
	}

	@Test
	@DisplayName("Uložení preferencí ořízne nekladnou hodnotu na 1 a přepne do VIEW")
	void should_storeMinimumOne_whenPageSizeNotPositive() throws Exception {
		PortletPreferences preferences = mock(PortletPreferences.class);
		ActionRequest actionRequest = mock(ActionRequest.class);
		ActionResponse actionResponse = mock(ActionResponse.class);

		when(actionRequest.getPreferences()).thenReturn(preferences);
		when(
			actionRequest.getParameter(TaskPortletKeys.PREF_PAGE_SIZE)
		).thenReturn(
			"0"
		);

		new SavePreferencesMVCActionCommand().doProcessAction(
			actionRequest, actionResponse);

		verify(preferences).setValue(TaskPortletKeys.PREF_PAGE_SIZE, "1");
		verify(preferences).store();
		verify(actionResponse).setPortletMode(PortletMode.VIEW);
	}

	private static PortletPreferences _prefs(String pageSize) {
		PortletPreferences preferences = mock(PortletPreferences.class);

		when(
			preferences.getValue(
				TaskPortletKeys.PREF_PAGE_SIZE,
				String.valueOf(TaskPortletKeys.DEFAULT_PAGE_SIZE))
		).thenReturn(
			pageSize
		);

		return preferences;
	}

}