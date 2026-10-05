# Liferay → Spring Boot: demo projekt

Študijný projekt pred pohovorom. Rovnaká doména **Task** (`id, title, done, createDate`) je implementovaná dvakrát:

| Časť | Adresár | Čo ukazuje |
|---|---|---|
| 1 | [`liferay-workspace/`](liferay-workspace) | Liferay CE 7.4: Service Builder, OSGi DS (`@Component`/`@Reference`, `service.ranking`), MVCPortlet (render/action/resource, EDIT mód + PortletPreferences), JAX-RS REST, Docker, Gogo shell |
| 2 | [`spring-boot-tasks/`](spring-boot-tasks) | Spring Boot 4.1: JPA, Spring Data, service, REST controller, DTO, validácia, ProblemDetail, OpenAPI, Flyway migrácia z Liferay tabuľky, OAuth2 resource server (Keycloak), Testcontainers |

Obe časti sme naozaj zbuildili, spustili a otestovali (pozri [Overenie](#overenie-čo-bolo-reálne-otestované)).

---

## Overené verzie

| Komponent | Verzia | Zdroj overenia |
|---|---|---|
| Liferay Portal CE | **7.4.3.132-ga132** (release key `portal-7.4-ga132`, `promoted: true`) | `https://releases.liferay.com/releases.json`, Docker Hub `liferay/portal` tagy |
| Docker image | `liferay/portal:7.4.3.132-ga132` (v image je Azul Zulu **21.0.9**, `JAVA_VERSION=zulu21`) | `docker image inspect` + `java -version` v kontajneri |
| Java pre Liferay | JDK 17 a 21 sú certifikované od CE 7.4 GA120 / DXP 2024.Q2 | liferay.dev blog „Java evolution is here“ |
| Blade CLI | 8.0.2 | Liferay Nexus `com.liferay.blade.cli/maven-metadata.xml` |
| Liferay Workspace Gradle plugin | **17.1.11** (blade vygeneroval 17.1.5, zvýšené na najnovšie) | Liferay Nexus `com.liferay.gradle.plugins.workspace/maven-metadata.xml` |
| Gradle wrapper (workspace) | 8.9 | vygenerované blade |
| Service Builder | **1.0.496** (pinnuté!) | `Bundle-Version` v tagu `7.4.3.132-ga132` repozitára `liferay/liferay-portal` |
| PostgreSQL | 16 | – |
| Spring Boot | **4.1.1** (najnovšia stabilná; 4.2.0 je zatiaľ len M2) | Maven Central `spring-boot/maven-metadata.xml`, start.spring.io |
| Java pre Spring Boot | 21 | – |
| springdoc-openapi | 3.1.1 (3.1.x cieli na Spring Boot 4.1) | Maven Central + GitHub release notes |
| Z BOM Spring Bootu | Spring 7.0.9, Security 7.1.1, Hibernate 7.4.5, Flyway 12.4.0, Testcontainers 2.0.5, JUnit 6.0.3, Jackson 3.1.5 | `mvn dependency:list` |
| Unit testy v Liferay workspace | JUnit 5.14.4, Mockito 5.24.0 | Maven Central |

**Čo sa nedalo overiť:** presná dvojica Liferay + PostgreSQL 16 v oficiálnej compatibility matrix (dokument vyžaduje prihlásenie). Lokálne beží bez problémov.

---

## Predpoklady

- Docker Desktop (Liferay potrebuje ~3 GB RAM)
- JDK 21 (`java -version`)
- Maven netreba, projekt má `mvnw`. Gradle netreba, workspace má `gradlew`.
- Príkazy sú pre **Windows PowerShell 5.1** (Windows 11). Skripty `.ps1` potrebujú povolené lokálne skripty (raz):
  ```powershell
  Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
  ```
  Volaj `curl.exe`, nie `curl` (v PowerShell 5.1 je `curl` alias na `Invoke-WebRequest`). Pre Linux/macOS ostali pôvodné `gogo.sh` a `create-demo-page.sh`.
- **Globálne Gradle init skripty:** ak máš v `~/.gradle/init.d/` init skript, ktorý pridáva nedostupný interný repozitár, build workspace padne. Riešenie bez zásahu do globálnej konfigurácie je oddelený Gradle home:
  ```powershell
  $env:GRADLE_USER_HOME = "$HOME\.gradle-liferay-demo"
  ```

---

## Časť 1: Liferay workspace

### Štruktúra

```
liferay-workspace/
├── docker-compose.yml          Liferay CE GA132 + PostgreSQL 16
├── gogo.ps1 / gogo.sh          Gogo príkaz cez telnet v kontajneri
├── create-demo-page.ps1 / .sh  Vytvorí widget stránku s portletom (JSON WS)
├── build.gradle                Spoločná konfigurácia unit testov (JUnit 5 + Mockito)
├── settings.gradle             Liferay Workspace plugin 17.1.11
├── gradle.properties           liferay.workspace.product=portal-7.4-ga132
└── modules/
    ├── task/                   SERVICE BUILDER
    │   ├── task-api/           generované rozhranie (export)
    │   └── task-service/       service.xml + implementácia
    ├── greeting-api/           OSGi API bundle (Export-Package)
    ├── greeting-impl/          DS implementácia (ranking 100) + Gogo príkaz (konzument)
    ├── greeting-impl-alt/      druhá implementácia (ranking 200)
    ├── task-web/               MVCPortlet (render/action/resource, EDIT mód)
    └── task-rest/              JAX-RS whiteboard REST (/o/tasks)
```

### Spustenie

```powershell
cd liferay-workspace
$env:GRADLE_USER_HOME = "$HOME\.gradle-liferay-demo"   # viď Predpoklady

docker compose up -d                 # prvý štart cca 2 min, sleduj: docker compose logs -f liferay
                                     # hotovo pri "Server startup in [...] milliseconds"
.\gradlew test                       # unit testy (15)
.\gradlew deploy                     # JARy → .\bundles\osgi\modules (namountované do kontajnera = hot deploy)
.\gogo.ps1 "lb com.example"          # všetkých 7 bundlov musí byť Active
.\create-demo-page.ps1               # stránka http://localhost:8080/web/guest/task-demo s portletom
```

Prihlásenie: `test@liferay.com` / `test` (setup wizard je vypnutý cez env premenné `LIFERAY_*` v `docker-compose.yml`).

### 1.1 Service Builder

`modules/task/task-service/service.xml` definuje entitu `Task` (namespace `DEMO` → tabuľka `DEMO_Task`) s finderom `Done` (a `GroupId` pre stránkovanie podľa site).

```powershell
.\gradlew :modules:task:task-service:buildService
```

**Čo Service Builder vygeneruje a kam patrí vlastný kód:**

| Súbor | Modul | Pregeneruje sa? | Účel |
|---|---|---|---|
| `model/Task`, `TaskModel`, `TaskWrapper`, `TaskTable` | task-api | áno | rozhranie modelu |
| `service/TaskLocalService`, `TaskLocalServiceUtil`, `TaskLocalServiceWrapper` | task-api | áno | API služby (OSGi export) |
| `service/persistence/TaskPersistence`, `TaskUtil` | task-api | áno | DAO rozhranie (`findByDone`, `countByDone`...) |
| `exception/NoSuchTaskException`, `TaskTitleException` | task-api | áno | výnimky |
| `model/impl/TaskModelImpl`, `TaskBaseImpl`, `TaskCacheModel` | task-service | áno | Hibernate mapovanie, cache |
| `service/persistence/impl/TaskPersistenceImpl` | task-service | áno | SQL/Hibernate implementácia finderov |
| `service/base/TaskLocalServiceBaseImpl` | task-service | áno | CRUD + `@Reference` na persistence, counter |
| `META-INF/sql/tables.sql`, `indexes.sql`, `module-hbm.xml`, `portlet-model-hints.xml` | task-service | áno | DDL a metadáta |
| **`model/impl/TaskImpl`** | task-service | **nie** | vlastné metódy modelu |
| **`service/impl/TaskLocalServiceImpl`** | task-service | **nie** | **business logika** (`addTask`, `toggleDone`...) |

Po pridaní public metódy do `TaskLocalServiceImpl` treba znova spustiť `buildService`, aby sa metóda dostala do rozhrania `TaskLocalService` v task-api.

Vygenerovaná DDL (`tables.sql`), z ktorej vychádza Flyway migrácia v časti 2:
```sql
create table DEMO_Task (taskId LONG not null primary key, groupId LONG, companyId LONG,
    userId LONG, createDate DATE null, title VARCHAR(75) null, done BOOLEAN);
```
Na PostgreSQL z toho Liferay spraví `public.demo_task (taskid bigint, ..., createdate timestamp, title varchar(75), done boolean)`.

Kľúčové vlastnosti:
- ID nepochádza z DB sekvencie, generuje ho `counterLocalService.increment()` (tabuľka `Counter`).
- Transakcie riadi AOP proxy (`AopService`), takže každá public metóda je transakčná.
- `ServiceContext` nesie userId, companyId, scopeGroupId a oprávnenia.

### 1.2 OSGi demo (Declarative Services)

- `greeting-api`: rozhranie `GreetingService` (`@ProviderType`), verzia balíka v `packageinfo`.
- `greeting-impl`: `DefaultGreetingService` (`service.ranking=100`) + `GreetingCommand` (Gogo príkaz `greeting:hello`, `greeting:all`).
- `greeting-impl-alt`: `FriendlyGreetingService` (`service.ranking=200`).

**Manifesty** (vygeneroval bnd, overené cez `tar -xOf bundles\osgi\modules\<bundle>.jar META-INF/MANIFEST.MF`):
```
greeting-api:   Export-Package: com.example.greeting.api;version="1.0.0"
greeting-impl:  Import-Package: com.example.greeting.api;version="1.0",java.lang,...
                Service-Component: OSGI-INF/com.example.greeting.impl.DefaultGreetingService.xml,
                                   OSGI-INF/com.example.greeting.impl.GreetingCommand.xml
                Require-Capability: osgi.extender;filter:="(&(osgi.extender=osgi.component)(version>=1.4.0)...)"
```
Čisté bnd by dalo rozsah `[1.0,2)`, resp. `[1.0,1.1)` pre implementáciu ProviderType rozhrania. Liferay workspace hornú hranicu importu zámerne neuvádza (`version="1.0"` znamená ≥ 1.0).

**Dynamický výber služby** (reálny výstup):
```
PS> .\gogo.ps1 "greeting:hello Erich"
Ahoj Erich, vitaj v OSGi!                 ← FriendlyGreetingService (ranking 200)
PS> .\gogo.ps1 "greeting:all Erich"
FriendlyGreetingService: Ahoj Erich, vitaj v OSGi!
DefaultGreetingService: Hello, Erich!
PS> .\gogo.ps1 "stop <id greeting-impl-alt>"
PS> .\gogo.ps1 "greeting:hello Erich"
Hello, Erich!                             ← za behu prepnuté, bez reštartu
PS> .\gogo.ps1 "start <id>"  →  znova "Ahoj Erich..."
```
Funguje to vďaka `@Reference(policy = DYNAMIC, policyOption = GREEDY)`:
- `STATIC` by komponent pri zmene reštartoval.
- `RELUCTANT` by novú službu s vyšším rankingom ignoroval.

**Ďalšie Gogo príkazy:**
```powershell
.\gogo.ps1 "lb com.example"                                      # zoznam bundlov + stav
.\gogo.ps1 "diag <id>"                                           # prečo bundle nie je resolved (chýbajúci import)
.\gogo.ps1 "headers <id>"                                        # MANIFEST
.\gogo.ps1 "services com.example.greeting.api.GreetingService"   # kto službu registruje a kto ju používa
.\gogo.ps1 "scr:info com.example.greeting.impl.GreetingCommand"  # stav DS komponentu a jeho referencií
.\gogo.ps1 "scr:list"                                            # všetky DS komponenty
```

### 1.3 Portlet (MVCPortlet)

`modules/task-web`, portlet `com_example_task_web_TaskPortlet`:

| Trieda | Fáza | `mvc.command.name` | Spring ekvivalent |
|---|---|---|---|
| `ViewTasksMVCRenderCommand` | RENDER | `/`, `/task/view` | `GET /api/tasks` |
| `AddTaskMVCActionCommand` | ACTION | `/task/add` | `POST /api/tasks` |
| `ToggleTaskMVCActionCommand` | ACTION | `/task/toggle` | `PATCH /api/tasks/{id}/toggle` |
| `SavePreferencesMVCActionCommand` | ACTION (EDIT mód) | `/task/save_preferences` | `application.yml` / `@ConfigurationProperties` |
| `TasksJsonMVCResourceCommand` | RESOURCE | `/task/json` | `GET /api/tasks` (JSON) |

- **ACTION** mení stav a beží iba pre portlet, na ktorý sa kliklo. Potom nasleduje render všetkých portletov na stránke.
- **RENDER** je idempotentný a vracia HTML fragment. Portál ho volá pre každý portlet pri každom načítaní stránky.
- **RESOURCE** vracia ľubovoľné dáta (JSON, súbor) bez prekreslenia stránky. Je to najbližšia obdoba REST endpointu, ale je viazaná na stránku (`p_l_id`) aj session.
- JSP (`view.jsp`, `edit.jsp`) používa `<portlet:actionURL>`, `<portlet:renderURL>`, `<portlet:resourceURL>` a `<portlet:namespace/>`.
- **EDIT mód:** `javax.portlet.portlet-mode=text/html;view,edit` → v menu portletu sa objaví „Preferences“ a `pageSize` sa uloží do PortletPreferences pre danú inštanciu portletu.

### 1.4 REST v Liferay: JAX-RS namiesto REST Buildera

`modules/task-rest`: `TaskRestApplication` je komponent JAX-RS Whiteboardu.

```powershell
curl.exe -u test@liferay.com:test "http://localhost:8080/o/tasks?groupId=20117"

# JSON zo súboru: PowerShell 5.1 by úvodzovky v -d '{...}' zahodil a diakritika by nemusela prísť v UTF-8
[IO.File]::WriteAllText("$PWD\task.json", '{"title":"Task z REST"}')
curl.exe -u test@liferay.com:test -H "Content-Type: application/json" --data-binary "@task.json" "http://localhost:8080/o/tasks?groupId=20117"
Remove-Item task.json

curl.exe -u test@liferay.com:test -X PATCH http://localhost:8080/o/tasks/1/toggle
```
(`groupId` site Guest zistíš cez `.\create-demo-page.ps1`, ktorý ho vypíše.)

**Prečo JAX-RS a nie REST Builder:** stačí jedna trieda a jeden modul. REST Builder potrebuje OpenAPI YAML, generovanie a dva moduly (api + impl). Na demo je to zbytočná réžia. REST Builder sa oplatí pri produkčnom headless API (`/o/headless-*`): generuje DTO, OpenAPI, GraphQL, stránkovanie, filtre, batch a export/import a dodržiava Liferay konvencie (OAuth2 scopes).

`liferay.oauth2=false` vypína kontrolu OAuth2 scope, takže stačí Basic Auth. **Toto je len pre demo.**

### 1.5 docker-compose

- `postgres:16` (DB `lportal`, port **5433** na hoste, aby nekolidoval s lokálnym Postgresom).
- `liferay/portal:7.4.3.132-ga132`: konfigurácia cez `LIFERAY_*` env premenné (prevod `portal-ext.properties`: `.` → `_PERIOD_`, veľké písmeno → `_UPPERCASE<X>`).
- `./bundles/osgi/modules` je namountované do `/opt/liferay/osgi/modules`. Kopíruje tam `gradlew deploy` a Liferay (File Install) bundly hneď nasadí.
- Gogo shell počúva len na `localhost:11311` vnútri kontajnera, preto `gogo.ps1` používa `docker compose exec ... telnet`.

---

## Časť 2: Spring Boot

### Štruktúra

```
spring-boot-tasks/src/main/java/com/example/tasks/
├── task/
│   ├── Task.java                    JPA entita        ← Service Builder model
│   ├── TaskRepository.java          Spring Data       ← TaskPersistence + finder "Done"
│   ├── TaskService.java             @Transactional    ← TaskLocalServiceImpl
│   ├── TaskController.java          REST              ← MVC*Command
│   ├── TaskMapper.java, TaskNotFoundException.java
│   └── dto/                         records + Bean Validation
├── common/GlobalExceptionHandler    ProblemDetail (RFC 9457) ← SessionErrors + <liferay-ui:error>
└── config/
    ├── TaskProperties               ← PortletPreferences
    ├── SecurityConfig               ← Liferay roly/permissions
    ├── KeycloakRealmRoleConverter   JWT realm_access.roles → ROLE_*
    └── OpenApiConfig
resources/
├── application.yml, application-keycloak.yml
└── db/migration/V1__create_task.sql, V2__import_liferay_tasks.sql
```

### Spustenie

```powershell
cd spring-boot-tasks
.\mvnw test                 # 42 testov, Testcontainers si spustia vlastný postgres:16 (beží Docker)
.\mvnw spring-boot:run      # port 8081, DB = Liferay PostgreSQL z docker-compose (localhost:5433/lportal)
```

- Swagger UI: http://localhost:8081/swagger-ui.html, OpenAPI JSON: http://localhost:8081/v3/api-docs
- Flyway stav: http://localhost:8081/actuator/flyway

| Metóda | URL | Popis |
|---|---|---|
| GET | `/api/tasks?done=&page=0&size=` | stránkovaný zoznam (`size` obmedzený `tasks.max-page-size`) |
| GET | `/api/tasks/{id}` | detail, 404 ProblemDetail |
| POST | `/api/tasks` `{"title":"..."}` | 201 + `Location`, 400 pri prázdnom title |
| PUT | `/api/tasks/{id}` `{"title":"...","done":true}` | úprava |
| PATCH | `/api/tasks/{id}/toggle` | prepnutie `done` |
| DELETE | `/api/tasks/{id}` | 204 |

### Flyway: migrácia z Liferay tabuľky

**Strangler fig v praxi:** Spring Boot beží nad **tou istou PostgreSQL** ako Liferay, ale vo vlastnej schéme `tasks`.
- `V1__create_task.sql`: cieľová tabuľka `tasks.task`. Štruktúra vychádza z `DEMO_Task` (snake_case, NOT NULL, dlhší title, identity ID).
- `V2__import_liferay_tasks.sql`: ak existuje `public.demo_task` (placeholder `${liferaySchema}`), skopíruje dáta a:
  - **zachová pôvodné ID** (kvôli URL a referenciám),
  - nahradí prázdne a NULL hodnoty predvolenými,
  - posunie identity sekvenciu za `max(id)`.
  
  Bez Liferay tabuľky sa import preskočí.

Overené na reálnych dátach: úlohy vytvorené v Liferay cez portlet a JAX-RS (ID 1, 2) sa objavili v `GET /api/tasks` a nová úloha zo Spring Bootu dostala ID 3.

`LiferayMigrationIntegrationTest` overuje to isté v Testcontainers: init skript `liferay-legacy-schema.sql` vytvorí `demo_task` presne tak, ako ju vytvorí Liferay.

**Časové pásmo:** Liferay JVM beží s `-Duser.timezone=GMT`, takže `createDate` je v DB v UTC. Spring preto používa `Instant` + `hibernate.jdbc.time_zone=UTC`. Bez toho by sa nové a migrované záznamy líšili o 2 hodiny (zistené pri teste a opravené).

### Security

`SecurityConfig` obsahuje dva `SecurityFilterChain` beany prepínané cez `tasks.security.enabled`:
- `false` (predvolené, lokálne demo): všetko povolené.
- `true` (profil `keycloak`): OAuth2 resource server, JWT od Keycloaku. Liferay oprávnenia sa mapujú na Keycloak realm roly:
  - `VIEW` → `tasks-viewer`
  - `ADD_ENTRY`, `UPDATE`, `DELETE` → `tasks-editor`
  - Swagger a health ostávajú verejné.

`SecurityIntegrationTest` overuje 401/403/200/201 s mockovaným `JwtDecoder` a `jwt()` z spring-security-test. Keycloak samotný nie je súčasťou dema (iba konfigurácia v `application-keycloak.yml`).

---

## Mapovanie Liferay → Spring Boot

| Liferay | Spring Boot | Poznámka |
|---|---|---|
| Portlet **render** fáza (`MVCRenderCommand`) | `GET /api/tasks` | HTML render presúva na frontend (Angular); backend vracia JSON |
| Portlet **action** fáza (`MVCActionCommand`) + redirect | `POST` / `PUT` / `PATCH` / `DELETE` | PRG pattern nahrádza HTTP status (201/204) |
| Portlet **resource** fáza (`MVCResourceCommand`) | `GET` endpoint s JSON | resource URL je viazaná na stránku a session, REST nie |
| `PortletPreferences` (EDIT mód) | `@ConfigurationProperties` / `application.yml`; per-užívateľ nastavenie vo vlastnej tabuľke alebo na frontende | preferences boli per inštancia portletu na stránke |
| `service.xml` + Service Builder | JPA `@Entity` + Spring Data `JpaRepository` + Flyway | DDL spravuje Flyway, nie generátor |
| `<finder name="Done">` | derived query `findByDone(boolean, Pageable)` | |
| `TaskLocalServiceImpl` (`AopService`) | `@Service` + `@Transactional` | transakcie sú explicitné |
| `counterLocalService.increment()` | `IDENTITY` / sekvencia | po migrácii posunúť sekvenciu |
| OSGi DS `@Component` / `@Reference` | `@Service` / konštruktorová injekcia | |
| `service.ranking` + DYNAMIC/GREEDY | `@Primary`, `@Order`, `@Qualifier`, `List<T>`, `@ConditionalOn...` | Spring kontext je po štarte statický, bez hot-swap služieb |
| `Export-Package` / `Import-Package` | Maven moduly / JPMS / ArchUnit | modularita na úrovni buildu, nie runtime |
| Liferay roly + `ResourcePermission` + `ModelResourcePermission.check()` | Spring Security: roly z JWT, `requestMatchers`, `@PreAuthorize`; per-záznam ACL/ReBAC | identitu spravuje IdP (Keycloak) |
| Portlet session, `SessionErrors`, `ThemeDisplay`, `ServiceContext` | Stateless API: JWT, query/path parametre, ProblemDetail | žiadny server-side stav |
| `ParamUtil`, namespacované parametre | `@RequestParam`, `@PathVariable`, `@RequestBody` + `@Valid` | |
| `JSONFactory` / `JSONPortletResponseUtil` | Jackson (records) | Liferay serializuje `long` ako string (`"id":"1"`), Jackson ako číslo, čo môže rozbiť klientov |
| `/o/...` JAX-RS whiteboard / REST Builder | `@RestController` + springdoc OpenAPI | |
| Gogo shell (`lb`, `diag`, `services`) | Actuator (`/actuator/health`, `beans`, `conditions`, `flyway`) | |

---

## Typické migračné výzvy a stratégie

1. **Strangler fig.** Novú aplikáciu postav vedľa Liferay. Reverse proxy (nginx, Spring Cloud Gateway) presmerúva postupne URL po URL (`/web/guest/tasks` → Angular + `/api/tasks`). Liferay nevypínaj naraz. Portlety môžu dočasne volať nové API (resource command alebo JS fetch).
2. **Dáta.**
   - Service Builder tabuľky sú bežné SQL tabuľky, dajú sa migrovať Flywayom, ETL alebo CDC (Debezium) pri postupnom prechode.
   - **Pozor na:** ID z tabuľky `Counter` (zachovať a posunúť sekvencie), `companyId`/`groupId` (multi-tenancy, site → tenant), `uuid_` a `ctCollectionId` (Change Tracking), `mvccVersion`, UTC časy, `LONG` ako string v JSON a lokalizované polia uložené ako XML (`<root available-locales=...>`).
   - Postup: najprv read-only kópia, potom dvojitý zápis alebo CDC, nakoniec prepnutie.
3. **Používatelia a oprávnenia.**
   - Tabuľky `User_`, `Role_`, `UserGroup`, `Organization_`, `ResourcePermission` migruj do IdP (Keycloak). Môžeš použiť LDAP federation, ak Liferay už používal LDAP, alebo import cez Admin API.
   - **Heslá:** Liferay hashuje (PBKDF2 a i.). Keycloak vie importovať hash so správnym algoritmom, inak treba vynútený reset.
   - Site roly a per-záznam oprávnenia (`ResourcePermission` na `classPK`) nemajú 1:1 obdobu. Rieš ich doménovým modelom (vlastník, tím) alebo ACL/ReBAC (OpenFGA, Keycloak Authorization Services).
   - SSO počas prechodu: Liferay aj nová aplikácia ako klienti toho istého Keycloak realmu (OIDC).
4. **URL a SEO.** Friendly URL (`/web/guest/...`, `/-/`), `p_p_id` parametre a odkazy v obsahu treba presmerovať (301) a vytvoriť mapovaciu tabuľku starých a nových URL.
5. **Vyhľadávanie (Elasticsearch / OpenSearch).** Liferay indexuje cez vlastné Indexer/ModelDocumentContributor a sidecar ES (iba na vývoj). V novom svete: Spring Data Elasticsearch / OpenSearch client, indexovanie cez eventy (outbox) a reindex job. Facety, permission filtrovanie výsledkov a lokalizovaná analýza sa musia napísať znova.
6. **Documents & Media.** Binárky sú v Store (`data/document_library`, S3, DB) a metadáta v `DLFileEntry` a `DLFileVersion` (verzie, workflow, permissions). Migrácia: export cez headless API alebo priamo zo store + metadáta z DB → S3/MinIO + vlastná tabuľka. Pozor na verzie, náhľady, WebDAV a odkazy z web contentu.
7. **Web content, layouty a fragmenty.** Toto nie je backend a väčšinou smeruje do headless CMS alebo do Angularu. Oddeľ „aplikačné“ portlety (migruj) od „obsahových“ stránok (CMS rozhodnutie).
8. **Workflow (Kaleo), Asset framework (tagy, kategórie), komentáre, ratingy, notifikácie, scheduler.** Inventarizuj, čo sa naozaj používa, a nahraď cielene (Camunda/Flowable, Spring `@Scheduled`/Quartz, vlastné tabuľky).
9. **Integrácie a OSGi konfigurácia.** `@Meta.OCD` konfigurácie zo System Settings → `@ConfigurationProperties` + Spring Cloud Config/Vault. Inventár `*.config` súborov.
10. **Testovanie migrácie.** Rovnaké API kontrakty (contract testy), porovnávanie výstupov starého a nového systému na produkčnej kópii dát, Testcontainers s reálnym dumpom schémy.

---

## 15 pravdepodobných otázok na pohovor (so stručnými odpoveďami)

1. **Aký je rozdiel medzi action, render a resource fázou portletu?**
   - Action mení stav, beží iba pre jeden portlet a nasleduje redirect a render.
   - Render je idempotentný a vracia HTML fragment pre každý portlet na stránke.
   - Resource vracia dáta (JSON/súbor) mimo renderu stránky, typicky cez AJAX.
   - V REST je action POST/PUT/PATCH/DELETE, render aj resource sú GET.

2. **Čo generuje Service Builder a kam patrí vlastný kód?**
   - Generuje model, persistence (Hibernate), local/remote service, base implementácie, SQL a výnimky.
   - Vlastný kód patrí iba do `*LocalServiceImpl` / `*ServiceImpl` a `*Impl` modelu. Ostatné sa pri `buildService` pregeneruje.

3. **Ako by si migroval Service Builder entitu do Spring Bootu?**
   - JPA entita nad prevzatou štruktúrou tabuľky, Spring Data repository (findery → derived queries) a service s `@Transactional`.
   - DDL prevezme Flyway (V1 = súčasný stav, potom zmeny).
   - Zachovať ID a posunúť sekvencie, vyriešiť `companyId`/`groupId`, UTC.

4. **Čo je OSGi Declarative Services a ako sa líši od Spring DI?**
   - DS registruje komponenty ako služby v OSGi service registry. Služby sa môžu objavovať a miznúť za behu (dynamické referencie, ranking, cardinality).
   - Spring vytvorí statický kontext pri štarte.
   - Obe robia IoC, ale OSGi rieši aj modularitu a životný cyklus modulov (install/start/stop/update bez reštartu JVM).

5. **Čo robí `service.ranking` a policy `DYNAMIC` + `GREEDY`?**
   - Pri viacerých implementáciách vyhráva najvyšší ranking.
   - GREEDY sa prepojí na lepšiu službu, keď sa objaví. DYNAMIC to spraví bez reštartu komponentu.
   - V Springu: `@Primary`, `@Qualifier`, `@Order`, `@ConditionalOnProperty`.

6. **Čo je Export-Package / Import-Package a čo sa stane, keď import chýba?**
   - Bundle vidí iba importované balíky, ktoré iný bundle exportuje s vyhovujúcou verziou.
   - Bez nich bundle zostane `Installed`, nie `Resolved`/`Active`. `diag <id>` ukáže chýbajúce požiadavky.

7. **Ako by si postupoval pri migrácii celej Liferay aplikácie?**
   - Inventár: portlety, služby, tabuľky, integrácie, oprávnenia, obsah.
   - Strangler fig cez gateway, najprv read-only API nad rovnakou DB, potom presun zápisov, dát a používateľov.
   - Frontend po častiach (Angular), vypnutie Liferay až nakoniec. Každý krok so spätnou cestou (rollback).

8. **Čo s PortletPreferences?**
   - Rozlíš, či ide o globálnu konfiguráciu (`application.yml`, `@ConfigurationProperties`, Config server), per-užívateľské nastavenie (tabuľka + endpoint) alebo nastavenie UI komponentu (frontend config, CMS).
   - Dáta sú v tabuľkách `PortletPreferences` a `PortletPreferenceValue`.

9. **Ako nahradíš Liferay role a permissions?**
   - Identita a roly v IdP (Keycloak, OIDC). API ako OAuth2 resource server.
   - Hrubé oprávnenia ako roly v JWT a `requestMatchers` / `@PreAuthorize`.
   - Per-záznam oprávnenia (`ResourcePermission`) cez doménové pravidlá alebo ACL/ReBAC. Migrácia používateľov cez LDAP federation alebo import s hashom hesla.

10. **Prečo stateless API namiesto portlet session?**
    - Horizontálne škálovanie bez sticky sessions, jednoduchšie cache a testy, jasný kontrakt pre Angular aj mobil.
    - Stav nesie token (identita), URL a body. CSRF pri čisto token-based API (bez cookies) odpadá.

11. **Ako migruješ dáta a zároveň udržíš oba systémy v prevádzke?**
    - Zdieľaná DB s oddelenou schémou (ako v deme), alebo CDC (Debezium) z Liferay DB do novej, alebo dual-write cez outbox.
    - Idempotentné migračné skripty, kontrolné súčty a počty, prepínanie po doménach.

12. **REST Builder vs. JAX-RS komponent v Liferay?**
    - JAX-RS whiteboard je jednoduchý: jedna trieda, plná kontrola.
    - REST Builder generuje z OpenAPI DTO, resource, GraphQL, stránkovanie, filtre, batch a OAuth2 scopes. Je to štandard pre Liferay headless API.
    - Pri migrácii sú headless API dobrý zdroj kontraktu pre nové Spring endpointy.

13. **Ako budeš migrovať vyhľadávanie a Documents & Media?**
    - Search: vlastný index v Elasticsearch/OpenSearch, plnený eventmi (outbox) + reindex job. Treba vyriešiť filtrovanie podľa oprávnení a lokalizácie.
    - D&M: binárky zo store do S3/MinIO, metadáta a verzie z `DLFileEntry`/`DLFileVersion` do vlastnej tabuľky. 301 presmerovania pre staré `/documents/...` URL.

14. **Na čo si dať pozor pri Liferay dátach v DB?**
    - ID z `Counter` (nie sekvencia), `companyId`/`groupId`, lokalizované polia ako XML, `uuid_` a `externalReferenceCode` (dobré na idempotentný import), `ctCollectionId` (publications), soft-delete cez status (workflow), UTC dátumy.
    - Nemenné identifikátory používaj ako cudzie kľúče až po overení.

15. **Ako testuješ migráciu a novú aplikáciu?**
    - Unit testy služieb (Mockito), integračné testy s Testcontainers (reálny PostgreSQL + Flyway vrátane importu z legacy schémy), contract testy API a security testy (`jwt()`).
    - Porovnanie výstupov starého a nového API na kópii produkčných dát. Smoke testy po prepnutí v gateway.

---

## Overenie: čo bolo reálne otestované

| Čo | Ako | Výsledok |
|---|---|---|
| Build workspace | `.\gradlew jar deploy` | všetkých 7 bundlov `Active` (`lb com.example`) |
| Unit testy Liferay | `.\gradlew test` | 15/15 (TaskLocalServiceImpl, GreetingCommand, impl-y, portlet commands) |
| OSGi ranking | `greeting:hello` / `stop` / `start` v Gogo | prepínanie 200 ↔ 100 za behu |
| Portlet render | `GET /web/guest/task-demo` (prihlásený curl) | tabuľka úloh, „N task(s), showing max 10“ |
| Portlet action | POST na `actionURL` `/task/add`, `/task/toggle` | úloha vytvorená/prepnutá; prázdny title → „Task title is required.“ |
| Portlet resource | GET `resourceURL` `/task/json` | JSON pole úloh |
| EDIT mód | POST `/task/save_preferences` `pageSize=1` | „showing max 1“, vykreslená 1 úloha |
| JAX-RS | curl `/o/tasks` GET/POST/PATCH | 200/201/400/404; anonym → 403 |
| Spring testy | `.\mvnw test` | 42/42 (unit + Testcontainers + security) |
| Spring proti Liferay DB | `spring-boot:run` + curl | V1+V2 aplikované, Liferay úlohy 1, 2 importované, nová ID 3 |
| Swagger | `/swagger-ui.html`, `/v3/api-docs` | 200 |

### Problémy nájdené počas buildu (dobré „war stories“ na pohovor)

- **Service Builder vs. verzia portálu:** workspace plugin stiahol najnovší Service Builder (1.0.561). Ten generoval kód pre novšie API (`TaskPersistence.cacheResult` neexistuje v GA132), takže kompilácia padla. Riešenie: pin + `resolutionStrategy.force` na 1.0.496 (verzia z tagu GA132).
- **`Validator.isBlank("   ")` vracia v GA132 `false`.** Unit test odhalil, že title z medzier by prešiel. Nahradené `String.isBlank()`.
- **MVCRenderCommand s `mvc.command.name=/` sa použije vo všetkých portlet módoch**, takže `edit-template` sa nikdy neuplatnil a EDIT mód zobrazoval view. Riešenie: kontrola `PortletMode.EDIT` v render commande.
- **Unit testy Liferay kódu mimo portálu:** potrebujú `--add-opens java.base/java.lang.invoke` (kvôli `StringBundler`) a `PropsUtil.setProps(mock)` pre `ParamUtil`.
- **Liferay JSON serializuje `long` ako string**, Jackson ako číslo, čo je potenciálny breaking change pre klientov.
- **UTC:** Liferay ukladá časy v GMT, Spring v lokálnom čase. Bez `Instant` + `hibernate.jdbc.time_zone=UTC` vznikne posun.
- **`add-layout` cez JSON WS:** jednoduchší variant padal na `LayoutFriendlyURLException`, variant s mapami a `typeSettings` funguje (`create-demo-page.ps1`).

---

## Upratovanie

```powershell
cd liferay-workspace; docker compose down -v    # -v zmaže aj DB a Liferay data volume
```
