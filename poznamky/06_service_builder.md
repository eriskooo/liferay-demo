# 06 – Service Builder

## Čo sa naučíš

- Čo je Service Builder a prečo ho Liferay má
- Ako vyzerá `service.xml` a čo z neho vznikne (≈ 20 tried + SQL)
- Do ktorých dvoch súborov píšeš vlastný kód a ktorých sa nikdy nedotýkaš
- Ako Liferay generuje ID (tabuľka `Counter`) a prečo sú v ID diery
- Ako to isté vyzerá v Spring Boot (JPA entita + Spring Data)

---

## Teória v skratke

Service Builder je **generátor kódu**. Napíšeš XML s popisom entity a on vygeneruje celú perzistentnú a servisnú vrstvu: model, DAO (tu „persistence“), servis, SQL skripty, cache aj OSGi zapojenie. Interne používa Hibernate, ale Hibernate anotácie v kóde neuvidíš.

Prečo to Liferay robí takto (a nie JPA):
- jednotný štýl všetkých Liferay modulov (aj samotný Liferay je napísaný cez Service Builder),
- automatická **cache** entít a finderov, pripravená na cluster,
- integrácia s portálom: multi-tenancy (`companyId`, `groupId`), oprávnenia, indexovanie do vyhľadávania, transakcie, JSON web služby.

```
                          service.xml
                               |
                .\gradlew buildService
                               |
        +----------------------+------------------------+
        v                                               v
   task-api  (exportované rozhrania)               task-service  (implementácia)
   ├── model/Task, TaskModel, TaskWrapper         ├── model/impl/TaskModelImpl, TaskBaseImpl, TaskCacheModel
   ├── service/TaskLocalService (+Util, Wrapper)  ├── model/impl/TaskImpl                 ← TVOJ KÓD (model)
   ├── service/persistence/TaskPersistence        ├── service/base/TaskLocalServiceBaseImpl
   └── exception/NoSuchTaskException,             ├── service/impl/TaskLocalServiceImpl   ← TVOJ KÓD (logika)
                 TaskTitleException               ├── service/persistence/impl/TaskPersistenceImpl
                                                  └── META-INF/sql/tables.sql, indexes.sql, sequences.sql
```

**Zlaté pravidlo:** vlastný kód píšeš len do **`TaskLocalServiceImpl`** (business logika) a **`TaskImpl`** (pomocné metódy modelu). Tie Service Builder vygeneruje **raz** a potom ich už neprepisuje. Všetko ostatné sa pri každom `buildService` pregeneruje a ručné zmeny by si stratil.

Prečo dva moduly:
- **`task-api`** exportuje rozhrania. Ostatné moduly (portlet, REST) závisia **len od API**.
- **`task-service`** obsahuje implementáciu, ktorá je pred ostatnými skrytá.

Je to ten istý princíp ako `greeting-api` a `greeting-impl` z [kapitoly 05](05_osgi_a_gogo_shell.md).

---

## Krok po kroku

### 1. Prečítaj si `service.xml`

[`modules/task/task-service/service.xml`](../liferay-workspace/modules/task/task-service/service.xml) (bez komentárov):

```xml
<service-builder dependency-injector="ds" package-path="com.example.task">
	<namespace>DEMO</namespace>
	<entity local-service="true" name="Task" remote-service="false" uuid="false">

		<column name="taskId" primary="true" type="long" />

		<column name="groupId" type="long" />
		<column name="companyId" type="long" />
		<column name="userId" type="long" />
		<column name="createDate" type="Date" />

		<column name="title" type="String" />
		<column name="done" type="boolean" />

		<order by="desc">
			<order-column name="createDate" />
		</order>

		<finder name="Done" return-type="Collection">
			<finder-column name="done" />
		</finder>
		<finder name="GroupId" return-type="Collection">
			<finder-column name="groupId" />
		</finder>
	</entity>
	<exceptions>
		<exception>TaskTitle</exception>
	</exceptions>
</service-builder>
```

Riadok po riadku:

| Element | Čo z neho vznikne |
|---|---|
| `package-path="com.example.task"` | balíčky `com.example.task.model`, `.service`, `.service.persistence`… |
| `dependency-injector="ds"` | vygenerované triedy sú OSGi DS komponenty (`@Component`, `@Reference`) |
| `<namespace>DEMO</namespace>` | prefix tabuliek: entita `Task` → tabuľka **`DEMO_Task`** |
| `local-service="true"` | vznikne `TaskLocalService` (volanie v rámci servera, **bez** kontroly oprávnení) |
| `remote-service="false"` | **nevznikne** `TaskService` (remote verzia s kontrolou oprávnení a JSON WS) |
| `uuid="false"` | bez stĺpca `uuid_` (ten sa používa pri exporte a importe obsahu medzi prostrediami) |
| `<column ... primary="true">` | primárny kľúč `taskId` |
| `groupId`, `companyId`, `userId`, `createDate` | štandardné Liferay stĺpce: site, inštancia portálu, autor, čas |
| `<order by="desc">` | predvolené zoradenie všetkých dotazov: najnovšie prvé |
| `<finder name="Done">` | metódy `findByDone(boolean)`, `countByDone(boolean)`… + DB index na `done` |
| `<finder name="GroupId">` | `findByGroupId(long, start, end)`, `countByGroupId(long)` + index |
| `<exception>TaskTitle</exception>` | trieda `TaskTitleException` v `task-api` |

### 2. Spusti generovanie

```powershell
cd liferay-workspace
.\gradlew :modules:task:task-service:buildService
```

Očakávaný výstup:

```
> Task :modules:task:task-service:buildService
Building Task
Writing ..\task-test\src\testIntegration\java\com\example\task\service\persistence\test\TaskPersistenceTest.java
Writing src\main\resources\service.properties
BUILD SUCCESSFUL in 8s
```

Zaujímavé je, čo **nie je** vo výstupe: žiadna Java trieda v `task-api` ani `task-service` sa nezmenila. Service Builder je deterministický. Keď sa `service.xml` nezmení, vygeneruje presne to isté a súbory neprepisuje. Zapísal len:
- `service.properties`, kde zvýšil `build.number` (počítadlo buildov),
- kostru integračného testu do `task-test/` (modul, ktorý v deme nepoužívame).

Upratanie, aby si nemal v gite zbytočné zmeny:

```powershell
git checkout -- modules/task/task-service/src/main/resources/service.properties
Remove-Item modules\task\task-test -Recurse -Force
```

> **Kedy `buildService` spúšťaš:** po každej zmene `service.xml` a po pridaní novej **public** metódy do `TaskLocalServiceImpl`. Service Builder ju skopíruje do rozhrania `TaskLocalService` v `task-api`, inak by ju ostatné moduly nevideli.

### 3. Pozri, koľko kódu vzniklo

```powershell
Get-ChildItem modules\task -Recurse -Filter *.java |
  Where-Object FullName -like '*\main\*' |
  ForEach-Object { ($_.FullName -split '\\java\\', 2)[1] } |
  Sort-Object
```

```
com\example\task\exception\NoSuchTaskException.java
com\example\task\exception\TaskTitleException.java
com\example\task\model\impl\TaskBaseImpl.java
com\example\task\model\impl\TaskCacheModel.java
com\example\task\model\impl\TaskImpl.java                   ← tvoj kód
com\example\task\model\impl\TaskModelImpl.java
com\example\task\model\Task.java
com\example\task\model\TaskModel.java
com\example\task\model\TaskTable.java
com\example\task\model\TaskWrapper.java
com\example\task\service\base\TaskLocalServiceBaseImpl.java
com\example\task\service\impl\TaskLocalServiceImpl.java     ← tvoj kód
com\example\task\service\persistence\impl\constants\DEMOPersistenceConstants.java
com\example\task\service\persistence\impl\TaskModelArgumentsResolver.java
com\example\task\service\persistence\impl\TaskPersistenceImpl.java
com\example\task\service\persistence\TaskPersistence.java
com\example\task\service\persistence\TaskUtil.java
com\example\task\service\TaskLocalService.java
com\example\task\service\TaskLocalServiceUtil.java
com\example\task\service\TaskLocalServiceWrapper.java
```

20 tried pre jednu entitu so 7 stĺpcami. Samotný `TaskPersistenceImpl.java` má **1 679 riadkov**, `TaskModelImpl.java` 725. V Spring Boot to isté zvládnu 2 súbory (entita + repository).

Na čo sú tie triedy:

| Trieda | Úloha | Spring ekvivalent |
|---|---|---|
| `Task` (rozhranie) | model, s ktorým pracujú ostatné moduly | JPA entita `Task` |
| `TaskModelImpl`, `TaskBaseImpl` | gettery/settery, mapovanie na stĺpce | polia entity |
| `TaskImpl` | miesto pre vlastné metódy modelu (zatiaľ prázdne) | metódy v entite (`toggleDone()`) |
| `TaskCacheModel` | serializovateľná verzia pre cache | (Hibernate 2nd level cache) |
| `TaskPersistence` / `TaskPersistenceImpl` | CRUD + findery (DAO) | `TaskRepository extends JpaRepository` |
| `TaskUtil` | statický prístup k persistence | – |
| `TaskLocalService` | rozhranie servisu (exportované) | public API `TaskService` |
| `TaskLocalServiceBaseImpl` | CRUD metódy servisu + `@Reference` na persistence a Counter | – |
| `TaskLocalServiceImpl` | **tvoja business logika** | `@Service TaskService` |
| `TaskLocalServiceUtil` | statický prístup k servisu (pre JSP, staré API) | – |
| `TaskLocalServiceWrapper` | na prepisovanie servisu inými modulmi | AOP / dekorátor |
| `tables.sql`, `indexes.sql` | DDL, ktoré Liferay spustí pri prvom štarte | Flyway `V1__create_task.sql` |

### 4. Pozri sa na vytvorenú tabuľku a dáta

Tabuľka vznikla pri prvom nasadení `task-service` ([kapitola 04](04_build_a_deploy_modulov.md)). Vytvor si pár úloh cez REST API (podrobne v [kapitole 08](08_rest_v_liferay.md)):

```powershell
[IO.File]::WriteAllText("$PWD\task.json", '{"title":"Kúpiť mlieko"}')
curl.exe -s -u test@liferay.com:test -H 'Content-Type: application/json' `
  --data-binary "@task.json" "http://localhost:8080/o/tasks?groupId=20117"
Remove-Item task.json
```

> `groupId=20117` je ID site „Guest“. U teba môže byť iné, zistíš ho príkazom:
> `docker compose exec -T postgres psql -U liferay -d lportal -tAc "select groupid from group_ where friendlyurl='/guest'"`
>
> JSON posielame zo súboru (`--data-binary "@task.json"`) kvôli úvodzovkám a diakritike, vysvetlenie je v sekcii Časté chyby. `[IO.File]::WriteAllText` zapíše UTF-8 bez BOM. `@` musí byť v úvodzovkách, inak ho PowerShell berie ako splatting.

Pozri sa do DB:

```powershell
docker compose exec -T postgres psql -U liferay -d lportal -c "select * from demo_task order by taskid"
```

```
 taskid | groupid |   companyid    | userid |       createdate        |      title       | done
--------+---------+----------------+--------+-------------------------+------------------+------
      1 |   20117 | 54453140550724 |  20123 | 2026-10-04 07:10:00.889 | Kúpiť mlieko     | t
      2 |   20117 | 54453140550724 |  20123 | 2026-10-04 07:10:01.815 | Napísať poznámky | f
      3 |   20117 | 54453140550724 |  20123 | 2026-10-04 07:10:12.908 | Kúpiť chlieb     | f
    101 |   20117 | 54453140550724 |  20123 | 2026-10-04 07:11:41.422 | Zavolať mame     | f
```

- `userid 20123` je `test@liferay.com` (pozri `user_` v [kapitole 03](03_spustenie_liferay.md)). Vyplnil ho `ServiceContext` z prihláseného používateľa.
- `createdate` je v **UTC**. Liferay JVM beží v GMT. Pri migrácii na to treba myslieť ([kapitola 11](11_migracia_dat_a_security.md)).
- **Prečo je posledné ID 101, a nie 4?** Pozri ďalší krok.

### 5. Counter: ako Liferay generuje ID

V `TaskLocalServiceImpl.addTask` je:

```java
long taskId = counterLocalService.increment(Task.class.getName());
```

ID teda **nedáva databáza** (sekvencia, identity), ale Liferay služba **Counter**, ktorá si stav drží v tabuľke `counter`:

```powershell
docker compose exec -T postgres psql -U liferay -d lportal -c `
  "select name, currentid from counter where name = 'com.example.task.model.Task'"
```

```
            name             | currentid
-----------------------------+-----------
 com.example.task.model.Task |       200
```

Counter si z DB **rezervuje blok 100 ID** a rozdáva ich z pamäte (rýchle, bez zápisu do DB pri každom inserte). Čo sa stalo v deme:

1. Prvá úloha: Counter si rezervoval 1–100 (v DB `currentid=100`) a rozdal 1, 2, 3.
2. Reštart Liferay: nepoužité ID 4–100 sa stratili.
3. Ďalšia úloha: nový blok 101–200 (v DB `currentid=200`), úloha dostala **101**.

Pre migráciu z toho plynie:
- Diery v ID sú v Liferay normálne. Na ID sa nespoliehaj ako na poradie bez medzier.
- Po migrácii dát do novej tabuľky musí identity/sekvencia začínať **za** najvyšším importovaným ID. V Spring Boot projekte to rieši Flyway skript ([kapitola 11](11_migracia_dat_a_security.md)).

---

## Kód z repa: vlastná logika

[`TaskLocalServiceImpl.java`](../liferay-workspace/modules/task/task-service/src/main/java/com/example/task/service/impl/TaskLocalServiceImpl.java) (skrátené):

```java
@Component(
	property = "model.class.name=com.example.task.model.Task",
	service = AopService.class
)
public class TaskLocalServiceImpl extends TaskLocalServiceBaseImpl {

	public Task addTask(String title, ServiceContext serviceContext) throws PortalException {
		if ((title == null) || title.isBlank()) {
			throw new TaskTitleException("Title must not be blank");
		}

		long taskId = counterLocalService.increment(Task.class.getName());

		Task task = taskPersistence.create(taskId);       // nový objekt, ešte nie v DB

		task.setGroupId(serviceContext.getScopeGroupId()); // site
		task.setCompanyId(serviceContext.getCompanyId());  // inštancia portálu
		task.setUserId(serviceContext.getUserId());        // prihlásený používateľ
		task.setCreateDate(serviceContext.getCreateDate(new Date()));
		task.setTitle(title.trim());
		task.setDone(false);

		return taskPersistence.update(task);              // INSERT
	}

	public Task toggleDone(long taskId) throws PortalException {
		Task task = taskPersistence.findByPrimaryKey(taskId); // NoSuchTaskException, ak neexistuje
		task.setDone(!task.isDone());
		return taskPersistence.update(task);                  // UPDATE
	}

	public List<Task> getTasksByDone(boolean done) {
		return taskPersistence.findByDone(done);              // vygenerovaný finder
	}
	...
}
```

Na čo si dať pozor:

- **`taskPersistence`, `counterLocalService`** sú polia z vygenerovanej `TaskLocalServiceBaseImpl`, ktorá ich dostane cez `@Reference`. Ty ich len používaš.
- **`service = AopService.class`**: Liferay obalí triedu proxy, ktorá rieši transakcie. Rozhranie `TaskLocalService` má anotáciu `@Transactional(... rollbackFor = {PortalException.class, SystemException.class})`, takže **každá public metóda je transakčná**. Metódy `get*` sú len na čítanie.
- **`ServiceContext`** je Liferay „taška“ s kontextom požiadavky: kto je prihlásený, v akom site, jazyk, oprávnenia. Posiela sa do takmer každej Liferay metódy, ktorá niečo vytvára.
- **Validácia** je v servise a pri chybe vyhodí vlastnú výnimku `TaskTitleException` (vygenerovanú zo `<exception>`).
- **`update()` robí INSERT aj UPDATE.** Persistence rozozná podľa stavu objektu (nový/existujúci), čo má urobiť. Podobne ako `save()` v Spring Data.

---

## Spring Boot ekvivalent

Entita [`Task.java`](../spring-boot-tasks/src/main/java/com/example/tasks/task/Task.java) (skrátené):

```java
@Entity
@Table(name = "task")
public class Task {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)  // ID dáva DB, nie Counter
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private boolean done;

	@Column(name = "create_date", nullable = false, updatable = false)
	private Instant createDate;                          // Instant = UTC ako v Liferay

	@Column(name = "group_id") private Long groupId;     // legacy, len pre importované dáta
	@Column(name = "company_id") private Long companyId;
	@Column(name = "user_id") private Long userId;

	public void toggleDone() { done = !done; }           // logika v entite (≈ TaskImpl)
}
```

Repository [`TaskRepository.java`](../spring-boot-tasks/src/main/java/com/example/tasks/task/TaskRepository.java):

```java
public interface TaskRepository extends JpaRepository<Task, Long> {
	Page<Task> findByDone(boolean done, Pageable pageable);   // ≈ <finder name="Done">
}
```

Servis [`TaskService.java`](../spring-boot-tasks/src/main/java/com/example/tasks/task/TaskService.java) (skrátené):

```java
@Service
@Transactional(readOnly = true)
public class TaskService {
	public Task create(CreateTaskRequest request) {
		return taskRepository.save(new Task(request.title().trim()));
	}

	@Transactional
	public Task toggleDone(long id) {
		Task task = getById(id);   // TaskNotFoundException ≈ NoSuchTaskException
		task.toggleDone();
		return task;               // dirty checking: UPDATE pri commite, bez save()
	}
}
```

| Service Builder | Spring Boot |
|---|---|
| `service.xml` | JPA anotácie na entite |
| `buildService` generuje ~20 tried | Spring Data generuje implementáciu repository **za behu** (proxy) |
| `<finder name="Done">` | `findByDone(...)` (derived query z názvu metódy) |
| `TaskPersistenceImpl` | `JpaRepository` (CRUD, stránkovanie, sorting) |
| `TaskLocalServiceImpl` + `AopService` | `@Service` + `@Transactional` |
| `counterLocalService.increment(...)` | `@GeneratedValue(IDENTITY)` alebo sekvencia |
| `tables.sql` pri prvom štarte + upgrade procesy | Flyway migrácie (`V1__...sql`, `V2__...sql`) |
| `<namespace>DEMO</namespace>` → `DEMO_Task` | `@Table(name = "task")` |
| `title VARCHAR(75)` (predvolená dĺžka) | `varchar(255) not null` vo Flyway skripte |
| `start`/`end` stránkovanie (`findByGroupId(groupId, 0, 20)`) | `Pageable` (`PageRequest.of(page, size)`) |
| `NoSuchTaskException` (checked, `PortalException`) | `TaskNotFoundException` (runtime) |
| Entity a finder cache zabudovaná | voliteľne Hibernate 2nd level cache / Spring Cache |

---

## Časté chyby

| Príznak | Príčina | Riešenie |
|---|---|---|
| Ručná zmena v `TaskPersistenceImpl` alebo `TaskModelImpl` zmizla | Súbor sa pri `buildService` pregeneroval | Vlastný kód len do `TaskLocalServiceImpl` a `TaskImpl` |
| Nová metóda v `TaskLocalServiceImpl` nie je vidieť v portlete | Nespustil si `buildService`, metóda nie je v rozhraní `TaskLocalService` | `.\gradlew :modules:task:task-service:buildService` a potom `deploy` |
| Kompilácia padá na neexistujúcich metódach (napr. `cacheResult`) | Novší Service Builder generuje kód pre novšie API, ako má portál | Pinnúť verziu Service Buildera k verzii portálu (v deme 1.0.496, pozri `task-service/build.gradle`) |
| Po zmene `service.xml` (nový stĺpec) sa tabuľka nezmenila | `tables.sql` sa spúšťa len ak tabuľka neexistuje | Upgrade proces (`UpgradeStepRegistrator`) + zvýšiť `Liferay-Require-SchemaVersion` v `bnd.bnd`, alebo v dev zmazať tabuľku/DB |
| Diakritika v DB ako `K�pit` | `curl -d '{"title":"Kúpiť"}'` na Windows neposlal UTF-8 (overené pri písaní kapitoly). V PowerShell 5.1 sa z `-d` navyše stratia úvodzovky a JSON je neplatný | JSON do súboru cez `[IO.File]::WriteAllText` a `--data-binary "@task.json"` |
| V ID sú veľké diery | Counter rezervuje bloky po 100, reštart nepoužité zahodí | Normálne správanie, nič neopravuj |
| Priamy SQL `UPDATE` sa v portáli neprejaví | Liferay má entity/finder cache, o zmene v DB nevie | Meniť dáta cez servis. Ak už musíš cez SQL, vyčisti cache (Server Administration) alebo reštartuj |

---

## Otázky na pohovor

**Čo je Service Builder?**
Liferay generátor perzistentnej a servisnej vrstvy z `service.xml`. Generuje model, persistence (DAO nad Hibernate), local/remote servis, SQL, cache a OSGi zapojenie. Vlastný kód sa píše do `*LocalServiceImpl` a `*Impl`.

**Aký je rozdiel medzi LocalService a Service (remote)?**
`LocalService` sa volá v rámci JVM a **nekontroluje oprávnenia**. `Service` (pri `remote-service="true"`) je obal, ktorý kontroluje oprávnenia a dá sa sprístupniť cez JSON web služby. Typicky remote servis skontroluje permission a zavolá local.

**Ako Liferay generuje primárne kľúče?**
Cez `CounterLocalService` a tabuľku `counter`, nie cez DB sekvencie. Counter rezervuje bloky ID (štandardne 100), preto po reštarte vznikajú diery. Pri migrácii dát treba nastaviť identity/sekvenciu novej DB za maximálne importované ID.

**Ako riešiš zmenu schémy v Service Builder module?**
Upravíš `service.xml`, spustíš `buildService` a napíšeš upgrade proces (`UpgradeStepRegistrator`, `UpgradeProcess`), ktorý zmení existujúcu tabuľku. Zvýšiš schema verziu bundlu. V Spring Boot to isté robí ďalšia Flyway migrácia.

**Ako by si migroval Service Builder entitu do Spring Boot?**
`service.xml` → JPA entita (názvy stĺpcov, typy, dĺžky), findery → Spring Data derived queries, `*LocalServiceImpl` → `@Service` s `@Transactional`, `tables.sql` → Flyway `V1`, dáta → Flyway alebo ETL skript (vrátane nastavenia sekvencie). Rozhodnúť treba, čo s `groupId`/`companyId`/`userId`: zahodiť, ponechať ako legacy referencie alebo namapovať na nový tenant a používateľa. V deme zostali ako legacy stĺpce.

**Čo ti Service Builder dáva, čo v Spring Boot musíš riešiť sám?**
Automatickú entity/finder cache s invalidáciou v clustri, integráciu s oprávneniami, indexovaním (vyhľadávanie), assetmi a workflow, plus multi-tenancy cez `companyId`.

---

**Ďalej:** [07 – Portlet (MVCPortlet)](07_portlet_mvc.md)
