# 08 – REST v Liferay

## Čo sa naučíš

- Aké sú v Liferay spôsoby, ako vystaviť REST API, a prečo demo používa JAX-RS
- Ako funguje JAX-RS Whiteboard (OSGi komponent = REST aplikácia na `/o/...`)
- Ako volať API cez `curl` (Basic Auth) a aké odpovede čakať
- Ktoré slabiny demo API má a ako ich rieši Spring Boot

**Predpoklad:** Liferay beží, moduly sú nasadené ([kapitola 04](04_build_a_deploy_modulov.md)).

---

## Teória v skratke

### Možnosti REST v Liferay 7.4

| Spôsob | Čo to je | Kedy |
|---|---|---|
| **Headless API** (vstavané) | Liferay vlastné REST API na `/o/headless-*` (používatelia, obsah, dokumenty, sites…) | Práca s dátami portálu. Prehliadač API: `http://localhost:8080/o/api` |
| **REST Builder** | Generátor: z OpenAPI YAML vygeneruje JAX-RS resource, DTO, GraphQL, stránkovanie, filtre, batch | „Produkčné“ API vlastných entít v Liferay štýle |
| **JAX-RS Whiteboard** (ručne) | Jedna OSGi komponenta `extends javax.ws.rs.core.Application` s `@GET`, `@POST`… | Jednoduché vlastné API, **toto používa demo** |
| JSON Web Services (staršie) | Automaticky z remote servisov Service Buildera na `/api/jsonws` | Legacy, používa ho `create-demo-page.sh` |

**Prečo demo nepoužíva REST Builder:** REST Builder potrebuje OpenAPI YAML, dva ďalšie moduly (`*-rest-api`, `*-rest-impl`) a generovanie. Pre ukážku je to zbytočne veľa. JAX-RS komponent je jedna trieda, ktorej princíp je takmer rovnaký ako pri Spring `@RestController`. Na pohovore stačí vedieť, že REST Builder existuje a čo generuje.

### JAX-RS Whiteboard

**JAX-RS** je Java štandard pre REST (`@Path`, `@GET`, `@POST`, `@Produces`). Liferay ho implementuje cez Apache CXF.

**Whiteboard** je OSGi vzor: nevoláš žiadny „register“. Vyvesíš na nástenku službu typu `javax.ws.rs.core.Application` so správnymi vlastnosťami a JAX-RS runtime si ju sám nájde a sprístupní:

```
 @Component(service = Application.class,
            property = "osgi.jaxrs.application.base=/tasks")
                         |
                         v
 JAX-RS Whiteboard (Apache CXF v Liferay)  →  http://localhost:8080/o/tasks
                                                                    ^^ ^^^^^
                                       prefix všetkých OSGi JAX-RS aplikácií   application.base
```

Je to ten istý princíp ako portlet (`service = Portlet.class`) v [kapitole 07](07_portlet_mvc.md) a Gogo príkaz v [kapitole 05](05_osgi_a_gogo_shell.md).

---

## Krok po kroku

Premenné, nech sú príkazy kratšie (Git Bash):

```bash
U=test@liferay.com:test
B=http://localhost:8080/o/tasks
G=20117   # groupId site Guest, zistíš: docker compose exec -T postgres psql -U liferay -d lportal -tAc "select groupid from group_ where friendlyurl='/guest'"
```

### 1. Bez prihlásenia: 403

```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' "$B?groupId=$G"
```

```
HTTP 403
```

Telo odpovede je HTML stránka s presmerovaním na `/c`, nie JSON. Zlé heslo (`-u test@liferay.com:zle`) vráti tiež `403`.

### 2. Zoznam úloh (GET)

```bash
curl -s -w ' HTTP %{http_code}\n' -u $U "$B?groupId=$G"
```

```
[{"groupId":"20117","id":"3","title":"Kúpiť chlieb","done":false,"createDate":"2026-10-04T07:10:12.908Z"},{"groupId":"20117","id":"2","title":"Napísať poznámky","done":false,"createDate":"2026-10-04T07:10:01.815Z"},{"groupId":"20117","id":"1","title":"Kúpiť mlieko","done":false,"createDate":"2026-10-04T07:10:00.889Z"}] HTTP 200
```

Parametre:
- `size=2` – max. počet (predvolene 20),
- `done=true|false` – filter podľa stavu.

```bash
curl -s -u $U "$B?groupId=$G&size=2"
```

```
[{"groupId":"20117","id":"102","title":"Guest task","done":true,...},{"groupId":"20117","id":"101","title":"Zavolať mame","done":false,...}]
```

Bez `groupId` dostaneš prázdne pole `[]`, lebo `groupId` je potom 0 a v site 0 nie sú žiadne úlohy.

### 3. Vytvor úlohu (POST)

JSON s diakritikou posielaj **zo súboru**. V Git Bash na Windows `curl -d '{"title":"Kúpiť"}'` pri overovaní neposlal UTF-8 a v DB vzniklo `K�pit`:

```bash
printf '{"title":"Kúpiť chlieb"}' > task.json
curl -s -w ' HTTP %{http_code}\n' -u $U -H 'Content-Type: application/json' \
  --data-binary @task.json "$B?groupId=$G"
rm task.json
```

```
{"groupId":"20117","id":"3","title":"Kúpiť chlieb","done":false,"createDate":"2026-10-04T07:10:12.908Z"} HTTP 201
```

Prázdny title:

```bash
curl -s -w ' HTTP %{http_code}\n' -u $U -H 'Content-Type: application/json' -d '{"title":"   "}' "$B?groupId=$G"
```

```
Title must not be blank HTTP 400
```

Telo je obyčajný text, nie JSON.

### 4. Prepni stav (PATCH)

```bash
curl -s -w ' HTTP %{http_code}\n' -u $U -X PATCH "$B/1/toggle"
```

```
{"groupId":"20117","id":"1","title":"Kúpiť mlieko","done":true,"createDate":"2026-10-04T07:10:00.889Z"} HTTP 200
```

Neexistujúce ID:

```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' -u $U -X PATCH "$B/999/toggle"
```

```
HTTP 404
```

### 5. Filter `done`

```bash
curl -s -w ' HTTP %{http_code}\n' -u $U "$B?groupId=$G&done=true"
```

```
[{"groupId":"20117","id":"1","title":"Kúpiť mlieko","done":true,"createDate":"2026-10-04T07:10:00.889Z"}] HTTP 200
```

> **Chyba v deme:** filter `done` volá `getTasksByDone(done)`, ktorý **ignoruje `groupId`** a vráti úlohy zo všetkých sites. Bez filtra sa volá `getGroupTasks(groupId, ...)`. V jednom site to nevidno, ale je to presne typ chyby (únik dát medzi tenantmi), ktorý treba pri migrácii hľadať.

### 6. Nevalidný JSON: 200 s prázdnym telom (!)

```bash
curl -s -i -u $U -H 'Content-Type: application/json' -d 'nie json' "$B?groupId=$G" | head -1
```

```
HTTP/1.1 200
```

Odpoveď má `Content-Length: 0`, ale v logu Liferay je:

```
ERROR [AuthVerifierFilter:55] org.apache.cxf.interceptor.Fault: org.json.JSONException: A JSONObject text must begin with '{' at 1 [character 2 line 1]
```

Nezachytená výnimka v JAX-RS metóde sa v Liferay ku klientovi dostala ako **`200` s prázdnym telom**. Klient si myslí, že všetko prebehlo v poriadku. Spring Boot pri nevalidnom JSON vráti `400` automaticky (`HttpMessageNotReadableException`) a demo to navyše balí do `ProblemDetail`.

### 7. Vstavané headless API

Liferay má REST API aj pre vlastné dáta. Interaktívny prehliadač (podobný Swagger UI) je na:

<http://localhost:8080/o/api> (prihlásený v prehliadači)

Príklad: aktuálny používateľ (použité aj v [kapitole 03](03_spustenie_liferay.md)):

```bash
curl -s -u $U http://localhost:8080/o/headless-admin-user/v1.0/my-user-account | grep -E '"(emailAddress|alternateName)"'
```

```
  "alternateName" : "test",
  "emailAddress" : "test@liferay.com",
```

Pri migrácii je užitočné vedieť, že **dáta z Liferay sa dajú čítať aj cez headless API**, nielen priamo z DB. To sa hodí pre postupnú migráciu (strangler fig).

---

## Kód z repa

[`TaskRestApplication.java`](../liferay-workspace/modules/task-rest/src/main/java/com/example/task/rest/application/TaskRestApplication.java) (skrátené):

```java
@Component(
	property = {
		JaxrsWhiteboardConstants.JAX_RS_APPLICATION_BASE + "=/tasks",   // → /o/tasks
		JaxrsWhiteboardConstants.JAX_RS_NAME + "=Tasks.Rest",
		"auth.verifier.guest.allowed=false",   // hosť nesmie → 403
		"liferay.oauth2=false"                 // bez OAuth2 scope kontroly, stačí Basic Auth (len demo!)
	},
	service = Application.class
)
public class TaskRestApplication extends Application {

	@Override
	public Set<Object> getSingletons() {
		return Collections.singleton(this);    // trieda je aplikácia aj resource naraz
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getTasks(
		@QueryParam("groupId") long groupId, @QueryParam("done") Boolean done,
		@DefaultValue("20") @QueryParam("size") int size) {
		JSONArray jsonArray = _jsonFactory.createJSONArray();
		(done == null ?
			_taskLocalService.getGroupTasks(groupId, 0, size) :
			_taskLocalService.getTasksByDone(done)                 // ← ignoruje groupId
		).forEach(task -> jsonArray.put(_toJSON(task)));
		return jsonArray.toString();
	}

	@Consumes(MediaType.APPLICATION_JSON)
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response addTask(
			@QueryParam("groupId") long groupId, String body,
			@Context HttpServletRequest httpServletRequest) throws Exception {
		JSONObject jsonObject = _jsonFactory.createJSONObject(body);    // ručné parsovanie
		ServiceContext serviceContext = ServiceContextFactory.getInstance(httpServletRequest);
		serviceContext.setScopeGroupId(groupId);
		try {
			Task task = _taskLocalService.addTask(jsonObject.getString("title"), serviceContext);
			return Response.status(Response.Status.CREATED).entity(_toJSON(task).toString()).build();
		}
		catch (TaskTitleException taskTitleException) {
			return Response.status(Response.Status.BAD_REQUEST).entity(taskTitleException.getMessage()).build();
		}
	}

	@PATCH
	@Path("/{taskId}/toggle")
	@Produces(MediaType.APPLICATION_JSON)
	public Response toggle(@PathParam("taskId") long taskId) throws PortalException { ... }   // 404 pri NoSuchTaskException

	@Reference private JSONFactory _jsonFactory;
	@Reference private TaskLocalService _taskLocalService;     // ten istý servis ako portlet
}
```

Čo si všimnúť:

- **REST aj portlet volajú ten istý `TaskLocalService`.** Business logika je na jednom mieste. Presne takto by mala vyzerať aj aplikácia pred migráciou, inak sa logika ťažko vyberá.
- **JSON sa skladá ručne** (`JSONUtil.put(...)`), žiadne DTO a žiadny Jackson. `long` sa v Liferay JSON serializuje ako reťazec (`"id":"3"`).
- **Chyby sa riešia v každej metóde zvlášť** (`try/catch` → `Response.status(...)`). Nie je tu globálny exception handler, preto nevalidný JSON skončil ako `200`.
- **`ServiceContextFactory.getInstance(httpServletRequest)`** z prihláseného používateľa vytvorí kontext (userId, companyId).
- **Autentifikácia:** Liferay `AuthVerifierFilter` pred aplikáciou overí Basic Auth (alebo session cookie, OAuth2 token). `auth.verifier.guest.allowed=false` zakáže anonymný prístup.

---

## Spring Boot ekvivalent

[`TaskController.java`](../spring-boot-tasks/src/main/java/com/example/tasks/task/TaskController.java) (skrátené):

```java
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	@GetMapping
	public PageResponse<TaskResponse> list(
		@RequestParam(required = false) Boolean done,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(required = false) Integer size) { ... }

	@GetMapping("/{id}")
	public TaskResponse get(@PathVariable long id) { ... }

	@PostMapping
	public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) { ... }   // 201 + Location

	@PutMapping("/{id}")
	public TaskResponse update(@PathVariable long id, @Valid @RequestBody UpdateTaskRequest request) { ... }

	@PatchMapping("/{id}/toggle")
	public TaskResponse toggle(@PathVariable long id) { ... }

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable long id) { ... }
}
```

| JAX-RS v Liferay (`/o/tasks`) | Spring Boot (`/api/tasks`) |
|---|---|
| `@Component(service = Application.class)` + `application.base` | `@RestController` + `@RequestMapping` |
| `@GET`, `@POST`, `@PATCH`, `@Path` | `@GetMapping`, `@PostMapping`, `@PatchMapping` |
| `@QueryParam`, `@PathParam`, `@DefaultValue` | `@RequestParam`, `@PathVariable`, `defaultValue` |
| `String body` + `JSONFactory` (ručne) | `@RequestBody CreateTaskRequest` (record, Jackson) |
| validácia v servise → `TaskTitleException` → text `400` | `@Valid` + `@NotBlank` → `ProblemDetail` JSON `400` |
| nevalidný JSON → **`200` prázdne telo** | nevalidný JSON → `400` |
| `NoSuchTaskException` → ručne `404` | `TaskNotFoundException` → `GlobalExceptionHandler` → `404 ProblemDetail` |
| `JSONUtil.put(...)`, `long` ako reťazec | DTO `TaskResponse` (record), `long` ako číslo |
| `groupId` v URL, `ServiceContext` | žiadny site, používateľ z JWT (`SecurityContext`) |
| `start`/`end`, `size` | `Pageable`, odpoveď `PageResponse` (obsah + metadáta stránky) |
| Basic Auth / session / OAuth2 cez `AuthVerifierFilter` | Spring Security, OAuth2 resource server (JWT z Keycloaku) |
| Prehliadač API `/o/api` (len headless API) | Swagger UI `/swagger-ui.html` (springdoc), aj pre vlastné API |

**Pozor na zmenu kontraktu pri migrácii:** `"id":"3"` (reťazec) → `"id":3` (číslo), iné URL (`/o/tasks` → `/api/tasks`), iný formát chýb (text → `ProblemDetail`), stránkovanie s obálkou. Ak API používajú externí klienti, treba buď zachovať kompatibilitu (adaptér, verzia `v1`), alebo koordinovať zmenu. Viac v [kapitole 12](12_migracna_strategia.md).

---

## Časté chyby

| Príznak | Príčina | Riešenie |
|---|---|---|
| `403` aj s menom a heslom | Zlé heslo, alebo aplikácia vyžaduje OAuth2 scope | Over heslo. Pre demo `liferay.oauth2=false`, v reále OAuth2 aplikácia v Control Paneli |
| `404` na `/o/tasks` | Bundle `task-rest` nebeží | `./gogo.sh "lb task-rest"`, `scr:info com.example.task.rest.application.TaskRestApplication` |
| Prázdne pole `[]` | Chýba alebo je zlý `groupId` | Pridaj `?groupId=...` (ID site) |
| Diakritika `K�pit` v DB | `curl -d` v Git Bash na Windows neposlal UTF-8 | JSON do súboru + `--data-binary @task.json` |
| `200` s prázdnym telom | Nezachytená výnimka v resource metóde | Pozri log Liferay. V kóde chytať výnimky alebo pridať JAX-RS `ExceptionMapper` |
| `415 Unsupported Media Type` pri POST | Chýba `Content-Type: application/json` | Pridaj hlavičku |

---

## Otázky na pohovor

**Aké možnosti REST API má Liferay?**
Vstavané headless API (`/o/headless-*`) pre dáta portálu, REST Builder na generovanie vlastného API z OpenAPI, ručné JAX-RS Whiteboard aplikácie ako OSGi komponenty a staršie JSON Web Services (`/api/jsonws`) z remote servisov Service Buildera.

**Čo je REST Builder a kedy by si ho použil?**
Generátor, ktorý z OpenAPI YAML vytvorí JAX-RS resources, DTO, GraphQL endpointy, stránkovanie, filtrovanie, triedenie a batch operácie v štýle Liferay headless API. Hodí sa pre verejné, dlhodobo udržiavané API v Liferay. Pre jednoduché interné API stačí JAX-RS komponent.

**Ako funguje JAX-RS Whiteboard?**
JAX-RS aplikácia (`Application`) alebo resource sa zaregistruje ako OSGi služba s vlastnosťami `osgi.jaxrs.application.base` a `osgi.jaxrs.name`. Whiteboard runtime ju nájde a sprístupní pod `/o/<base>`. Nič sa nevolá ručne.

**Ako sa v Liferay REST API autentifikuje?**
Cez auth verifiery: Basic Auth, session cookie (s CSRF tokenom), OAuth2 (Liferay ako authorization server) alebo portal session. Pre headless API je štandard OAuth2 so scope. V Spring Boot to nahradí Spring Security, typicky OAuth2 resource server s JWT z Keycloaku.

**Na čo si dať pozor pri prepise Liferay REST API do Spring Boot?**
Zmena kontraktu: URL, typy (`long` ako reťazec vs. číslo), formát chýb, stránkovanie, dátumy (UTC). Autentifikácia (Basic/OAuth2 v Liferay → JWT). Multi-tenancy (`groupId`, `companyId`) a kontroly oprávnení, ktoré mohli byť skryté v remote servisoch.

---

**Ďalej:** [09 – Testovanie v Liferay](09_testovanie_liferay.md)
