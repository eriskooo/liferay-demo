Create a learning/demo project that demonstrates core Liferay backend concepts and their migration to Spring Boot. I have a job interview next week about migrating a Liferay application to Spring Boot + REST (+ probably Angular). Focus on BACKEND; keep UI minimal.

IMPORTANT
- Verify all versions (Liferay CE image/release, Liferay Workspace Gradle plugin, Java version compatibility, Spring Boot) against official sources before using them. Do not guess versions. If something cannot be verified, say so.
- Everything must actually build and run. Test it yourself, fix errors, don't stop at "should work".
- Use Docker for the Liferay runtime (official liferay/portal CE image).

PART 1 – Liferay Workspace (Gradle), domain: simple "Task" (id, title, done, createDate)
1. service-builder module (api + service):
   - service.xml with Task entity, finder by "done"
   - TaskLocalServiceImpl with custom methods (addTask, toggleDone)
   - show what Service Builder generates and where custom code goes
2. OSGi demo module:
   - an API bundle with an interface (e.g. GreetingService) + exported package
   - an impl bundle using Declarative Services (@Component, @Reference)
   - a second implementation with service.ranking to show dynamic service selection
   - show bnd.bnd / MANIFEST (Export-Package, Import-Package, versions)
3. portlet module (MVCPortlet):
   - @Component with javax.portlet.* properties
   - MVCRenderCommand (list tasks), MVCActionCommand (add, toggle), MVCResourceCommand (return tasks as JSON)
   - PortletPreferences in EDIT mode (e.g. page size)
   - minimal JSP with portlet taglibs (actionURL, renderURL, resourceURL, namespace)
   - comments explaining action vs render phase
4. Liferay headless REST: briefly show how the same data could be exposed via REST Builder or a JAX-RS OSGi component (pick the simpler one and explain why).
5. docker-compose to run Liferay + deploy modules; Gogo shell commands to inspect bundles (lb, diag, services).

PART 2 – Spring Boot equivalent (separate Gradle/Maven project, latest stable Spring Boot, Java 21 if compatible)
- Same Task domain: JPA entity, Spring Data repository, service layer, REST controller (CRUD + toggle), DTOs, validation, global exception handler
- OpenAPI/Swagger UI
- Flyway migration that reads the Liferay Service Builder table structure (show how existing Liferay tables/data could be migrated)
- Spring Security placeholder showing where Liferay roles/permissions would be replaced (e.g. Keycloak/OAuth2 resource server, config only)
- Integration tests (Testcontainers + PostgreSQL)

PART 3 – Documentation (README.md in Slovak)
- How to run both parts
- Side-by-side mapping table: portlet render/action/resource -> REST endpoints; PortletPreferences -> config; Service Builder -> JPA; OSGi DS -> Spring DI; Liferay permissions -> Spring Security; portlet session -> stateless API
- Typical migration challenges and strategies (strangler fig, data migration, users/permissions, URLs, search/Elasticsearch, documents & media)
- 15 likely interview questions about Liferay -> Spring Boot migration with short answers

Work step by step: first propose the project structure and verified versions, then implement, build, run and test each part.