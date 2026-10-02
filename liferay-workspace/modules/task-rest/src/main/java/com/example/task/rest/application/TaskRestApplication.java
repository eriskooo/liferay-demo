package com.example.task.rest.application;

import com.example.task.exception.NoSuchTaskException;
import com.example.task.exception.TaskTitleException;
import com.example.task.model.Task;
import com.example.task.service.TaskLocalService;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;

import java.util.Collections;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;

/**
 * REST nad stejnou TaskLocalService jako portlet - JAX-RS Whiteboard komponenta.
 *
 * Proč JAX-RS místo REST Builderu: jedna třída, žádné generování z OpenAPI YAML
 * a dvou modulů (api/impl). REST Builder se vyplatí pro "produkční" headless API
 * (/o/headless-*): generuje DTO, OpenAPI, GraphQL, stránkování, filtry, batch.
 *
 * URL: http://localhost:8080/o/tasks  (prefix /o = OSGi JAX-RS whiteboard)
 */
@Component(
	property = {
		JaxrsWhiteboardConstants.JAX_RS_APPLICATION_BASE + "=/tasks",
		JaxrsWhiteboardConstants.JAX_RS_NAME + "=Tasks.Rest",
		// Bez OAuth2 scope kontroly - stačí Basic Auth (jen pro demo!)
		"auth.verifier.guest.allowed=false", "liferay.oauth2=false"
	},
	service = Application.class
)
public class TaskRestApplication extends Application {

	@Override
	public Set<Object> getSingletons() {
		return Collections.singleton(this);
	}

	/** Seznam úkolů daného site, volitelně filtrovaný podle done */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getTasks(
		@QueryParam("groupId") long groupId, @QueryParam("done") Boolean done,
		@DefaultValue("20") @QueryParam("size") int size) {

		JSONArray jsonArray = _jsonFactory.createJSONArray();

		(done == null ?
			_taskLocalService.getGroupTasks(groupId, 0, size) :
				_taskLocalService.getTasksByDone(done)
		).forEach(
			task -> jsonArray.put(_toJSON(task))
		);

		return jsonArray.toString();
	}

	/** Vytvoří úkol; body: {"title": "..."} */
	@Consumes(MediaType.APPLICATION_JSON)
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response addTask(
			@QueryParam("groupId") long groupId, String body,
			@Context HttpServletRequest httpServletRequest)
		throws Exception {

		JSONObject jsonObject = _jsonFactory.createJSONObject(body);

		ServiceContext serviceContext = ServiceContextFactory.getInstance(
			httpServletRequest);

		serviceContext.setScopeGroupId(groupId);

		try {
			Task task = _taskLocalService.addTask(
				jsonObject.getString("title"), serviceContext);

			return Response.status(
				Response.Status.CREATED
			).entity(
				_toJSON(task).toString()
			).build();
		}
		catch (TaskTitleException taskTitleException) {
			return Response.status(
				Response.Status.BAD_REQUEST
			).entity(
				taskTitleException.getMessage()
			).build();
		}
	}

	/** Přepne done */
	@PATCH
	@Path("/{taskId}/toggle")
	@Produces(MediaType.APPLICATION_JSON)
	public Response toggle(@PathParam("taskId") long taskId)
		throws PortalException {

		try {
			return Response.ok(
				_toJSON(_taskLocalService.toggleDone(taskId)).toString()
			).build();
		}
		catch (NoSuchTaskException noSuchTaskException) {
			return Response.status(
				Response.Status.NOT_FOUND
			).build();
		}
	}

	// Liferay JSON serializuje long jako string ("id":"1") kvůli přesnosti v JS.
	// REST Builder / Spring (Jackson) by vrátil číslo - pozor na kompatibilitu klientů.
	private JSONObject _toJSON(Task task) {
		return JSONUtil.put(
			"createDate", String.valueOf(task.getCreateDate().toInstant())
		).put(
			"done", task.isDone()
		).put(
			"groupId", task.getGroupId()
		).put(
			"id", task.getTaskId()
		).put(
			"title", task.getTitle()
		);
	}

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private TaskLocalService _taskLocalService;

}