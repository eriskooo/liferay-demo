package com.example.task.web.portlet;

import com.example.task.web.constants.TaskPortletKeys;

import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;

import javax.portlet.Portlet;

import org.osgi.service.component.annotations.Component;

/**
 * Portlet = OSGi služba typu javax.portlet.Portlet. Vlastnosti javax.portlet.*
 * nahrazují dřívější portlet.xml, com.liferay.portlet.* pak liferay-portlet.xml.
 *
 * Třída je záměrně prázdná: MVCPortlet podle parametru mvcRenderCommandName /
 * javax.portlet.action / p_p_resource_id deleguje na MVC*Command komponenty
 * se shodným javax.portlet.name a mvc.command.name.
 *
 * Životní cyklus požadavku (JSR-286 / Portlet 2.0):
 *  - ACTION phase (processAction): změna stavu (insert/update), běží jen pro
 *    portlet, na který se kliklo; výsledek je redirect (PRG pattern).
 *  - RENDER phase (render): idempotentní, vrací HTML fragment; portál ji volá
 *    pro VŠECHNY portlety na stránce při každém načtení stránky.
 *  - RESOURCE phase (serveResource): vrací libovolná data (JSON, soubor)
 *    mimo stránku - typicky pro AJAX. Nejbližší obdoba REST endpointu.
 */
@Component(
	property = {
		"com.liferay.portlet.display-category=category.sample",
		"com.liferay.portlet.instanceable=false",
		"javax.portlet.display-name=Tasks",
		"javax.portlet.init-param.template-path=/",
		"javax.portlet.init-param.view-template=/view.jsp",
		"javax.portlet.name=" + TaskPortletKeys.TASK,
		"javax.portlet.portlet-mode=text/html;view,edit",
		"javax.portlet.resource-bundle=content.Language",
		"javax.portlet.security-role-ref=power-user,user"
	},
	service = Portlet.class
)
public class TaskPortlet extends MVCPortlet {
}