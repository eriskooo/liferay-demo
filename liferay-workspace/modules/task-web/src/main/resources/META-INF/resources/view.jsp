<%@ include file="/init.jsp" %>

<%--
	actionURL   -> ACTION fáze (změna stavu), pak redirect + render
	renderURL   -> jen RENDER fáze (navigace, žádná změna dat)
	resourceURL -> RESOURCE fáze (JSON/soubor, AJAX), stránka se nepřekresluje
	<portlet:namespace/> = unikátní prefix portletu (_com_example_task_web_TaskPortlet_),
	nutný pro id/name prvků, protože na stránce může být více portletů.
--%>

<portlet:actionURL name="<%= TaskPortletKeys.MVC_ACTION_ADD %>" var="addTaskURL" />

<portlet:renderURL var="refreshURL">
	<portlet:param name="mvcRenderCommandName" value="<%= TaskPortletKeys.MVC_RENDER_VIEW %>" />
</portlet:renderURL>

<portlet:resourceURL id="<%= TaskPortletKeys.MVC_RESOURCE_TASKS_JSON %>" var="tasksJsonURL" />

<liferay-ui:error exception="<%= TaskTitleException.class %>" message="task-title-is-required" />

<aui:form action="${addTaskURL}" method="post" name="fm">
	<aui:input label="title" name="title" type="text" />

	<aui:button type="submit" value="add-task" />
</aui:form>

<p>${tasksCount} task(s), showing max ${pageSize} &middot; <a href="${refreshURL}">refresh (render URL)</a></p>

<c:choose>
	<c:when test="${empty tasks}">
		<p><liferay-ui:message key="no-tasks" /></p>
	</c:when>
	<c:otherwise>
		<table class="table">
			<thead>
				<tr><th>ID</th><th><liferay-ui:message key="title" /></th><th><liferay-ui:message key="done" /></th><th></th></tr>
			</thead>
			<tbody>
				<c:forEach items="${tasks}" var="task">
					<portlet:actionURL name="<%= TaskPortletKeys.MVC_ACTION_TOGGLE %>" var="toggleURL">
						<portlet:param name="taskId" value="${task.taskId}" />
					</portlet:actionURL>

					<tr>
						<td>${task.taskId}</td>
						<td><c:out value="${task.title}" /></td>
						<td>${task.done ? "&#10004;" : ""}</td>
						<td>
							<aui:form action="${toggleURL}" method="post" name="toggle${task.taskId}">
								<aui:button type="submit" value="toggle" />
							</aui:form>
						</td>
					</tr>
				</c:forEach>
			</tbody>
		</table>
	</c:otherwise>
</c:choose>

<button class="btn btn-secondary" id="<portlet:namespace />loadJson" type="button"><liferay-ui:message key="load-json" /></button>

<pre id="<portlet:namespace />json"></pre>

<script>
	document.getElementById('<portlet:namespace />loadJson').addEventListener('click', function () {
		Liferay.Util.fetch('${tasksJsonURL}')
			.then(function (response) { return response.json(); })
			.then(function (data) {
				document.getElementById('<portlet:namespace />json').textContent = JSON.stringify(data, null, 2);
			});
	});
</script>
