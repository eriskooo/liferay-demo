# 09 – Testovanie v Liferay

## Čo sa naučíš

- Aké druhy testov sa v Liferay píšu a prečo je to ťažšie ako v Spring Boot
- Ako spustiť unit testy workspace a kde je report
- Triky, bez ktorých Liferay kód mimo portálu netestuješ (`@Reference` polia, `PropsUtil`, `--add-opens`)
- Ako demo overiť ručne (checklist) a čo z toho pri migrácii premeniť na automatické testy

---

## Teória v skratke

### Prečo je testovanie v Liferay ťažšie

Spring Boot aplikáciu spustíš v teste celú (`@SpringBootTest`) za pár sekúnd, s reálnou DB v Testcontaineri. Liferay kód je naopak **zviazaný s bežiacim portálom**:

- komponenty dostávajú závislosti od OSGi (`@Reference`) a mimo kontajnera nikto nič neinjektuje,
- statické utility (`ParamUtil`, `PropsUtil`, `*LocalServiceUtil`) sa pri inicializácii pozerajú do portálu,
- portál štartuje minúty, takže „spustiť celý Liferay v teste“ je drahé.

### Typy testov v Liferay projektoch

| Typ | Ako | Rýchlosť | V deme |
|---|---|---|---|
| **Unit test** | JUnit + Mockito, trieda sa vytvorí cez `new`, závislosti sú mocky | sekundy | **áno, 15 testov** |
| **Integračný test** | Liferay `testIntegration` (Arquillian): test sa nasadí ako bundle do **bežiaceho** portálu a volá reálne služby | minúty, potrebuje portál | nie. Service Builder vygeneruje kostru `TaskPersistenceTest` do `task-test/`, ktorú v deme nepoužívame ([kapitola 06](06_service_builder.md)) |
| **Funkčný / E2E test** | prehliadač (Selenium, Playwright…) proti bežiacemu portálu | minúty | nie, nahrádza ho ručný checklist nižšie |
| **Ručné overenie** | Gogo shell, `curl`, prehliadač | – | **áno, kapitoly 04–08** |

V praxi majú Liferay projekty často **málo automatických testov** a veľa ručného overovania. Pri migrácii je to riziko aj príležitosť: nové Spring Boot API sa dá pokryť testami oveľa ľahšie ([kapitola 10](10_spring_boot_projekt.md)).

---

## Krok po kroku

Z adresára `liferay-workspace` (s nastaveným `GRADLE_USER_HOME`, [kapitola 02](02_predpoklady_a_setup.md)):

### 1. Spusti všetky unit testy

```bash
./gradlew clean test
```

Očakávaný koniec výstupu:

```
BUILD SUCCESSFUL in 36s
234 actionable tasks: 76 executed, 158 up-to-date
```

Liferay portál pri tom **nemusí bežať**, testy sú čisté unit testy.

### 2. Koľko testov prebehlo

Gradle pri úspechu počty nevypisuje. Zistíš ich z XML reportov:

```bash
find modules -path '*test-results/test/*.xml' | xargs grep -ho 'testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"'
```

```
testsuite name="com.example.greeting.impl.GreetingCommandTest" tests="3" skipped="0" failures="0" errors="0"
testsuite name="com.example.greeting.alt.FriendlyGreetingServiceTest" tests="1" skipped="0" failures="0" errors="0"
testsuite name="com.example.task.service.base.TaskLocalServiceImplTest" tests="7" skipped="0" failures="0" errors="0"
testsuite name="com.example.task.web.command.PortletCommandsTest" tests="4" skipped="0" failures="0" errors="0"
```

Spolu **15 testov, 0 chýb**. HTML report pre každý modul je v `modules/<modul>/build/reports/tests/test/index.html`, napríklad:

```
modules/task/task-service/build/reports/tests/test/index.html
```

### 3. Spusti jednu testovaciu triedu

```bash
./gradlew :modules:task:task-service:test --tests '*TaskLocalServiceImplTest'
```

```
> Task :modules:task:task-service:test
BUILD SUCCESSFUL in 13s
```

Ak sa test nespustí (`UP-TO-DATE`), Gradle si myslí, že sa nič nezmenilo. Vynútiš ho cez `--rerun-tasks` (prebuduje všetko) alebo `cleanTest` pred `test`.

---

## Kód z repa: ako sa testuje Liferay kód

### Trik 1: `@Reference` polia naplníš ručne

[`TaskLocalServiceImplTest.java`](../liferay-workspace/modules/task/task-service/src/test/java/com/example/task/service/base/TaskLocalServiceImplTest.java):

```java
package com.example.task.service.base;      // ← ZÁMERNE balíček base triedy

class TaskLocalServiceImplTest {

	@BeforeEach
	void setUp() {
		_service.counterLocalService = _counterLocalService;   // protected pole z TaskLocalServiceBaseImpl
		_service.taskPersistence = _taskPersistence;
	}

	@Test
	@DisplayName("Vytvoří úkol s ID z Counteru a daty ze ServiceContextu")
	void should_createTask_whenTitleValid() throws Exception {
		Task task = mock(Task.class);

		when(_counterLocalService.increment(Task.class.getName())).thenReturn(42L);
		when(_taskPersistence.create(42L)).thenReturn(task);
		when(_taskPersistence.update(task)).thenReturn(task);

		ServiceContext serviceContext = new ServiceContext();
		serviceContext.setCompanyId(1L);
		serviceContext.setScopeGroupId(2L);
		serviceContext.setUserId(3L);

		assertSame(task, _service.addTask("  Learn OSGi  ", serviceContext));

		verify(task).setTitle("Learn OSGi");    // trim
		verify(task).setGroupId(2L);
		verify(task).setDone(false);
	}

	private final CounterLocalService _counterLocalService = mock(CounterLocalService.class);
	private final TaskLocalServiceImpl _service = new TaskLocalServiceImpl();      // bez OSGi
	private final TaskPersistence _taskPersistence = mock(TaskPersistence.class);
}
```

- Závislosti (`taskPersistence`, `counterLocalService`) sú v **vygenerovanej** `TaskLocalServiceBaseImpl` ako `protected` polia s `@Reference`. Test leží v balíčku `...service.base`, aby k nim mal prístup, a vloží do nich mocky.
- V Spring Boot by si to vyriešil konštruktorovou injekciou: `new TaskService(repositoryMock, properties)`.

Testované scenáre (7): vytvorenie, prázdny title, `null` title, toggle, toggle neexistujúcej úlohy, finder `Done`, stránkovanie a počet.

### Trik 2: `@Reference` cez reflexiu

[`GreetingCommandTest.java`](../liferay-workspace/modules/greeting-impl/src/test/java/com/example/greeting/impl/GreetingCommandTest.java): pole `_greetingService` je `private volatile`, takže ho test nastaví reflexiou:

```java
field.set(command, (GreetingService)name -> "Hi " + name);   // lambda ako fake služba
assertEquals("Hi Erich", command.hello("Erich"));
```

A dynamické bind/unbind metódy zavolá priamo, presne ako by ich volal OSGi runtime:

```java
command.addGreetingService(defaultService);
assertEquals("DefaultGreetingService: Hello, Erich!", command.all("Erich"));

command.removeGreetingService(defaultService);
assertTrue(command.all("Erich").isEmpty());
```

### Trik 3: `PropsUtil` mock pre statické utility

[`PortletCommandsTest.java`](../liferay-workspace/modules/task-web/src/test/java/com/example/task/web/command/PortletCommandsTest.java):

```java
@BeforeAll
static void setUpClass() {
	// ParamUtil pri inicializácii číta portal.properties cez PropsUtil,
	// mimo portálu treba podstrčiť mock
	PropsUtil.setProps(mock(Props.class));
}

@Test
@DisplayName("Uložení preferencí ořízne nekladnou hodnotu na 1 a přepne do VIEW")
void should_storeMinimumOne_whenPageSizeNotPositive() throws Exception {
	PortletPreferences preferences = mock(PortletPreferences.class);
	ActionRequest actionRequest = mock(ActionRequest.class);
	ActionResponse actionResponse = mock(ActionResponse.class);

	when(actionRequest.getPreferences()).thenReturn(preferences);
	when(actionRequest.getParameter("pageSize")).thenReturn("0");

	new SavePreferencesMVCActionCommand().doProcessAction(actionRequest, actionResponse);

	verify(preferences).setValue("pageSize", "1");
	verify(preferences).store();
	verify(actionResponse).setPortletMode(PortletMode.VIEW);
}
```

Portlet API (`ActionRequest`, `PortletPreferences`, `RenderRequest`) sú rozhrania, takže sa dajú mockovať. Ťažšie sú statické Liferay utility.

### Trik 4: `--add-opens` v `build.gradle`

Root [`build.gradle`](../liferay-workspace/build.gradle):

```groovy
subprojects {
	plugins.withId("java") {
		dependencies {
			testImplementation group: "com.liferay.portal", name: "release.portal.api"
			testImplementation platform("org.junit:junit-bom:5.14.4")
			testImplementation "org.junit.jupiter:junit-jupiter"
			testImplementation "org.mockito:mockito-core:5.24.0"
			testRuntimeOnly "org.junit.platform:junit-platform-launcher"
		}

		test {
			useJUnitPlatform()
			// Liferay kernel (StringBundler) siaha na JDK internals, portál štartuje s rovnakým --add-opens
			jvmArgs "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED"
		}
	}
}
```

- Liferay API je v module `compileOnly` (dodá ho portál). Pre testy ho treba pridať ako `testImplementation`, inak triedy pri behu testu chýbajú.
- Podľa [README](../README.md#problémy-nájdené-počas-buildu-dobré-war-stories-na-pohovor) bez `--add-opens` testy padali na `StringBundler` (v tejto session som to bez nich znova neskúšal).

### Čo unit test v deme našiel

Z README: unit test `should_throwTitleException_whenTitleBlank` odhalil, že Liferay `Validator.isBlank("   ")` vracia v GA132 `false`. Title z medzier by teda prešiel validáciou. Oprava: čistá Java `String.isBlank()` (komentár v `TaskLocalServiceImpl`). Dobrý príklad, prečo aj triviálna validácia potrebuje test.

---

## Ručný checklist (regresia pred migráciou)

Všetky body sú overené v predchádzajúcich kapitolách. Pri migrácii sa z nich stávajú **akceptačné kritériá** pre nové API: čo fungovalo v Liferay, musí fungovať aj v Spring Boot.

| # | Čo overiť | Ako | Očakávané | Kapitola |
|---|---|---|---|---|
| 1 | Všetky bundly bežia | `./gogo.sh "lb com.example"` | 7× `Active` | 04 |
| 2 | Tabuľka existuje | `psql ... '\d demo_task'` | 7 stĺpcov, indexy na `done`, `groupid` | 04 |
| 3 | Ranking služieb | `greeting:hello`, `stop`/`start` alt bundlu | prepínanie 200 ↔ 100 | 05 |
| 4 | Portlet render | otvor `/web/guest/task-demo` | tabuľka, `N task(s), showing max 10` | 07 |
| 5 | Portlet action add | formulár Add Task | nová úloha. Prázdny title → `Task title is required.` | 07 |
| 6 | Portlet action toggle | tlačidlo Toggle | ✔ sa prepne | 07 |
| 7 | Portlet resource | Load JSON | JSON pole úloh | 07 |
| 8 | EDIT mód | Preferences → Page Size 2 | `showing max 2`. Hosť nemá prístup. | 07 |
| 9 | REST GET/POST/PATCH | `curl` na `/o/tasks` | 200 / 201 / 400 / 404, anonym 403 | 08 |
| 10 | Unit testy | `./gradlew test` | 15/15 | 09 |

Nájdené chyby, ktoré checklist odhalil (a ktoré musí nové riešenie riešiť lepšie):
- hosť smie pridávať a prepínať úlohy cez portlet ([kapitola 07](07_portlet_mvc.md)),
- REST filter `done` ignoruje `groupId` ([kapitola 08](08_rest_v_liferay.md)),
- nevalidný JSON v REST vráti `200` s prázdnym telom ([kapitola 08](08_rest_v_liferay.md)).

---

## Spring Boot ekvivalent

Detaily v [kapitole 10](10_spring_boot_projekt.md). Porovnanie:

| Liferay | Spring Boot (`spring-boot-tasks`) |
|---|---|
| Unit test: `new TaskLocalServiceImpl()` + ručné nastavenie `protected` polí | `new TaskService(mockRepository, properties)`, konštruktorová injekcia |
| `@Reference` cez reflexiu | netreba, závislosti idú cez konštruktor |
| `PropsUtil.setProps(mock(...))` kvôli statickým utilitám | netreba, žiadne statické utility s globálnym stavom |
| `--add-opens` pre testy | netreba |
| Integračný test = nasadiť do bežiaceho portálu (Arquillian) | `@SpringBootTest` + Testcontainers PostgreSQL, štart v sekundách |
| Test REST = `curl` proti bežiacemu portálu | `MockMvc` v teste (`TaskControllerIntegrationTest`) |
| Test security = ručne (hosť vs. prihlásený) | `SecurityIntegrationTest` s mock JWT |
| Test migrácie dát = ručne v DB | `LiferayMigrationIntegrationTest` (Flyway nad Liferay schémou v Testcontaineri) |

---

## Časté chyby

| Príznak | Príčina | Riešenie |
|---|---|---|
| `NoClassDefFoundError` na Liferay triedu v teste | Liferay API je len `compileOnly` | `testImplementation "com.liferay.portal:release.portal.api"` (v deme v root `build.gradle`) |
| `NullPointerException` na `taskPersistence` v teste | `@Reference` pole nikto nenaplnil (nie je OSGi) | Nastaviť mock ručne (test v balíčku base triedy) alebo reflexiou |
| `NullPointerException` v `ParamUtil`/`PropsUtil` | Statická utilita hľadá portál | `PropsUtil.setProps(mock(Props.class))` v `@BeforeAll` |
| `InaccessibleObjectException` / chyby okolo `StringBundler` | JDK 17+ bez `--add-opens` | `jvmArgs "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED", ...` |
| Testy sa nespustia (`UP-TO-DATE`) | Gradle cache | `./gradlew cleanTest test` |
| Testy prechádzajú, v portáli to nefunguje | Unit test nepokryje OSGi zapojenie (chýbajúca služba, zlý `mvc.command.name`) | Ručný checklist / integračný test, `scr:info` v Gogo |

---

## Otázky na pohovor

**Ako sa testuje Liferay kód?**
Unit testy s JUnit a Mockito: komponent sa vytvorí cez `new` a `@Reference` závislosti sa nahradia mockmi. Integračné testy (`testIntegration`, Arquillian) bežia v nasadenom portáli. Funkčné testy idú cez prehliadač. V praxi je veľa ručného testovania, lebo integračné testy sú pomalé a zložité na infraštruktúru.

**Aké sú typické problémy s unit testami v Liferay?**
Statické utility (`*Util`, `PropsUtil`, `ParamUtil`) s globálnym stavom, field injection cez `@Reference` (protected/private polia), potreba Liferay API na test classpath a `--add-opens` na novších JDK.

**Ako by si zabezpečil, že migrácia nič nerozbila?**
Pred migráciou spíšem správanie pôvodnej aplikácie (checklist, prípadne zaznamenané requesty a odpovede) ako akceptačné kritériá. Nové API pokryjem unit, integračnými (Testcontainers) a security testami. Migráciu dát testujem na kópii produkčnej schémy (v deme `liferay-legacy-schema.sql`). Počas strangler fig prechodu porovnávam odpovede starého a nového API.

**Čo je výhoda Spring Boot z pohľadu testovania?**
Konštruktorová injekcia, žiadny globálny statický stav, `@SpringBootTest` a test slices (`@WebMvcTest`, `@DataJpaTest`), Testcontainers s reálnou DB a štart kontextu v sekundách. Testovať sa dá všetko v CI bez bežiaceho portálu.

---

**Ďalej:** [10 – Spring Boot projekt](10_spring_boot_projekt.md)
