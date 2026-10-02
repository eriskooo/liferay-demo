<%@ include file="/init.jsp" %>

<%-- EDIT mód: konfigurace instance portletu uložená do PortletPreferences --%>

<portlet:actionURL name="<%= TaskPortletKeys.MVC_ACTION_SAVE_PREFERENCES %>" var="savePreferencesURL" />

<aui:form action="${savePreferencesURL}" method="post" name="prefs">
	<aui:input
		label="page-size"
		name="<%= TaskPortletKeys.PREF_PAGE_SIZE %>"
		type="number"
		value='<%= portletPreferences.getValue(TaskPortletKeys.PREF_PAGE_SIZE, String.valueOf(TaskPortletKeys.DEFAULT_PAGE_SIZE)) %>'
	/>

	<aui:button type="submit" />
</aui:form>
