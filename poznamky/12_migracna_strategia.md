# 12 – Migračná stratégia

## Čo sa naučíš

- Ako sa migruje Liferay aplikácia **postupne**, bez veľkého „vypnúť a zapnúť“ (strangler fig)
- Čo všetko okrem business logiky treba vyriešiť: dáta, používateľov a oprávnenia, URL, vyhľadávanie, dokumenty
- Na čo si dať pozor v Liferay tabuľkách (ID, `companyId`/`groupId`, UTC, JSON)
- Ako vyzerá konkrétny migračný plán pre našu demo aplikáciu Task

---

## Teória v skratke

### Prečo nie „big bang“

Najlákavejší plán je: pol roka písať novú Spring Boot + Angular aplikáciu, potom jeden víkend prepnúť. V praxi to zlyháva:

- Liferay medzitým žije ďalej (nové požiadavky, opravy) a nová aplikácia ho dobieha.
- Pri prepnutí sa naraz ukážu všetky problémy (dáta, oprávnenia, URL) a cesta späť je ťažká.
- Používatelia dostanú naraz celý nový systém.

Preto sa migruje **postupne, kúsok po kúsku, s možnosťou vrátiť sa späť**.

### Strangler fig (škrtiaci figovník)

Názov pochádza z rastliny, ktorá obrastie strom, až ho nakoniec úplne nahradí. V softvéri:

```
                       používateľ
                           |
                 +---------v----------+
                 |   reverse proxy    |   nginx / Spring Cloud Gateway
                 |   (smerovanie URL) |
                 +----+----------+----+
     staré URL        |          |        nové URL (pribúdajú)
  /web/guest/...      |          |        /api/tasks, /app/tasks
                 +----v----+  +--v-------------------------+
                 | Liferay |  | Spring Boot API + Angular  |
                 +----+----+  +--+-------------------------+
                      |          |
                 +----v----------v----+
                 |  PostgreSQL        |   spočiatku spoločná DB,
                 |  public.demo_task  |   nová appka má vlastnú schému "tasks"
                 |  tasks.task        |
                 +--------------------+
```

1. Pred Liferay postavíš **reverse proxy**. Všetky požiadavky idú cez ňu.
2. Novú aplikáciu postavíš **vedľa** Liferay.
3. Proxy postupne, **URL po URL**, presmerúva na novú aplikáciu (napríklad `/web/guest/tasks` → Angular + `/api/tasks`).
4. Dočasne môžu aj staré portlety volať nové API (cez resource command alebo JS `fetch`).
5. Liferay vypneš, až keď cez neho už nič nejde.

Výhoda: každý krok je malý, dá sa otestovať na produkcii a v prípade problému stačí v proxy vrátiť smerovanie späť.

### Čo všetko treba migrovať (inventár)

Prvý krok každej migrácie je **inventár**: čo aplikácia z Liferay naozaj používa. Kód v `*LocalServiceImpl` je zvyčajne tá ľahšia časť. Typický zoznam:

| Oblasť | Čo v Liferay | Čo s tým |
|---|---|---|
| Business logika | `*LocalServiceImpl`, OSGi služby | `@Service` triedy ([kapitola 10](10_spring_boot_projekt.md)) |
| Dáta | Service Builder tabuľky (`DEMO_Task`) | Flyway / ETL / CDC ([kapitola 11](11_migracia_dat_a_security.md)) |
| UI | Portlety, JSP | REST API + Angular |
| Používatelia, role, oprávnenia | `User_`, `Role_`, `ResourcePermission` | Keycloak + Spring Security |
| URL | friendly URL `/web/guest/...`, `p_p_id` parametre | 301 presmerovania |
| Vyhľadávanie | Elasticsearch cez Liferay indexery | vlastný index |
| Dokumenty | Documents & Media | S3 / MinIO + vlastná tabuľka |
| Obsah | web content, stránky, fragmenty | headless CMS alebo Angular |
| Ostatné | workflow (Kaleo), tagy, kategórie, komentáre, scheduler | nahradiť cielene, len to, čo sa používa |
| Konfigurácia | System Settings (`@Meta.OCD`), `*.config` súbory | `@ConfigurationProperties`, Config server / Vault |

Nižšie sú jednotlivé oblasti rozpísané.

### 1. Dáta

Service Builder tabuľky sú **obyčajné SQL tabuľky**. Nič magické, dajú sa čítať aj z inej aplikácie. Možnosti:

- **Spoločná DB, vlastná schéma** – presne toto robí demo. Spring Boot beží nad tou istou PostgreSQL ako Liferay, ale vlastnú tabuľku má v schéme `tasks`. Flyway skript ([`V2__import_liferay_tasks.sql`](../spring-boot-tasks/src/main/resources/db/migration/V2__import_liferay_tasks.sql)) skopíruje dáta z `public.demo_task`.
- **ETL** – jednorazový export a import.
- **CDC (Change Data Capture, napr. Debezium)** – zmeny z Liferay DB sa priebežne prelievajú do novej. Hodí sa, keď majú oba systémy bežať dlhšie súbežne.
- **Dual-write cez outbox** – aplikácia zapisuje do oboch svetov.

Odporúčaný postup: najprv **read-only kópia**, potom **dvojitý zápis alebo CDC** a nakoniec **prepnutie**.

**Na čo si dať pozor v Liferay tabuľkách:**

| Pasca | Prečo | Riešenie |
|---|---|---|
| ID nie sú zo sekvencie | Liferay prideľuje ID cez tabuľku `Counter` (`counterLocalService.increment()`) | Zachovať pôvodné ID (kvôli URL a referenciám) a posunúť sekvenciu za `max(id)`. Demo to robí v `V2__import_liferay_tasks.sql`. |
| `companyId`, `groupId` | Liferay je multi-tenant: inštancia portálu a site | Rozhodnúť: tenant ID alebo zahodiť |
| `uuid_`, `externalReferenceCode` | Stabilné identifikátory | Dobré na idempotentný import |
| `ctCollectionId`, `mvccVersion` | Change Tracking (publications), optimistic locking | Väčšinou netreba prenášať, ale treba vedieť, čo znamenajú |
| Lokalizované polia | Uložené ako XML (`<root available-locales=...>`) | Rozparsovať do vlastnej štruktúry |
| Časy | Liferay JVM beží v GMT, `createDate` je v DB v UTC | Spring: `Instant` + `hibernate.jdbc.time_zone=UTC` ([`application.yml`](../spring-boot-tasks/src/main/resources/application.yml)) |
| `long` v JSON | Liferay serializuje `long` ako string (`"id":"1"`), Jackson ako číslo | Potenciálny breaking change pre klientov, treba to v kontrakte rozhodnúť |
| Soft-delete | Niektoré entity majú workflow `status` namiesto mazania | Pri migrácii filtrovať podľa statusu |

### 2. Používatelia a oprávnenia

V Liferay je všetko v jednom: tabuľky `User_`, `Role_`, `UserGroup`, `Organization_`, `ResourcePermission`. V novom svete identitu spravuje **IdP (Identity Provider), typicky Keycloak**, a Spring Boot je **OAuth2 resource server**, ktorý len overí JWT token.

- **Používatelia:** migrácia do Keycloaku. Ak Liferay už používal LDAP, Keycloak sa na ten istý LDAP napojí (LDAP federation). Inak import cez Admin API.
- **Heslá:** Liferay ich hashuje (PBKDF2 a ďalšie). Keycloak vie importovať hash, ak pozná algoritmus, inak treba vynútiť reset hesla.
- **Hrubé oprávnenia (role):** ako roly v JWT a v Spring Security `requestMatchers` alebo `@PreAuthorize`. V deme sa Liferay akcie mapujú na Keycloak realm roly: `VIEW` → `tasks-viewer`, `ADD_ENTRY`/`UPDATE`/`DELETE` → `tasks-editor` ([`SecurityConfig.java`](../spring-boot-tasks/src/main/java/com/example/tasks/config/SecurityConfig.java)).
- **Oprávnenia na konkrétny záznam** (`ResourcePermission` na `classPK`, „tento používateľ smie upravovať túto jednu úlohu“): 1:1 obdobu v Spring Security nemajú. Rieši sa doménovým modelom (vlastník, tím) alebo ACL/ReBAC (OpenFGA, Keycloak Authorization Services).
- **SSO počas prechodu:** Liferay aj nová aplikácia sú klienti toho istého Keycloak realmu (OIDC). Používateľ sa prihlási raz a funguje mu oboje.

### 3. URL a SEO

Liferay má vlastné URL: friendly URL (`/web/guest/...`, `/-/`), portletové parametre (`p_p_id`, `p_p_lifecycle`) a odkazy priamo v obsahu. Po migrácii:

- vytvoriť **mapovaciu tabuľku** starých a nových URL,
- staré URL **presmerovať (301)**, aby fungovali záložky, odkazy a vyhľadávače,
- pozrieť sa na odkazy uložené v obsahu (web content).

### 4. Vyhľadávanie

Liferay indexuje dáta do Elasticsearch cez vlastné indexery (Indexer, ModelDocumentContributor). Sidecar Elasticsearch, ktorý Liferay spúšťa sám, je **len na vývoj**. V produkcii je samostatný cluster, v repe pozri `liferay-workspace/configs/prod/osgi/configs/`.

V novom svete:
- Spring Data Elasticsearch alebo OpenSearch client,
- indexovanie cez eventy (outbox pattern) + reindex job,
- **znova napísať** facety, filtrovanie výsledkov podľa oprávnení a lokalizovanú analýzu textu. Toto Liferay robil za teba a často sa na to zabúda.

### 5. Documents & Media

Binárne súbory sú v **Store** (súborový systém `data/document_library`, S3 alebo DB), metadáta v tabuľkách `DLFileEntry` a `DLFileVersion` (verzie, workflow, oprávnenia).

Migrácia: export cez headless API alebo priamo zo store + metadáta z DB → S3/MinIO + vlastná tabuľka. Pozor na verzie súborov, náhľady, WebDAV a odkazy z web contentu (opäť 301 pre staré `/documents/...` URL).

### 6. Web content, stránky, fragmenty

Toto nie je backend. Rozdeľ portál na:
- **aplikačné portlety** (napr. náš Tasks) → migruješ do Spring Boot + Angular,
- **obsahové stránky** (texty, bannery, články) → rozhodnutie o CMS (headless CMS, alebo nechať v Liferay).

### 7. Workflow, tagy, komentáre, scheduler

Kaleo workflow, Asset framework (tagy, kategórie), komentáre, ratingy, notifikácie, plánovač úloh. Inventarizuj, čo sa **naozaj používa**, a nahraď cielene: Camunda/Flowable, Spring `@Scheduled`/Quartz, vlastné tabuľky. Nenahrádzaj veci „pre istotu“.

### 8. Testovanie migrácie

- **Contract testy:** nové API dodržiava dohodnutý kontrakt.
- **Porovnanie výstupov** starého a nového systému na kópii produkčných dát.
- **Testcontainers s reálnou schémou:** demo má [`LiferayMigrationIntegrationTest`](../spring-boot-tasks/src/test/java/com/example/tasks/migration/LiferayMigrationIntegrationTest.java), ktorý vytvorí tabuľku `demo_task` presne ako Liferay ([`liferay-legacy-schema.sql`](../spring-boot-tasks/src/test/resources/liferay-legacy-schema.sql)) a overí Flyway import.
- **Smoke testy** po každom prepnutí v proxy.

---

## Cvičenie: navrhni migráciu demo aplikácie krok po kroku

Vyskúšaj si to, akoby to bola úloha na pohovore: *„Máme Liferay s portletom Tasks a REST API `/o/tasks`. Navrhni migráciu na Spring Boot + Angular.“* Najprv si skús odpovedať sám, potom porovnaj s riešením.

### Krok 1: Inventár

Prejdi kód a zapíš, čo aplikácia používa:

| Čo | Kde v repe | Poznámka |
|---|---|---|
| Entita Task, tabuľka `DEMO_Task` | [`service.xml`](../liferay-workspace/modules/task/task-service/service.xml) | stĺpce `groupId`, `companyId`, `userId` – multi-tenancy |
| Business logika `addTask`, `toggleDone` | [`TaskLocalServiceImpl.java`](../liferay-workspace/modules/task/task-service/src/main/java/com/example/task/service/impl/TaskLocalServiceImpl.java) | validácia prázdneho title |
| Portlet: zoznam, pridanie, prepnutie, JSON | [`task-web/.../command/`](../liferay-workspace/modules/task-web/src/main/java/com/example/task/web/command) | render, action ×3, resource |
| Nastavenie veľkosti stránky | `SavePreferencesMVCActionCommand` (EDIT mód) | PortletPreferences |
| REST API | [`TaskRestApplication.java`](../liferay-workspace/modules/task-rest/src/main/java/com/example/task/rest/application/TaskRestApplication.java) | `/o/tasks`, Basic Auth, `liferay.oauth2=false` len pre demo |
| Oprávnenia | `javax.portlet.security-role-ref=power-user,user` v `TaskPortlet` | kto vidí portlet |

Žiadne vyhľadávanie, dokumenty ani workflow → migrácia je jednoduchá. V reálnom projekte by táto tabuľka mala desiatky riadkov.

### Krok 2: Cieľová architektúra

- Spring Boot API ([`spring-boot-tasks`](../spring-boot-tasks)) s endpointmi `/api/tasks` (GET, POST, PUT, PATCH toggle, DELETE).
- Angular frontend namiesto JSP.
- Keycloak ako IdP, Spring Boot ako resource server.
- Mapovanie portletu na REST:

| Portlet | REST |
|---|---|
| render (`ViewTasksMVCRenderCommand`) | `GET /api/tasks` |
| action `/task/add` | `POST /api/tasks` |
| action `/task/toggle` | `PATCH /api/tasks/{id}/toggle` |
| resource `/task/json` | `GET /api/tasks` (JSON) |
| EDIT mód, `pageSize` | `tasks.*` v `application.yml` ([`TaskProperties.java`](../spring-boot-tasks/src/main/java/com/example/tasks/config/TaskProperties.java)) |

### Krok 3: Dáta – read-only kópia

Spring Boot sa pripojí k Liferay PostgreSQL (`localhost:5433/lportal`), vlastnú tabuľku má v schéme `tasks`:
- `V1__create_task.sql` vytvorí `tasks.task`,
- `V2__import_liferay_tasks.sql` skopíruje dáta z `public.demo_task`, **zachová ID** a posunie sekvenciu.

Overenie v deme: úlohy 1 a 2 z Liferay sa objavili v `GET /api/tasks` a nová úloha zo Spring Bootu dostala ID 3. Podrobne v [kapitole 11](11_migracia_dat_a_security.md).

### Krok 4: Proxy a postupné prepínanie

1. Proxy pred Liferay, zatiaľ všetko smeruje na Liferay.
2. Klienti REST API prejdú z `/o/tasks` na `/api/tasks`. Pozor na `"id":"1"` vs. `"id":1`.
3. Stránka s portletom Tasks sa nahradí Angular stránkou, proxy presmeruje jej URL. Stará URL dostane 301.
4. Zápisy idú už len do novej aplikácie. Liferay tabuľka sa prestane používať.

### Krok 5: Používatelia

Používatelia a role do Keycloaku. Počas prechodu SSO pre oba systémy. Mapovanie: `VIEW` → `tasks-viewer`, `ADD_ENTRY`/`UPDATE`/`DELETE` → `tasks-editor`.

### Krok 6: Vypnutie

Keď cez proxy na Liferay nejde žiadna požiadavka, modul `task-web` a `task-rest` sa odinštaluje. Tabuľku `DEMO_Task` archivuješ a neskôr zmažeš.

**Každý krok má cestu späť:** stačí vrátiť smerovanie v proxy, dáta v Liferay zostávajú, kým sa zápisy nepresunú.

---

## Spring Boot ekvivalent

Celá mapovacia tabuľka v jednom (zdroj: [`README.md`](../README.md#mapovanie-liferay--spring-boot)):

| Liferay | Spring Boot |
|---|---|
| Render fáza (`MVCRenderCommand`) | `GET` endpoint, HTML render presúva na frontend |
| Action fáza (`MVCActionCommand`) + redirect | `POST`/`PUT`/`PATCH`/`DELETE`, HTTP status (201/204) namiesto PRG |
| Resource fáza (`MVCResourceCommand`) | `GET` endpoint s JSON |
| `PortletPreferences` | `@ConfigurationProperties`, per-používateľ vo vlastnej tabuľke alebo na frontende |
| `service.xml` + Service Builder | JPA `@Entity` + `JpaRepository` + Flyway |
| `<finder name="Done">` | `findByDone(boolean, Pageable)` |
| `TaskLocalServiceImpl` (`AopService`) | `@Service` + `@Transactional` |
| `counterLocalService.increment()` | `IDENTITY` / sekvencia (po migrácii posunúť) |
| `@Component` / `@Reference` | `@Service` / konštruktorová injekcia |
| `service.ranking` + `DYNAMIC`/`GREEDY` | `@Primary`, `@Qualifier`, `@Order`, `@ConditionalOn...` |
| `Export-Package` / `Import-Package` | Maven moduly / JPMS / ArchUnit |
| Liferay roly + `ResourcePermission` | roly z JWT, `requestMatchers`, `@PreAuthorize`, ACL/ReBAC |
| Portlet session, `SessionErrors`, `ThemeDisplay`, `ServiceContext` | Stateless API: JWT, parametre, ProblemDetail |
| `ParamUtil` | `@RequestParam`, `@PathVariable`, `@RequestBody` + `@Valid` |
| `JSONFactory` | Jackson |
| `/o/...` JAX-RS / REST Builder | `@RestController` + springdoc OpenAPI |
| Gogo shell | Actuator |

---

## Časté chyby

| Chyba | Prečo je zlá | Správne |
|---|---|---|
| Big bang prepnutie | Všetky problémy naraz, ťažká cesta späť | Strangler fig cez proxy, URL po URL |
| Začať písaním kódu | Až neskôr sa zistí, že aplikácia používa vyhľadávanie, dokumenty, workflow | Najprv inventár |
| Nové ID pri migrácii dát | Rozbijú sa URL, záložky, referencie z iných systémov | Zachovať ID, posunúť sekvenciu |
| Ignorovať časové pásmo | Posun o hodiny medzi starými a novými záznamami (v deme sa to naozaj stalo) | Všetko v UTC (`Instant`) |
| Zmeniť typ `id` v JSON bez dohody | Klienti očakávajúci string padnú | Rozhodnúť v API kontrakte, verzovať |
| Kopírovať Liferay oprávnenia 1:1 | `ResourcePermission` na úrovni záznamu nemá v Spring Security obdobu | Doménové pravidlá alebo ACL/ReBAC |
| Zabudnúť na staré URL | 404 pre používateľov aj vyhľadávače | Mapovacia tabuľka + 301 |

---

## Otázky na pohovor

**Ako by si postupoval pri migrácii celej Liferay aplikácie?**
Inventár (portlety, služby, tabuľky, integrácie, oprávnenia, obsah). Strangler fig cez gateway: najprv read-only API nad rovnakou DB, potom presun zápisov, dát a používateľov. Frontend po častiach, Liferay vypnúť až nakoniec. Každý krok s cestou späť.

**Ako migruješ dáta a zároveň udržíš oba systémy v prevádzke?**
Zdieľaná DB s oddelenou schémou (ako v deme), CDC (Debezium) alebo dual-write cez outbox. Idempotentné migračné skripty, kontrola počtov a súčtov, prepínanie po doménach.

**Na čo si dať pozor pri Liferay dátach v DB?**
ID z `Counter` (nie sekvencia), `companyId`/`groupId`, lokalizované polia ako XML, `uuid_` a `externalReferenceCode`, `ctCollectionId`, soft-delete cez status, UTC dátumy, `long` ako string v JSON.

**Ako nahradíš Liferay role a oprávnenia?**
Identita a roly v Keycloaku (OIDC), API ako OAuth2 resource server. Roly v JWT + `requestMatchers` / `@PreAuthorize`. Oprávnenia na záznam doménovými pravidlami alebo ACL/ReBAC. Používatelia cez LDAP federation alebo import s hashom hesla.

**Ako budeš migrovať vyhľadávanie a Documents & Media?**
Search: vlastný index v Elasticsearch/OpenSearch, plnený eventmi + reindex job, znova vyriešiť filtrovanie podľa oprávnení a lokalizáciu. D&M: binárky do S3/MinIO, metadáta a verzie do vlastnej tabuľky, 301 pre staré `/documents/...` URL.

**Čo je strangler fig a prečo ho použiť?**
Nová aplikácia rastie vedľa starej a reverse proxy na ňu postupne presmerúva URL. Malé kroky, overiteľné v produkcii, s možnosťou vrátiť smerovanie späť.

---

**Ďalej:** [13 – Otázky na pohovor](13_otazky_na_pohovor.md)
