# 01 – Čo je Liferay

## Čo sa naučíš

- Čo je Liferay a prečo vôbec vznikol
- Z čoho sa skladá bežiaci Liferay (Tomcat, OSGi, databáza, vyhľadávanie)
- Ako sa na Liferay „píše kód“: moduly, komponenty, portlety
- V čom sa to zásadne líši od Spring Boot aplikácie, a teda čo presne znamená „migrácia na Spring Boot“

---

## Teória v skratke

### Liferay = hotový portál, do ktorého sa píšu pluginy

Spring Boot appku začínaš od nuly: `main()`, pár controllerov, hotovo. Liferay je naopak **hotová aplikácia**: má používateľov, role, stránky, správu obsahu, dokumenty, vyhľadávanie, workflow a admin rozhranie.
Vývojár do nej **pridáva moduly**. Liferay sa používa hlavne na intranety, zákaznícke portály a weby veľkých firiem a úradov.

Dve edície:
- **Liferay Portal CE** (Community Edition) – zadarmo, bez podpory. Verzie sa volajú napr. `7.4.3.132-ga132` (GA = General Availability). Toto demo beží na **CE 7.4 GA132**.
- **Liferay DXP** – platená verzia s podporou. Novšie vydania sa volajú podľa štvrťroka, napr. `2024.Q2`.

### Portál a portlety

**Portál** skladá stránku z viacerých nezávislých okienok. Každé okienko je **portlet**: malá aplikácia, ktorá vyrenderuje len svoj kúsok HTML.

```
+--------------------------- stránka /web/guest/task-demo ---------------------------+
|  [ Navigácia (portlet) ]                                                            |
|  +----------------------------+   +-----------------------------------------------+ |
|  | Prihlásenie (portlet)      |   | Tasks (NÁŠ portlet z modulu task-web)         | |
|  |                            |   |  [ ] Kúpiť mlieko      [x] Napísať poznámky   | |
|  +----------------------------+   +-----------------------------------------------+ |
+-------------------------------------------------------------------------------------+
```

Portál pri každom zobrazení stránky požiada **všetky** portlety, aby sa vyrenderovali, a výsledok poskladá. 
Portlety sú definované štandardom JSR-286 (Portlet 2.0). Liferay podporuje aj novší Portlet 3.0, ale demo používa klasický štýl. Podrobnosti v [kapitole 07](07_portlet_mvc.md).

Prečo to vadí pri migrácii: portlet je **stavový** a **zviazaný s portálom** (URL, session, render cyklus). Spring Boot REST API je **bezstavové** a frontend (napríklad Angular) si stránku skladá sám.

### Z čoho sa skladá bežiaci Liferay

```
               prehliadač / REST klient
                         |
                    HTTP :8080
                         |
+------------------------v-------------------------------------------+
|  Docker kontajner liferay/portal:7.4.3.132-ga132                   |
|                                                                    |
|  Apache Tomcat                                                     |
|   └── webapp ROOT = jadro portálu (portal-impl, portal-kernel)     |
|         └── OSGi kontajner (Eclipse Equinox)                       |
|               ├── ~stovky Liferay bundlov (blogy, používatelia...) |
|               └── NAŠE bundly z ./bundles/osgi/modules             |
|                     greeting-*, task-api, task-service,            |
|                     task-web, task-rest                            |
|                                                                    |
|  Gogo shell (konzola do OSGi) – len vnútri na localhost:11311      |
+---------------------+-------------------------+--------------------+
                      | JDBC                    | (vyhľadávanie)
              +-------v--------+        +-------v-------------------+
              | PostgreSQL 16  |        | Elasticsearch             |
              | DB "lportal"   |        | v dev režime ho Liferay   |
              | (kontajner)    |        | spúšťa sám ako sidecar    |
              +----------------+        +---------------------------+
```

Najdôležitejšie:

1. **Tomcat** je obyčajný servlet kontajner. Portál je v ňom nasadený ako webová aplikácia.
2. **OSGi kontajner** beží vnútri portálu. Všetka funkcionalita (aj samotného Liferay) je rozdelená do **bundlov**, čiže JAR súborov s extra metadátami. Bundle sa dá nainštalovať, zastaviť alebo vymeniť **za behu, bez reštartu servera**. Na tom stojí hot deploy z [kapitoly 04](04_build_a_deploy_modulov.md).
3. **Databáza** obsahuje všetko: používateľov, stránky, obsah aj naše tabuľky (napríklad `DEMO_Task`). V deme je to PostgreSQL.
4. **Elasticsearch** slúži na fulltextové vyhľadávanie. V deme ho nepotrebuješ riešiť, ale pri migrácii je to samostatná téma ([kapitola 12](12_migracna_strategia.md)).

### Ako vyzerá kód pre Liferay

Píšeš **OSGi moduly**. Každý modul je samostatný Gradle projekt, ktorý sa zbuilduje do jedného JAR (bundlu). V module sú **komponenty**: triedy s anotáciou `@Component` z balíčka `org.osgi.service.component.annotations`. 
Pozor, nie je to Spring `@Component`. Komponent sa zaregistruje ako **služba** určitého typu a Liferay si ho podľa typu nájde:

| Chceš… | Komponent registruješ ako službu typu… |
|---|---|
| portlet (okienko na stránke) | `javax.portlet.Portlet` |
| handler pre jednu akciu portletu | `MVCActionCommand`, `MVCRenderCommand`, `MVCResourceCommand` |
| REST API | `javax.ws.rs.core.Application` (JAX-RS) |
| vlastnú business službu | vlastné rozhranie, napr. `GreetingService` |
| prácu s DB | generuje **Service Builder** (`TaskLocalService`, `TaskPersistence`) |

Toto je hlavný myšlienkový posun: v Liferay **nevoláš framework**. Zaregistruješ službu so správnymi vlastnosťami a portál si ju nájde sám, podobne ako Spring nájde `@RestController`.

### Čo teda znamená „migrácia Liferay → Spring Boot“

Z Liferay sa berie **len vlastná business logika a dáta**, nie celý portál:

| V Liferay | Po migrácii |
|---|---|
| Service Builder entity a `*LocalServiceImpl` | JPA entity, Spring Data, `@Service` |
| Portlety (render, action, resource) | REST API (`@RestController`) + frontend (Angular) |
| OSGi služby a `@Reference` | Spring beany a konštruktorová injekcia |
| Liferay používatelia, role, oprávnenia | Keycloak / OAuth2 + Spring Security |
| Tabuľky `DEMO_*` v Liferay DB | Vlastná schéma, migrácia dát cez Flyway / ETL |
| Funkcie portálu (CMS, dokumenty, vyhľadávanie) | Treba rozhodnúť: nahradiť, ponechať v Liferay, alebo kúpiť iný produkt |

Posledný riadok je na pohovore najdôležitejší. Liferay dáva „zadarmo“ veľa vecí, ktoré v Spring Boot zadarmo nie sú.

---

## Krok po kroku: zorientuj sa v repe

V tejto kapitole ešte nič nespúšťaš, len sa pozrieš, ako je demo poskladané. Príkazy sú pre **PowerShell** (Windows 11), spúšťaj ich z koreňa repa (`C:\projekty\tmp\liferay-demo`).

### 1. Aké moduly máme

```powershell
Get-ChildItem liferay-workspace\modules -Name
```

Očakávaný výstup:

```
greeting-api
greeting-impl
greeting-impl-alt
task
task-rest
task-web
```

`task` je len obal, skutočné moduly sú v ňom dva (`task-api` a `task-service`).

### 2. Koľko bundlov z toho vznikne

Každý bundle má súbor `bnd.bnd` s OSGi metadátami, takže stačí ich spočítať:

```powershell
Get-ChildItem liferay-workspace\modules -Recurse -Filter bnd.bnd | Resolve-Path -Relative | Sort-Object
```

Očakávaný výstup (7 bundlov):

```
.\liferay-workspace\modules\greeting-api\bnd.bnd
.\liferay-workspace\modules\greeting-impl\bnd.bnd
.\liferay-workspace\modules\greeting-impl-alt\bnd.bnd
.\liferay-workspace\modules\task\task-api\bnd.bnd
.\liferay-workspace\modules\task\task-service\bnd.bnd
.\liferay-workspace\modules\task-rest\bnd.bnd
.\liferay-workspace\modules\task-web\bnd.bnd
```

Týchto 7 bundlov uvidíš v [kapitole 05](05_osgi_a_gogo_shell.md) v Gogo shelli ako `Active`.

### 3. Aké OSGi služby moduly registrujú

```powershell
Get-ChildItem liferay-workspace\modules -Recurse -Filter *.java |
  Select-String -CaseSensitive -AllMatches 'service = (\w+)\.class' |
  ForEach-Object { $_.Matches } | ForEach-Object { $_.Groups[1].Value } |
  Group-Object | Sort-Object Count -Descending | Format-Table Count, Name -AutoSize
```

Očakávaný výstup:

```
Count Name
----- ----
    3 MVCActionCommand
    2 GreetingService
    1 MVCRenderCommand
    1 MVCResourceCommand
    1 Portlet
    1 Application
    1 AopService
    1 Object
    1 TaskPersistence
    1 ArgumentsResolver
```

Ako to čítať:

| Riadok | Modul | Význam |
|---|---|---|
| `MVCActionCommand` ×3 | task-web | pridať úlohu, prepnúť hotovo, uložiť nastavenia (action fáza) |
| `MVCRenderCommand`, `MVCResourceCommand` | task-web | zobraziť zoznam (render), vrátiť JSON (resource) |
| `Portlet` | task-web | samotný portlet „Tasks“ |
| `GreetingService` ×2 | greeting-impl, greeting-impl-alt | dve implementácie jedného rozhrania, vyhrá vyšší `service.ranking` |
| `Object` | greeting-impl | Gogo príkaz `greet` (Gogo príkazy sa registrujú ako `Object`) |
| `Application` | task-rest | JAX-RS REST API na `/o/tasks` |
| `AopService`, `TaskPersistence`, `ArgumentsResolver` | task-service | vygeneroval Service Builder (servisná vrstva + perzistencia) |

### 4. To isté v Spring Boot projekte

```powershell
Get-ChildItem spring-boot-tasks\src\main -Recurse -Filter *.java |
  Select-String -CaseSensitive -List '^@(Service|RestController|Repository|Configuration|Component)' |
  ForEach-Object { Resolve-Path -Relative $_.Path } | Sort-Object
```

Očakávaný výstup:

```
.\spring-boot-tasks\src\main\java\com\example\tasks\common\GlobalExceptionHandler.java
.\spring-boot-tasks\src\main\java\com\example\tasks\config\OpenApiConfig.java
.\spring-boot-tasks\src\main\java\com\example\tasks\config\SecurityConfig.java
.\spring-boot-tasks\src\main\java\com\example\tasks\config\TaskProperties.java
.\spring-boot-tasks\src\main\java\com\example\tasks\SpringBootTasksApplication.java
.\spring-boot-tasks\src\main\java\com\example\tasks\task\TaskController.java
.\spring-boot-tasks\src\main\java\com\example\tasks\task\TaskService.java
```

Všimni si, že `TaskRepository` v zozname chýba. Je to rozhranie `extends JpaRepository<Task, Long>` bez anotácie a Spring Data si implementáciu vyrobí sám. V Liferay to isté robí Service Builder, len generuje skutočný kód (`TaskPersistenceImpl`) do repa.

---

## Kód z repa: rovnaká vec v dvoch svetoch

### Business služba

Liferay, [`TaskLocalServiceImpl.java`](../liferay-workspace/modules/task/task-service/src/main/java/com/example/task/service/impl/TaskLocalServiceImpl.java) (skrátené):

```java
@Component(
	property = "model.class.name=com.example.task.model.Task",
	service = AopService.class
)
public class TaskLocalServiceImpl extends TaskLocalServiceBaseImpl {
	// vlastné metódy addTask, toggleDone...
}
```

- `@Component` je OSGi anotácia, `service = AopService.class` povie Liferay, že trieda má dostať transakcie cez AOP proxy.
- Trieda dedí od **vygenerovanej** bázovej triedy, ktorá už má CRUD metódy aj prístup k perzistencii.

Spring Boot, [`TaskService.java`](../spring-boot-tasks/src/main/java/com/example/tasks/task/TaskService.java) (skrátené):

```java
@Service
@Transactional(readOnly = true)
public class TaskService {

	private final TaskRepository taskRepository;
	private final TaskProperties taskProperties;

	public TaskService(TaskRepository taskRepository, TaskProperties taskProperties) { ... }
}
```

- Transakcie sú explicitné (`@Transactional`), závislosti idú cez konštruktor.
- Žiadna generovaná bázová trieda, CRUD dodáva `JpaRepository`.

### Portlet ako OSGi služba

[`TaskPortlet.java`](../liferay-workspace/modules/task-web/src/main/java/com/example/task/web/portlet/TaskPortlet.java):

```java
@Component(
	property = {
		"javax.portlet.display-name=Tasks",
		"javax.portlet.init-param.view-template=/view.jsp",
		"javax.portlet.name=" + TaskPortletKeys.TASK,
		"javax.portlet.portlet-mode=text/html;view,edit",
		...
	},
	service = Portlet.class
)
public class TaskPortlet extends MVCPortlet {
}
```

Trieda je prázdna a celé nastavenie je vo **vlastnostiach komponentu**. V starších verziách Liferay bolo v XML súboroch `portlet.xml` a `liferay-portlet.xml`. Práca sa deleguje na `MVC*Command` komponenty, viac v [kapitole 07](07_portlet_mvc.md).

---

## Spring Boot ekvivalent

| Pojem | Liferay | Spring Boot |
|---|---|---|
| Čo spúšťam | Celý portál (Tomcat + OSGi + stovky bundlov) | Jednu appku (`java -jar`) s embedded Tomcatom |
| Jednotka nasadenia | Bundle (JAR) pridaný do bežiaceho portálu | Celá aplikácia (jeden fat JAR / image) |
| Registrácia komponentu | OSGi `@Component(service = X.class)` | Spring `@Component` / `@Service` |
| Závislosť | `@Reference` (dynamická, služba môže prísť aj odísť za behu) | Konštruktorová injekcia (statická, nastaví sa pri štarte) |
| Konfigurácia | `portal-ext.properties`, OSGi konfigurácie, PortletPreferences | `application.yml`, `@ConfigurationProperties` |
| Web vrstva | Portlety (HTML fragmenty) | REST controllery (JSON) + samostatný frontend |
| Perzistencia | Service Builder (generovaný kód) | JPA + Spring Data |
| Štart | Prvý štart ~2 min, potom deploy modulov bez reštartu | Pár sekúnd, zmena = reštart |

---

## Časté chyby (v myslení)

| Chyba | Prečo je zlá | Správne |
|---|---|---|
| „Liferay je framework ako Spring“ | Liferay je hotový produkt. Framework (OSGi + portlety + Service Builder) je len spôsob, ako ho rozširovať. | Pri migrácii vždy zistiť, **ktoré funkcie portálu** aplikácia používa. |
| Zamieňať OSGi `@Component` so Spring `@Component` | Iný balíček aj iná sémantika (vlastnosti, dynamické referencie, ranking) | Pozri sa na import: `org.osgi.service.component.annotations` |
| „Portlet = controller, prepíšem ho 1:1“ | Portlet vracia HTML fragment a má fázy a stav. REST controller vracia dáta. | Rozdeliť na REST API + frontend; render ≈ `GET`, action ≈ `POST/PATCH`, resource ≈ `GET` s JSON |
| Ignorovať `groupId` a `companyId` v tabuľkách | Liferay je multi-tenant, každá entita patrí do inštancie a site | Pri migrácii dát rozhodnúť, čo s nimi (tenant ID alebo zahodiť) |

---

## Otázky na pohovor

**Čo je Liferay a na čo sa používa?**
Java portálová platforma (CE zadarmo, DXP platená) na intranety, zákaznícke portály a firemné weby. Obsahuje správu používateľov, obsahu, dokumentov, vyhľadávanie a workflow. Vývojári ju rozširujú OSGi modulmi.

**Aká je architektúra Liferay 7.x?**
Tomcat, v ňom webová aplikácia portálu a vnútri OSGi kontajner (Eclipse Equinox), kde beží väčšina funkcionality aj vlastné moduly. Dáta sú v relačnej DB, vyhľadávanie ide cez Elasticsearch.

**Čo je portlet a ako sa líši od REST endpointu?**
Portlet je komponent, ktorý renderuje fragment HTML stránky v portáli. Má fázy (action, render, resource), stav a URL generované portálom. REST endpoint je bezstavový a vracia dáta. O zobrazenie sa stará klient.

**Prečo firmy migrujú z Liferay na Spring Boot?**
Typicky kvôli licenčným nákladom DXP, zložitosti upgradov medzi verziami, nedostatku Liferay vývojárov, potrebe moderného SPA frontendu (Angular, React) a nasadzovaniu v kontajneroch alebo mikroslužbách. Pri migrácii však treba nahradiť aj funkcie, ktoré portál dával zadarmo.

**Čo je najväčšie riziko takej migrácie?**
Podceniť funkcie portálu, ktoré aplikácia používa implicitne: používatelia a oprávnenia, CMS, Documents & Media, vyhľadávanie, workflow a URL. Business logika v `*LocalServiceImpl` je zvyčajne tá jednoduchšia časť.

---

**Ďalej:** [02 – Predpoklady a setup](02_predpoklady_a_setup.md)
