<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>

<%@ taglib uri="http://liferay.com/tld/aui" prefix="aui" %><%@
taglib uri="http://liferay.com/tld/theme" prefix="liferay-theme" %><%@
taglib uri="http://liferay.com/tld/ui" prefix="liferay-ui" %>

<%@ page import="com.example.task.exception.TaskTitleException" %><%@
page import="com.example.task.web.constants.TaskPortletKeys" %>

<%-- Zpřístupní renderRequest, portletPreferences, themeDisplay... jako proměnné --%>
<liferay-theme:defineObjects />

<portlet:defineObjects />
