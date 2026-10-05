# 10 – Spring Boot projekt

## Čo sa naučíš

- Ako je postavený `spring-boot-tasks` a ktorá trieda nahrádza ktorú Liferay vec
- Ako aplikáciu spustiť nad **tou istou** PostgreSQL ako Liferay
- Ako volať REST API, Swagger UI a Actuator
- Ako spustiť testy (unit + Testcontainers + security) a čo testujú

**Predpoklad:** beží PostgreSQL z `liferay-workspace/docker-compose.yml` ([kapitola 03](03_spustenie_liferay.md)). Liferay samotný bežať nemusí, ale ak beží a má dáta v `demo_task`, Spring Boot si ich pri prvom štarte naimportuje ([kapitola 11](11_migracia_dat_a_security.md)).

---

## Teória v skratke

Rovnaká doména **Task**, prepísaná do bežného Spring Boot štýlu:

| Komponent | Verzia (z `pom.xml` / BOM) |
|---|---|
| Spring Boot | 4.1.1 |
| Java | 21 |
| Hibernate | 7.4.5 |
| Flyway | 12.4.0 |
| springdoc-openapi | 3.1.1 |
| Testcontainers | 2.0.5 |
| JUnit | 6.0.3 |

(Overené verzie a zdroje sú v [README](../README.md#overené-verzie).)

### Štruktúra projektu

```
spring-boot-tasks/src/main/java/com/example/tasks/
├── SpringBootTasksApplication.java     main()                                     ≈ celý Liferay portál
├── task/
│   ├── Task.java                       JPA entita                                 ≈ Task, TaskModelImpl, TaskImpl (Service Builder)
│   ├── TaskRepository.java             Spring Data                                ≈ TaskPersistence / TaskPersistenceImpl
│   ├── TaskService.java                business logika, @Transactional            ≈ TaskLocalServiceImpl
│   ├── TaskController.java             REST /api/tasks                            ≈ portlet commands + JAX-RS /o/tasks
│   ├── TaskMapper.java                 entita → DTO                               ≈ ručné JSONUtil.put(...)
│   ├── TaskNotFoundException.java      → 404                                      ≈ NoSuchTaskException
│   └── dto/
│       ├── CreateTaskRequest.java      record + @NotBlank                         ≈ TaskTitleException
│       ├── UpdateTaskRequest.java
│       ├── TaskResponse.java           výstupné DTO (bez legacy stĺpcov)
│       └── PageResponse.java           stabilná obálka stránky
├── common/GlobalExceptionHandler.java  ProblemDetail pre chyby                    ≈ SessionErrors / try-catch v JAX-RS
└── config/
    ├── TaskProperties.java             @ConfigurationProperties("tasks")          ≈ PortletPreferences
    ├── SecurityConfig.java             Spring Security (open / OAuth2 JWT)        ≈ Liferay role a permissions
    ├── KeycloakRealmRoleConverter.java role z JWT → ROLE_*
    └── OpenApiConfig.java              Swagger metadata

src/main/resources/
├── application.yml                     ≈ portal-ext.properties
├── application-keycloak.yml            profil so zapnutou security
└── db/migration/
    ├── V1__create_task.sql             ≈ tables.sql zo Service Buildera
    └── V2__import_liferay_tasks.sql    import dát z Liferay tabuľky demo_task
```

**Kľúčové rozhodnutie v `application.yml`:** aplikácia beží na porte **8081** (8080 má Liferay) a pripája sa na **tú istú DB** `lportal` na `localhost:5433`, ale do **vlastnej schémy `tasks`**. Liferay tabuľky sú v schéme `public` a Spring ich len raz prečíta pri migrácii.

```yaml
server:
  port: 8081
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/lportal
  jpa:
    hibernate:
      ddl-auto: validate              # schému vlastní Flyway, Hibernate ju len kontroluje
    properties:
      hibernate.default_schema: tasks
      hibernate.jdbc.time_zone: UTC   # rovnako ako Liferay
  flyway:
    schemas: tasks
    create-schemas: true
```

---

## Krok po kroku

### 1. Spusti aplikáciu

```powershell
cd spring-boot-tasks
.\mvnw spring-boot:run
```

Prvý beh stiahne Maven a závislosti. Zaujímavé riadky logu:

```
FlywayExecutor  : Database: jdbc:postgresql://localhost:5433/lportal (PostgreSQL 16.15)
Schema          : Creating schema "tasks" ...
DbMigrate       : Migrating schema "tasks" to version "1 - create task"
DbMigrate       : Migrating schema "tasks" to version "2 - import liferay tasks"
DbMigrate       : Successfully applied 2 migrations to schema "tasks", now at version v2 (execution time 00:00.045s)
TomcatWebServer : Tomcat started on port 8081 (http) with context path '/'
SpringBootTasksApplication : Started SpringBootTasksApplication in 5.9 seconds (process running for 6.329)
```

**5,9 sekundy**, kým Liferay pri prvom štarte potreboval 130 s. Aplikácia beží v popredí, ukončíš ju `Ctrl+C`. Ďalšie príkazy spúšťaj v inom okne.

> Ak PostgreSQL nebeží, štart spadne na `Connection to localhost:5433 refused`. Spusti `docker compose up -d postgres` v `liferay-workspace`.

### 2. Zoznam úloh

```powershell
$B = "http://localhost:8081/api/tasks"
curl.exe -s "$B"
```

```json
{"content":[{"id":102,"title":"Guest task","done":true,"createDate":"2026-10-04T07:13:40.927Z"},{"id":101,"title":"Zavolať mame","done":false,"createDate":"2026-10-04T07:11:41.422Z"},{"id":3,"title":"Kúpiť chlieb","done":false,"createDate":"2026-10-04T07:10:12.908Z"},{"id":2,"title":"Napísať poznámky","done":false,"createDate":"2026-10-04T07:10:01.815Z"},{"id":1,"title":"Kúpiť mlieko","done":true,"createDate":"2026-10-04T07:10:00.889Z"}],"page":0,"size":10,"totalElements":5,"totalPages":1}
```

To sú **úlohy z Liferay**, ktoré naimportovala Flyway migrácia V2. ID, názvy aj časy sedia. Rozdiely oproti Liferay API:
- `"id":102` je **číslo**, v Liferay bolo `"id":"102"` (reťazec),
- odpoveď je obálka so stránkovaním (`content`, `page`, `size`, `totalElements`, `totalPages`),
- nie je tu `groupId`, nové API nemá koncept site.

Filter a stránkovanie:

```powershell
curl.exe -s "${B}?done=false&size=2"
```

(`${B}` v zložených zátvorkách, lebo `?` by PowerShell zobral ako súčasť názvu premennej, viď [kapitola 08](08_rest_v_liferay.md).)

```json
{"content":[{"id":101,"title":"Zavolať mame","done":false,...},{"id":3,"title":"Kúpiť chlieb","done":false,...}],"page":0,"size":2,"totalElements":3,"totalPages":2}
```

Na rozdiel od Liferay REST ([kapitola 08](08_rest_v_liferay.md)) tu filter `done` funguje aj so stránkovaním a vráti aj `totalElements`.

### 3. Detail, vytvorenie, toggle

```powershell
curl.exe -s "$B/101"
```

```json
{"id":101,"title":"Zavolať mame","done":false,"createDate":"2026-10-04T07:11:41.422Z"}
```

Vytvorenie (JSON zo súboru kvôli diakritike a úvodzovkám, rovnako ako v kapitole 08):

```powershell
[IO.File]::WriteAllText("$PWD\s.json", '{"title":"Prvá úloha v Spring Boot"}')
curl.exe -s -i --data-binary "@s.json" -H 'Content-Type: application/json' "$B" | Select-String '^HTTP|^Location|^\{'
Remove-Item s.json
```

```
HTTP/1.1 201
Location: /api/tasks/103
{"id":103,"title":"Prvá úloha v Spring Boot","done":false,"createDate":"2026-10-04T07:20:30.247827900Z"}
```

`201 Created` + hlavička `Location` je správny REST štýl. ID **103** pokračuje za najvyšším importovaným ID (102). Pozor však, čo sa stane, keď v tom istom čase pridáva úlohy aj Liferay ([kapitola 11](11_migracia_dat_a_security.md)).

```powershell
curl.exe -s -X PATCH "$B/2/toggle"
```

```json
{"id":2,"title":"Napísať poznámky","done":true,"createDate":"2026-10-04T07:10:01.815Z"}
```

### 4. Chyby: všade `ProblemDetail`

```powershell
[IO.File]::WriteAllText("$PWD\s.json", '{"title":"  "}')
curl.exe -s -w ' HTTP %{http_code}\n' -H 'Content-Type: application/json' --data-binary "@s.json" "$B"
Remove-Item s.json
```

```
{"detail":"Invalid request content.","instance":"/api/tasks","status":400,"title":"Bad Request"} HTTP 400
```

```powershell
curl.exe -s -w ' HTTP %{http_code}\n' -H 'Content-Type: application/json' -d 'nie json' "$B"
```

```
{"detail":"Failed to read request","instance":"/api/tasks","status":400,"title":"Bad Request"} HTTP 400
```

(V Liferay JAX-RS to isté vrátilo `200` s prázdnym telom.)

```powershell
curl.exe -s -w ' HTTP %{http_code}\n' "$B/999"
```

```
{"detail":"Task 999 not found","instance":"/api/tasks/999","status":404,"title":"Task not found"} HTTP 404
```

Formát je štandard **RFC 9457 Problem Details**. Frontend (Angular) tak spracuje všetky chyby jedným spôsobom.

### 5. Swagger UI a OpenAPI

V prehliadači otvor <http://localhost:8081/swagger-ui.html> (presmeruje na `/swagger-ui/index.html`). Uvidíš všetky endpointy a môžeš ich skúšať priamo.

Strojovo čitateľná špecifikácia:

```powershell
(curl.exe -s http://localhost:8081/v3/api-docs).Substring(0, 200)
```

```
{"openapi":"3.1.0","info":{"title":"Tasks API","description":"Spring Boot náhrada Liferay Task portletu / Service Builder služby","version":"v1"},...
```

Z tejto špecifikácie si Angular vie vygenerovať TypeScript klienta (napr. `openapi-generator`).

### 6. Actuator

```powershell
curl.exe -s http://localhost:8081/actuator/health
```

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

```powershell
(curl.exe -s http://localhost:8081/actuator/flyway).Substring(0, 300)
```

Ukáže aplikované migrácie (`V1__create_task.sql`, `V2__import_liferay_tasks.sql`, stav `SUCCESS`). `liveness` a `readiness` sú pripravené pre Kubernetes probes. V Liferay sa stav zisťuje z logu a Gogo shellu.

### 7. Testy

```powershell
.\mvnw test
```

Beží ~45 s (Testcontainers spustí vlastný PostgreSQL 16 v Dockeri, nezávislý od toho z docker-compose). Výsledok pri overovaní:

| Testovacia trieda | Testov | Druh | Čo overuje |
|---|---|---|---|
| `TaskServiceTest` | 11 | unit (Mockito) | logika servisu: create (trim), update, toggle, delete, stránkovanie, filter done, not found |
| `TaskControllerIntegrationTest` | 9 | `@SpringBootTest` + MockMvc + Testcontainers | REST: 201 + `Location`, 400/404 `ProblemDetail`, PUT, PATCH toggle, DELETE 204, stránkovanie + filter, OpenAPI |
| `SecurityIntegrationTest` | 6 | `@SpringBootTest` so zapnutou security + mock JWT | 401 bez tokenu, viewer číta, viewer nesmie písať (403), editor vytvára, token bez rolí 403, Swagger verejný |
| `LiferayMigrationIntegrationTest` | 3 | Flyway nad simulovanou Liferay schémou | import so zachovaním ID, predvolené hodnoty pre prázdny title/dátum, sekvencia za importovanými ID |
| `KeycloakRealmRoleConverterTest` | 4 | unit | prevod `realm_access.roles` z JWT na `ROLE_*` |
| `TaskPropertiesTest` | 4 | unit | `resolvePageSize`: null → predvolená, rozsah, orezanie na max, ≤ 0 → 1 |
| `PageResponseTest` | 2 | unit | obálka stránky |
| `TaskTest` | 2 | unit | `toggleDone()` na entite |
| `TaskMapperTest` | 1 | unit | entita → DTO |
| **Spolu** | **42** | | **0 chýb** |

Počty si overíš:

```powershell
Get-ChildItem target\surefire-reports\TEST-*.xml | ForEach-Object {
  $s = ([xml](Get-Content $_.FullName -Raw)).testsuite
  "name=""$($s.name)"" tests=""$($s.tests)"""
}
```

Jedna trieda:

```powershell
.\mvnw test "-Dtest=TaskServiceTest"
```

Ako funguje Testcontainers v projekte ([`TestcontainersConfiguration.java`](../spring-boot-tasks/src/test/java/com/example/tasks/TestcontainersConfiguration.java)):

```java
@Bean
@ServiceConnection                          // Spring Boot sám nastaví datasource na tento kontajner
PostgreSQLContainer postgresContainer() {
	return new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
			.withInitScript("liferay-legacy-schema.sql");   // simulovaná Liferay tabuľka demo_task
}
```

Testy tak bežia proti **reálnemu PostgreSQL** s rovnakou Liferay tabuľkou, akú vytvára Service Builder. Pritom netreba bežiaci Liferay. Porovnaj s [kapitolou 09](09_testovanie_liferay.md), kde integračný test v Liferay vyžaduje nasadenie do bežiaceho portálu.

---

## Kód z repa

### Controller: [`TaskController.java`](../spring-boot-tasks/src/main/java/com/example/tasks/task/TaskController.java)

```java
@GetMapping
@Operation(summary = "Seznam úkolů (stránkovaný)")
public PageResponse<TaskResponse> list(
		@RequestParam(required = false) Boolean done,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(required = false) Integer size) {
	return PageResponse.from(taskService.findAll(done, page, size), TaskMapper::toResponse);
}

/** Vytvorenie – portlet: MVCActionCommand "/task/add" */
@PostMapping
@Operation(summary = "Vytvoří úkol")
public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
	TaskResponse created = TaskMapper.toResponse(taskService.create(request));
	return ResponseEntity.created(URI.create("/api/tasks/" + created.id())).body(created);
}
```

### DTO ako records

```java
/** Vstup pre POST /api/tasks – validácia nahrádza ručnú kontrolu + TaskTitleException */
public record CreateTaskRequest(@NotBlank @Size(max = 255) String title) { }

/** Výstupné DTO – API nevystavuje JPA entitu ani legacy Liferay stĺpce */
public record TaskResponse(Long id, String title, boolean done, Instant createDate) { }
```

Prečo DTO a nie entita priamo: API kontrakt je oddelený od DB schémy. Legacy stĺpce (`group_id`, `company_id`, `user_id`) v entite sú, ale klient ich nevidí.

### Globálne chyby: [`GlobalExceptionHandler.java`](../spring-boot-tasks/src/main/java/com/example/tasks/common/GlobalExceptionHandler.java)

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {   // validácia, zlý JSON → ProblemDetail

	@ExceptionHandler(TaskNotFoundException.class)
	public ProblemDetail handleNotFound(TaskNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Task not found");
		return problem;
	}
}
```

`ResponseEntityExceptionHandler` už rieši štandardné Spring výnimky (nevalidný JSON, `@Valid`), stačí pridať vlastné.

---

## Spring Boot vs. Liferay: súhrn

| Vec | Liferay | Spring Boot |
|---|---|---|
| Štart | 130 s (prvý), 28 s (ďalší) | 5,9 s |
| Nasadenie | 7 bundlov do bežiaceho portálu | 1 JAR |
| Perzistencia | Service Builder, 20 tried | entita + repository |
| REST | JAX-RS, ručný JSON, chyby per metóda | `@RestController`, DTO, `ProblemDetail` |
| Konfigurácia | PortletPreferences, `portal-ext.properties` | `application.yml`, `@ConfigurationProperties` |
| Dokumentácia API | `/o/api` (len headless API) | Swagger UI pre vlastné API |
| Health | log, Gogo | `/actuator/health` |
| Testy | 15 unit testov, zvyšok ručne | 42 testov vrátane DB, REST, security a migrácie |
| Čo **chýba** oproti Liferay | – | UI, používatelia, role, CMS, dokumenty, vyhľadávanie, workflow. Všetko treba dodať inak. |

---

## Časté chyby

| Príznak | Príčina | Riešenie |
|---|---|---|
| `Connection to localhost:5433 refused` | PostgreSQL z docker-compose nebeží | `docker compose up -d postgres` v `liferay-workspace` |
| `Port 8081 was already in use` | Aplikácia už beží v inom okne | Ukonči ju alebo `--server.port=8082` |
| `Schema-validation: missing table [task]` | Flyway nebežal (iná schéma alebo vypnutý) | Skontroluj `spring.flyway.schemas` a `hibernate.default_schema` |
| Testy: `Could not find a valid Docker environment` | Docker Desktop nebeží | Spusti Docker |
| Diakritika rozbitá v DB | `curl -d` z konzoly na Windows (argumenty nie sú spoľahlivo UTF-8) | JSON zo súboru cez `[IO.File]::WriteAllText`, `--data-binary "@s.json"` |
| Import z Liferay sa „nezopakuje“ | Flyway V2 beží len raz (je v `flyway_schema_history`) | Je to zámer. Viac v kapitole 11. |

---

## Otázky na pohovor

**Ako by si štruktúroval Spring Boot náhradu Liferay modulu?**
Balíček podľa domény (`task`): entita, repository, servis, controller, DTO, mapper. K tomu spoločný exception handler a konfigurácia. Schému riadi Flyway (`ddl-auto: validate`), DTO oddeľujú API od entity a testy pokrývajú servis (unit), REST (MockMvc + Testcontainers), security a migráciu.

**Prečo `ddl-auto: validate` a nie `update`?**
Schéma musí byť verziovaná a reprodukovateľná (Flyway skripty v gite, review, rovnaké na všetkých prostrediach). `update` nevie bezpečne meniť existujúce dáta ani mazať stĺpce a pri migrácii z Liferay DB by mohol siahnuť na cudzie tabuľky. `validate` len skontroluje, že entita sedí so schémou.

**Prečo DTO a nie vracať entitu?**
Stabilný API kontrakt nezávislý od DB, žiadne lazy-loading problémy pri serializácii, skrytie interných a legacy polí, explicitná validácia vstupu.

**Čo je ProblemDetail?**
Štandardný formát chýb HTTP API (RFC 9457): `type`, `title`, `status`, `detail`, `instance`. Spring ho podporuje natívne a klient spracuje všetky chyby jednotne.

**Prečo Testcontainers namiesto H2?**
Testy bežia proti rovnakej DB a verzii ako produkcia (PostgreSQL 16), vrátane PostgreSQL-špecifického SQL (v deme blok `DO $$ ... $$` vo Flyway V2), ktoré by v H2 nefungovalo. `@ServiceConnection` nastaví pripojenie bez ďalšej konfigurácie.

---

**Ďalej:** [11 – Migrácia dát a security](11_migracia_dat_a_security.md)
