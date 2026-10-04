# 07 – Portlet (MVCPortlet)

## Čo sa naučíš

- Čo sú tri fázy portletu (render, action, resource) a kedy sa ktorá volá
- Ako `MVCPortlet` deleguje prácu na `MVC*Command` komponenty
- Čo sú portletové URL, `p_auth`, namespace a portlet módy (VIEW, EDIT)
- Čo sú PortletPreferences a kde sú uložené
- Ako sa portlet prepíše na REST API + frontend

**Predpoklad:** Liferay beží, moduly sú nasadené a v DB je pár úloh ([kapitola 06](06_service_builder.md)).

---

## Teória v skratke

### Tri fázy portletu

Na jednej stránke môže byť veľa portletov. Portál preto rozlišuje, **čo** sa od portletu chce:

```
 Používateľ otvorí stránku                Používateľ odošle formulár           JavaScript si pýta dáta
 (GET /web/guest/task-demo)               (POST ...p_p_lifecycle=1...)          (GET ...p_p_lifecycle=2...)
            |                                         |                                  |
            v                                         v                                  v
 RENDER všetkých portletov             ACTION len pre kliknutý portlet       RESOURCE len pre jeden portlet
 na stránke                            (zmení dáta: insert, update)          (vráti JSON / súbor)
            |                                         |                                  |
            |                                         v                                  |
            |                          RENDER všetkých portletov                         |
            v                                         v                                  v
 HTML celej stránky                    HTML celej stránky                    len JSON, stránka sa nemení
```

| Fáza | `p_p_lifecycle` | Kedy | Smie meniť dáta? | Náš command | Spring ekvivalent |
|---|---|---|---|---|---|
| **Render** | `0` | pri každom zobrazení stránky, pre **všetky** portlety | nie (musí byť idempotentná) | `ViewTasksMVCRenderCommand` | `GET` vracajúci HTML/JSON |
| **Action** | `1` | po odoslaní formulára, len pre jeden portlet | áno | `AddTask…`, `ToggleTask…`, `SavePreferences…` | `POST` / `PATCH` |
| **Resource** | `2` | AJAX, sťahovanie súboru | áno, ale typicky len číta | `TasksJsonMVCResourceCommand` | `GET` vracajúci JSON |

Pravidlo: **action mení, render zobrazuje**. Render sa volá stále dokola (aj keď používateľ klikne v *inom* portlete na tej istej stránke), preto nesmie nič meniť.

### MVCPortlet a commandy

Portlet [`TaskPortlet`](../liferay-workspace/modules/task-web/src/main/java/com/example/task/web/portlet/TaskPortlet.java) je prázdna trieda `extends MVCPortlet`. Liferay `MVCPortlet` funguje ako **dispatcher**: podľa parametra v URL nájde OSGi komponent, ktorý má rovnaké `javax.portlet.name` a `mvc.command.name`:

```
URL parameter                                             → komponent
--------------------------------------------------------------------------------------------------------
(žiadny) alebo mvcRenderCommandName=/task/view            → MVCRenderCommand   mvc.command.name=/  alebo /task/view
javax.portlet.action=/task/add                            → MVCActionCommand   mvc.command.name=/task/add
javax.portlet.action=/task/toggle                         → MVCActionCommand   mvc.command.name=/task/toggle
javax.portlet.action=/task/save_preferences               → MVCActionCommand   mvc.command.name=/task/save_preferences
p_p_resource_id=/task/json                                → MVCResourceCommand mvc.command.name=/task/json
```

Je to podobné ako Spring `DispatcherServlet` a `@RequestMapping`, len mapovanie nie je podľa cesty URL, ale podľa parametra a vlastností OSGi komponentu.

### Portletové URL

Portlet **nikdy neskladá URL ručne**, generuje ich cez taglib (`<portlet:actionURL>`, `<portlet:renderURL>`, `<portlet:resourceURL>`). Takto vyzerá vygenerovaná action URL z demo stránky:

```
http://localhost:8080/task-demo
  ?p_p_id=com_example_task_web_TaskPortlet                         ← ktorý portlet
  &p_p_lifecycle=1                                                 ← 1 = action
  &p_p_state=normal                                                ← normal / maximized / minimized
  &p_p_mode=view                                                   ← portlet mód
  &_com_example_task_web_TaskPortlet_javax.portlet.action=%2Ftask%2Fadd   ← ktorý command
  &p_auth=ffahfPoC                                                 ← CSRF token
```

- **Namespace** `_com_example_task_web_TaskPortlet_` je prefix všetkých parametrov a HTML `id` portletu. Na stránke môže byť viac portletov a ich polia `title` by sa inak pobili. Formulár preto posiela `_com_example_task_web_TaskPortlet_title`, nie `title`. `ParamUtil.getString(actionRequest, "title")` si prefix odstráni sám.
- **`p_auth`** je CSRF token. Action bez neho Liferay odmietne (overené nižšie).

### Portlet módy a PortletPreferences

- **VIEW** – bežné zobrazenie (`view.jsp`).
- **EDIT** – nastavenia **tejto inštancie** portletu (`edit.jsp`). V UI cez tri bodky v hlavičke portletu → *Preferences*. Prístup má len používateľ s oprávnením.

Nastavenia z EDIT módu sa ukladajú do **PortletPreferences**: kľúč-hodnota, ktorú Liferay ukladá do DB pre konkrétny portlet na konkrétnej stránke. V deme je to `pageSize`, koľko úloh zobraziť.

---

## Krok po kroku

### 1. Vytvor stránku s portletom

Portlet treba pridať na stránku. V UI by si to urobil cez *Site Builder → Pages → Add Page* a pretiahnutie widgetu „Tasks“ (kategória *Sample*). Skript to urobí za teba cez Liferay JSON web služby:

```bash
cd liferay-workspace
./create-demo-page.sh
```

```
Guest groupId=20117
Hotovo: http://localhost:8080/web/guest/task-demo
```

Ak stránka už existuje, skript vypíše `Stránku se nepodařilo vytvořit (už existuje? ...)`. To je v poriadku, pokračuj.

### 2. Render fáza: otvor stránku

V prehliadači otvor <http://localhost:8080/web/guest/task-demo>. Uvidíš:

```
Title [____________]  [Add Task]

5 task(s), showing max 10 · refresh (render URL)

ID   Title              Done
102  Guest task          ✔    [Toggle]
101  Zavolať mame             [Toggle]
3    Kúpiť chlieb             [Toggle]
2    Napísať poznámky         [Toggle]
1    Kúpiť mlieko        ✔    [Toggle]

[Load JSON (resource request)]
```

(Konkrétne úlohy závisia od toho, čo si vytvoril v kapitole 06.)

Overenie z terminálu, stránka je verejná, takže funguje aj bez prihlásenia:

```bash
curl -s http://localhost:8080/web/guest/task-demo | grep -oE '[0-9]+ task\(s\), showing max [0-9]+'
```

```
5 task(s), showing max 10
```

Čo sa stalo: portál zavolal render všetkých portletov na stránke. Pre náš portlet `MVCPortlet` nenašiel žiadny command parameter, tak použil `ViewTasksMVCRenderCommand` (`mvc.command.name=/`). Ten načítal úlohy a vrátil `/view.jsp`.

### 3. Action fáza: pridaj úlohu

Do poľa *Title* napíš text a klikni na **Add Task**. Stránka sa obnoví a úloha je v tabuľke.

Prehliadač poslal POST na action URL (`p_p_lifecycle=1`, `javax.portlet.action=/task/add`, `p_auth=...`). Liferay:
1. zavolal `AddTaskMVCActionCommand.doProcessAction()` → `TaskLocalService.addTask()` → INSERT,
2. **v tej istej HTTP odpovedi** vyrenderoval celú stránku znova (render všetkých portletov).

> **Overené zistenie:** pri POST cez `curl` vrátil Liferay po action rovno `HTTP 200` s HTML stránky, nie redirect (`302`). Preto pri F5 po odoslaní formulára prehliadač ponúkne „odoslať formulár znova“. Klasický PRG (Post/Redirect/Get) by vyžadoval poslať v action parameter `redirect` alebo zavolať `sendRedirect`. Demo to nerobí.

**Prázdny title:** klikni na *Add Task* bez textu. Zobrazí sa červená hláška **Task title is required.** Ako to funguje:
- `TaskLocalServiceImpl` vyhodí `TaskTitleException`,
- `AddTaskMVCActionCommand` ju chytí a zavolá `SessionErrors.add(actionRequest, TaskTitleException.class)`,
- `view.jsp` ju zobrazí cez `<liferay-ui:error exception="<%= TaskTitleException.class %>" message="task-title-is-required" />`, text je z `Language.properties`.

**Toggle:** klikni na **Toggle** pri úlohe. Prepne sa ✔. Je to ďalšia action (`/task/toggle` s parametrom `taskId`).

### 4. Resource fáza: načítaj JSON

Klikni na **Load JSON (resource request)**. Pod tlačidlom sa zobrazí JSON a **stránka sa neobnoví**. JavaScript v `view.jsp` zavolal `Liferay.Util.fetch(resourceURL)`.

Z terminálu (resource URL skopírovaná z HTML stránky):

```bash
curl -s 'http://localhost:8080/task-demo?p_p_id=com_example_task_web_TaskPortlet&p_p_lifecycle=2&p_p_state=normal&p_p_mode=view&p_p_resource_id=%2Ftask%2Fjson&p_p_cacheability=cacheLevelPage'
```

Výstup (tu ešte pred pridaním úlohy 102):

```json
[{"id":"101","title":"Zavolať mame","done":false,"createDate":"2026-10-04T07:11:41.422Z"},{"id":"3","title":"Kúpiť chlieb","done":false,"createDate":"2026-10-04T07:10:12.908Z"},{"id":"2","title":"Napísať poznámky","done":false,"createDate":"2026-10-04T07:10:01.815Z"},{"id":"1","title":"Kúpiť mlieko","done":true,"createDate":"2026-10-04T07:10:00.889Z"}]
```

Content-Type je `application/json;charset=UTF-8`. Resource fáza je **najbližšia vec k REST endpointu**, akú portlet má. Všimni si aj `"id":"101"`: Liferay JSON serializuje `long` ako **reťazec** (kvôli presnosti čísel v JavaScripte), Jackson v Spring Boot ako číslo.

### 5. EDIT mód a PortletPreferences

1. Prihlás sa (`test@liferay.com` / `test`) a otvor stránku `/web/guest/task-demo`.
2. V hlavičke portletu *Tasks* klikni na **tri bodky (⋮) → Preferences**. Toto je EDIT mód (`p_p_mode=edit`), zobrazí sa `edit.jsp` s poľom **Page Size** (hodnota 10).
3. Zadaj `2` a ulož.
4. Portlet sa prepne späť do VIEW (`actionResponse.setPortletMode(PortletMode.VIEW)`) a ukazuje **`5 task(s), showing max 2`**, v tabuľke sú len 2 riadky.

Nastavenie platí pre **všetkých** návštevníkov tejto stránky (aj neprihlásených), lebo patrí inštancii portletu na stránke, nie používateľovi.

Kde je uložené:

```bash
docker compose exec -T postgres psql -U liferay -d lportal -c "
  select p.portletid, p.ownertype, p.plid, v.name, v.smallvalue
  from portletpreferences p
  join portletpreferencevalue v on v.portletpreferencesid = p.portletpreferencesid
  where p.portletid like 'com_example%'"
```

```
            portletid             | ownertype | plid |   name   | smallvalue
----------------------------------+-----------+------+----------+------------
 com_example_task_web_TaskPortlet |         3 |   14 | pageSize | 2
```

- `plid` = ID stránky (Portal Layout ID).
- `ownertype 3` = preferencie patria stránke (layout), nie používateľovi ani site.

Vráť hodnotu späť na **10**, nech ďalšie kapitoly sedia.

**Ako hosť do EDIT módu nesmieš.** Bez prihlásenia URL s `p_p_mode=edit` vráti:

```
You do not have the roles required to access this portlet.
```

### 6. CSRF ochrana (`p_auth`)

Pokus o action **bez** `p_auth` (aj s platnou prihlásenou session) skončí `HTTP 403` a v logu:

```
WARN [SecurityPortletContainerWrapper:395] User 20123 is not allowed to access URL http://localhost:8080/web/guest/task-demo
  and portlet com_example_task_web_TaskPortlet: User 20123 did not provide a valid CSRF token for com.liferay.portlet.SecurityPortletContainerWrapper
```

Preto sa URL vždy generujú cez taglib, ktorý token doplní.

### 7. Bezpečnostná diera v deme (dobré na pohovor)

Pri overovaní som poslal action `/task/add` ako **neprihlásený hosť** (s `p_auth` z verejnej stránky) a úloha sa vytvorila:

```
 taskid |   title    | userid
--------+------------+--------
    102 | Guest task |  20096      ← 20096 = default (guest) user, nie test@liferay.com
```

Ani `ToggleTaskMVCActionCommand` nič nekontroluje (v kóde je komentár „Demo bez kontroly oprávnení“). V reálnej aplikácii by action command overil oprávnenie, napríklad cez `ModelResourcePermission` / `PortletResourcePermission` a definíciu v `resource-actions/*.xml`. To je presne vec, ktorú treba pri migrácii nájsť a preniesť do Spring Security ([kapitola 11](11_migracia_dat_a_security.md)).

---

## Kód z repa

### Render command: [`ViewTasksMVCRenderCommand.java`](../liferay-workspace/modules/task-web/src/main/java/com/example/task/web/command/ViewTasksMVCRenderCommand.java)

```java
@Component(
	property = {
		"javax.portlet.name=" + TaskPortletKeys.TASK, "mvc.command.name=/",
		"mvc.command.name=" + TaskPortletKeys.MVC_RENDER_VIEW
	},
	service = MVCRenderCommand.class
)
public class ViewTasksMVCRenderCommand implements MVCRenderCommand {

	@Override
	public String render(RenderRequest renderRequest, RenderResponse renderResponse) {
		// "/" sa použije vo VŠETKÝCH módoch, edit-template by sa inak neuplatnil
		if (PortletMode.EDIT.equals(renderRequest.getPortletMode())) {
			return "/edit.jsp";
		}

		ThemeDisplay themeDisplay = (ThemeDisplay)renderRequest.getAttribute(WebKeys.THEME_DISPLAY);
		int pageSize = getPageSize(renderRequest.getPreferences());

		List<Task> tasks = _taskLocalService.getGroupTasks(themeDisplay.getScopeGroupId(), 0, pageSize);

		renderRequest.setAttribute("tasks", tasks);                      // ≈ model.addAttribute(...)
		renderRequest.setAttribute("tasksCount", _taskLocalService.getGroupTasksCount(...));
		renderRequest.setAttribute("pageSize", pageSize);

		return "/view.jsp";                                               // ≈ názov view
	}
}
```

- **`ThemeDisplay`** je ďalší Liferay „kontext“: aktuálny site (`getScopeGroupId()`), používateľ, jazyk, stránka.
- **`getScopeGroupId()`**: portlet zobrazuje úlohy **aktuálneho site**. Na inom site by ukázal iné úlohy.
- Jeden komponent môže mať viac `mvc.command.name` (tu `/` aj `/task/view`).

### Action command: [`AddTaskMVCActionCommand.java`](../liferay-workspace/modules/task-web/src/main/java/com/example/task/web/command/AddTaskMVCActionCommand.java)

```java
@Component(
	property = {
		"javax.portlet.name=" + TaskPortletKeys.TASK,
		"mvc.command.name=" + TaskPortletKeys.MVC_ACTION_ADD      // "/task/add"
	},
	service = MVCActionCommand.class
)
public class AddTaskMVCActionCommand extends BaseMVCActionCommand {

	@Override
	protected void doProcessAction(ActionRequest actionRequest, ActionResponse actionResponse) throws Exception {
		String title = ParamUtil.getString(actionRequest, "title");

		ServiceContext serviceContext = ServiceContextFactory.getInstance(Task.class.getName(), actionRequest);

		try {
			_taskLocalService.addTask(title, serviceContext);
		}
		catch (TaskTitleException taskTitleException) {
			SessionErrors.add(actionRequest, taskTitleException.getClass());   // ≈ flash attribute
		}
	}

	@Reference
	private TaskLocalService _taskLocalService;
}
```

### Resource command: [`TasksJsonMVCResourceCommand.java`](../liferay-workspace/modules/task-web/src/main/java/com/example/task/web/command/TasksJsonMVCResourceCommand.java)

Zostaví `JSONArray` cez Liferay `JSONFactory`/`JSONUtil` a zapíše ho `JSONPortletResponseUtil.writeJSON(...)`. Žiadne JSP.

### Preferences: [`SavePreferencesMVCActionCommand.java`](../liferay-workspace/modules/task-web/src/main/java/com/example/task/web/command/SavePreferencesMVCActionCommand.java)

```java
PortletPreferences portletPreferences = actionRequest.getPreferences();
portletPreferences.setValue("pageSize", String.valueOf(Math.max(1, pageSize)));
portletPreferences.store();                              // zápis do DB

actionResponse.setPortletMode(PortletMode.VIEW);         // späť do VIEW
```

### JSP: [`view.jsp`](../liferay-workspace/modules/task-web/src/main/resources/META-INF/resources/view.jsp)

```jsp
<portlet:actionURL name="<%= TaskPortletKeys.MVC_ACTION_ADD %>" var="addTaskURL" />

<portlet:renderURL var="refreshURL">
	<portlet:param name="mvcRenderCommandName" value="<%= TaskPortletKeys.MVC_RENDER_VIEW %>" />
</portlet:renderURL>

<portlet:resourceURL id="<%= TaskPortletKeys.MVC_RESOURCE_TASKS_JSON %>" var="tasksJsonURL" />

<aui:form action="${addTaskURL}" method="post" name="fm">
	<aui:input label="title" name="title" type="text" />    <%-- name dostane namespace automaticky --%>
	<aui:button type="submit" value="add-task" />
</aui:form>

<button id="<portlet:namespace />loadJson" ...>             <%-- unikátne id na stránke --%>
```

- `<portlet:...URL>` generujú URL s `p_p_id`, `p_p_lifecycle`, namespace a `p_auth`.
- `<aui:form>`, `<aui:input>` sú Liferay taglib, ktoré pridajú namespace a Liferay CSS.
- Texty (`add-task`, `title`) sú kľúče z [`Language.properties`](../liferay-workspace/modules/task-web/src/main/resources/content/Language.properties) (i18n).

### Konfigurácia portletu: vlastnosti `@Component`

```java
"javax.portlet.name=" + TaskPortletKeys.TASK,              // com_example_task_web_TaskPortlet
"javax.portlet.portlet-mode=text/html;view,edit",          // povolené módy
"javax.portlet.init-param.view-template=/view.jsp",
"javax.portlet.security-role-ref=power-user,user",         // roly, ktoré portlet pozná
"com.liferay.portlet.display-category=category.sample",    // kategória v ponuke widgetov
"com.liferay.portlet.instanceable=false",                  // na stránke max. 1×
```

V starších verziách Liferay bolo toto v `portlet.xml` a `liferay-portlet.xml`.

---

## Spring Boot ekvivalent

Portlet sa pri migrácii **rozpadne na dve časti**: REST API (backend) a frontend (Angular), ktorý si stránku skladá sám.

| Portlet (Liferay) | Spring Boot REST ([`TaskController.java`](../spring-boot-tasks/src/main/java/com/example/tasks/task/TaskController.java)) |
|---|---|
| Render `/` → `ViewTasksMVCRenderCommand` + `view.jsp` | `GET /api/tasks?page=&size=&done=` → JSON, HTML robí Angular |
| Action `/task/add` | `POST /api/tasks` s telom `{"title": "..."}` → `201 Created` |
| Action `/task/toggle` + `taskId` | `PATCH /api/tasks/{id}/toggle` |
| Resource `/task/json` | to isté ako `GET /api/tasks` |
| – | `GET /api/tasks/{id}`, `PUT /api/tasks/{id}`, `DELETE /api/tasks/{id}` (CRUD navyše) |
| `SessionErrors` + `<liferay-ui:error>` | `400 Bad Request` + `ProblemDetail` JSON (`GlobalExceptionHandler`), hlášku zobrazí frontend |
| `ParamUtil.getString(request, "title")` | `@Valid @RequestBody CreateTaskRequest` (record + `@NotBlank`) |
| `ServiceContext` / `ThemeDisplay` (user, site) | `SecurityContext` / JWT (`@AuthenticationPrincipal`) |
| `p_auth` CSRF token | bezstavové API s Bearer tokenom (JWT) → CSRF sa pri ňom typicky vypína |
| Namespace `_com_example_..._` | netreba, API nemá „stránku s viacerými portletmi“ |
| Portlet session, stav medzi requestmi | bezstavové API, stav drží frontend |
| **PortletPreferences** `pageSize` (per inštancia na stránke) | `tasks.default-page-size` v `application.yml` → `@ConfigurationProperties TaskProperties` (globálne) + parameter `size` v requeste |
| EDIT mód s oprávnením | admin obrazovka vo frontende + endpoint chránený rolou, alebo konfigurácia |

Konfigurácia, ktorá nahrádza PortletPreferences ([`application.yml`](../spring-boot-tasks/src/main/resources/application.yml) a [`TaskProperties.java`](../spring-boot-tasks/src/main/java/com/example/tasks/config/TaskProperties.java)):

```yaml
# Nahrádza PortletPreferences (pageSize z EDIT módu portletu)
tasks:
  default-page-size: 10
  max-page-size: 100
```

```java
@ConfigurationProperties("tasks")
public record TaskProperties(
		@DefaultValue("10") @Min(1) int defaultPageSize,
		@DefaultValue("100") @Min(1) int maxPageSize, ...) {

	public int resolvePageSize(Integer requested) {
		if (requested == null) return defaultPageSize;
		return Math.clamp(requested, 1, maxPageSize);
	}
}
```

**Pozor na sémantický rozdiel:** PortletPreferences sú **per inštancia portletu na stránke** a menia sa za behu cez UI. `application.yml` je **globálny** a mení sa nasadením. Ak používatelia v pôvodnej aplikácii naozaj menia nastavenia v EDIT móde, v Spring Boot na to treba tabuľku nastavení + endpoint, nie `application.yml`.

---

## Časté chyby

| Príznak | Príčina | Riešenie |
|---|---|---|
| Portlet nie je v ponuke widgetov | Komponent portletu nie je aktívny | `./gogo.sh "scr:info com.example.task.web.portlet.TaskPortlet"` |
| Klik na tlačidlo nič neurobí, `HTTP 403`, v logu `did not provide a valid CSRF token` | URL bez `p_auth` (ručne poskladaná) | Generovať URL cez `<portlet:actionURL>` |
| Action command sa nezavolá | `mvc.command.name` v komponente ≠ `name` v `<portlet:actionURL>`, alebo iné `javax.portlet.name` | Zjednotiť cez konštanty (`TaskPortletKeys`) |
| Hodnota z formulára je `null`/prázdna | Pole bez namespace (obyčajný `<input name="title">`) | `<aui:input>` alebo `name="<portlet:namespace />title"` |
| EDIT mód zobrazuje VIEW | Render command s `mvc.command.name=/` sa použije vo všetkých módoch | Kontrola `PortletMode.EDIT` v render commande (ako v deme) |
| F5 po pridaní úlohy ponúka „odoslať znova“ | Po action nie je redirect | Parameter `redirect` vo formulári alebo `sendRedirect` v action |
| Zmena v render metóde dát (napr. počítadlo zobrazení) sa deje viackrát | Render sa volá pri každom zobrazení stránky, aj pri kliknutí v inom portlete | Meniť dáta len v action |
| `You do not have the roles required to access this portlet.` | Používateľ nemá oprávnenie na mód (EDIT) alebo portlet | Prihlásiť sa ako admin alebo upraviť oprávnenia |

---

## Otázky na pohovor

**Aký je rozdiel medzi action a render fázou?**
Action spracuje vstup a mení stav. Volá sa len pre portlet, s ktorým používateľ interagoval. Render generuje HTML, je idempotentný a portál ho volá pre všetky portlety na stránke pri každom zobrazení. Po action vždy nasleduje render.

**Na čo je resource fáza?**
Na vrátenie ľubovoľných dát mimo renderu stránky: JSON pre AJAX, sťahovanie súborov, obrázky. Je to najbližšia obdoba REST endpointu v portletovom svete.

**Čo je MVCPortlet a MVC command?**
Liferay implementácia portletu, ktorá deleguje render, action a resource na samostatné OSGi komponenty (`MVCRenderCommand`, `MVCActionCommand`, `MVCResourceCommand`) podľa `mvc.command.name`. Každá akcia je jedna malá trieda.

**Čo sú PortletPreferences?**
Konfigurácia inštancie portletu (typicky per stránka) uložená v DB, upravovaná v EDIT/konfiguračnom móde. Pri migrácii sa mapujú na konfiguráciu aplikácie (`@ConfigurationProperties`) alebo na tabuľku nastavení, ak ich používatelia menia za behu.

**Ako by si migroval portlet na Spring Boot + Angular?**
Každý render, action a resource command zmapujem na REST endpoint (render/resource → `GET`, action → `POST`/`PATCH`/`DELETE`). Business logika z `*LocalService` ide do `@Service`, validácia do Bean Validation, chyby do `ProblemDetail`. `ServiceContext` nahradí Spring Security kontext a JSP prepíšem do Angular komponentov. Oprávnenia z action commandov a permission checkerov treba explicitne preniesť (`@PreAuthorize`). Počas prechodu môže portlet volať nové REST API (strangler fig, [kapitola 12](12_migracna_strategia.md)).

**Čo je namespace a `p_auth`?**
Namespace je unikátny prefix parametrov a HTML id portletu, aby sa viac portletov na stránke nebilo. `p_auth` je CSRF token, ktorý Liferay vyžaduje pri action requestoch.

---

**Ďalej:** [08 – REST v Liferay](08_rest_v_liferay.md)
