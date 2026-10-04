# 00 – Osnova: Liferay po lopate (príprava na pohovor)

Cieľ: za pár dní pochopiť Liferay natoľko, aby si vedel **spustiť** demo z tohto repa, **vysvetliť** jeho kód a **namapovať** každý koncept na Spring Boot. Kvôli tomu je pohovor: migrácia Liferay aplikácie na Spring Boot + REST.

Nie je to kompletný kurz Liferay. UI veci (fragmenty, témy, web content) vynechávame, pretože pri migrácii backendu na Spring Boot ich takmer nepotrebuješ.

---

## Ako čítať tieto poznámky

- Kapitoly čítaj **v poradí**. Každá stavia na predchádzajúcej.
- Príkazy reálne spúšťaj. Pri každom je uvedený **očakávaný výstup**, takže hneď vidíš, či ti to funguje.
- Každý príkaz v kapitolách sa pri písaní reálne spúšťa na Windows 11. Ak niečo nešlo overiť, kapitola to výslovne uvedie.
- Kód sa neprepisuje. Kapitoly vysvetľujú existujúce projekty:
  - [`liferay-workspace/`](../liferay-workspace) – Liferay CE 7.4 (GA132)
  - [`spring-boot-tasks/`](../spring-boot-tasks) – Spring Boot 4.1 ekvivalent
- Celkový prehľad a overené verzie sú v [`README.md`](../README.md). Poznámky ho vysvetľujú krok po kroku.

### Štruktúra každej kapitoly

1. **Čo sa naučíš** – 2 až 4 body
2. **Teória v skratke** – max. pár odsekov
3. **Krok po kroku** – príkazy + očakávaný výstup
4. **Kód z repa** – konkrétne súbory s vysvetlením
5. **Spring Boot ekvivalent** – ako by to isté vyzeralo v `spring-boot-tasks`
6. **Časté chyby** – príčina a riešenie
7. **Otázky na pohovor** – krátke otázky s odpoveďou

---

## Mapa kapitol

| # | Kapitola | Čo z nej máš | Spring Boot paralela |
|---|---|---|---|
| 01 | [Čo je Liferay](01_co_je_liferay.md) | Mentálny model: portál, portlet, OSGi, ako je to celé poskladané | Jedna Spring Boot appka vs. „aplikačný server s pluginmi“ |
| 02 | [Predpoklady a setup](02_predpoklady_a_setup.md) | Docker, JDK 21, Gradle bez firemného repa, orientácia vo workspace | Maven wrapper, `pom.xml` |
| 03 | [Spustenie Liferay](03_spustenie_liferay.md) | Bežiaci portál v Dockeri, prihlásenie, admin UI, logy | `./mvnw spring-boot:run` |
| 04 | [Build a deploy modulov](04_build_a_deploy_modulov.md) | `gradlew deploy`, hot deploy, overenie, že modul nabehol | Rebuild a reštart appky, DevTools |
| 05 | [OSGi a Gogo shell](05_osgi_a_gogo_shell.md) | `@Component`, `@Reference`, `service.ranking`, `bnd.bnd`, príkazy `lb`, `diag`, `services` | Spring DI, `@Primary`, `@Qualifier` |
| 06 | [Service Builder](06_service_builder.md) | `service.xml`, čo sa generuje, kam patrí vlastný kód | JPA entita, Spring Data repository, `@Service` |
| 07 | [Portlet (MVCPortlet)](07_portlet_mvc.md) | Render, action a resource fáza, JSP, PortletPreferences, portlet na stránke | `@RestController`, `@ConfigurationProperties` |
| 08 | [REST v Liferay](08_rest_v_liferay.md) | JAX-RS modul, volanie cez `curl`, autentifikácia | `@RestController`, OpenAPI |
| 09 | [Testovanie v Liferay](09_testovanie_liferay.md) | Unit testy (JUnit 5 + Mockito), čo sa testuje ťažko a prečo, manuálne overenie | `@WebMvcTest`, `@SpringBootTest` |
| 10 | [Spring Boot projekt](10_spring_boot_projekt.md) | Spustenie `spring-boot-tasks`, Swagger UI, testy s Testcontainers | – |
| 11 | [Migrácia dát a security](11_migracia_dat_a_security.md) | Flyway import z Liferay tabuľky, Keycloak/OAuth2 namiesto Liferay rolí | Flyway, Spring Security resource server |
| 12 | [Migračná stratégia](12_migracna_strategia.md) | Strangler fig, URL, vyhľadávanie, dokumenty, používatelia a oprávnenia | – |
| 13 | [Otázky na pohovor](13_otazky_na_pohovor.md) | Otázky s odpoveďami + „war stories“ z buildu demo projektu | – |

---

## Slovník pojmov

Netreba sa ho učiť naspamäť. Vráť sa sem, keď v kapitole narazíš na neznáme slovo.

| Pojem | Čo to je | Najbližšia vec v Spring svete |
|---|---|---|
| **Liferay Portal / DXP** | Java platforma na weby a intranety. CE (Community Edition) je zadarmo, DXP je platená verzia. | Hotová aplikácia + framework v jednom |
| **Portál** | Webová aplikácia, ktorá skladá stránku z viacerých nezávislých okienok (portletov). | – |
| **Portlet** | Malá aplikácia, ktorá vyrenderuje **kúsok** stránky. Na jednej stránke ich môže byť viac. Štandard JSR-286 (Portlet 2.0). | Controller, ktorý vracia HTML fragment |
| **Render fáza** | Portlet generuje HTML. Volá sa pri každom zobrazení stránky. | `GET` handler |
| **Action fáza** | Portlet spracuje formulár (zmení dáta). Po nej vždy nasleduje render. | `POST` handler + redirect |
| **Resource fáza** | Portlet vráti ľubovoľné dáta (JSON, súbor) bez renderu celej stránky. | `@GetMapping` vracajúci JSON |
| **PortletPreferences** | Nastavenia jednej inštancie portletu na stránke (napr. počet riadkov). | `@ConfigurationProperties`, prípadne tabuľka nastavení |
| **OSGi** | Štandard pre modulárnu Javu. Aplikácia sa skladá z modulov, ktoré sa dajú za behu inštalovať, spúšťať a vymieňať. | Nič priamo. Spring Boot je jeden monolitický classpath. |
| **Bundle** | Jeden OSGi modul = JAR s extra hlavičkami v `MANIFEST.MF`. | Maven modul / JAR knižnica |
| **bnd / `bnd.bnd`** | Nástroj a konfiguračný súbor, ktorý generuje OSGi hlavičky manifestu. | Časť `pom.xml` |
| **Export-Package / Import-Package** | Ktoré balíčky bundle ponúka iným a ktoré od iných potrebuje. | Java Platform Module System (`module-info.java`) |
| **Declarative Services (DS)** | OSGi spôsob, ako definovať komponenty a ich závislosti anotáciami. | Spring DI |
| **`@Component`** (OSGi) | Označí triedu ako komponent/službu. Pozor: iná anotácia ako Spring `@Component`. | `@Component`, `@Service` |
| **`@Reference`** | Injektuje inú OSGi službu. | `@Autowired` / konštruktorová injekcia |
| **`service.ranking`** | Číslo, ktoré určí, ktorá implementácia vyhrá, ak ich je viac. Vyššie vyhráva. | `@Primary`, `@Order` |
| **Gogo shell** | Konzola do bežiaceho OSGi kontajnera (zoznam bundlov, diagnostika). | Spring Boot Actuator (`/actuator/beans`) |
| **Service Builder** | Liferay generátor perzistentnej a servisnej vrstvy zo súboru `service.xml`. | JPA entita + Spring Data repository + service |
| **LocalService** | Servisná vrstva bez kontroly oprávnení, volaná v rámci servera. | `@Service` trieda |
| **Liferay Workspace** | Gradle projekt so štandardnou štruktúrou pre Liferay moduly a konfiguráciu. | Multi-module Maven/Gradle projekt |
| **Blade CLI** | Liferay nástroj na generovanie modulov a workspace. | Spring Initializr (start.spring.io) |
| **`portal-ext.properties`** | Hlavný konfiguračný súbor portálu (DB, správanie). | `application.yml` |
| **JAX-RS** | Java štandard pre REST (anotácie `@Path`, `@GET`). Liferay ho používa pre REST endpointy. | `@RestController`, `@GetMapping` |
| **REST Builder** | Liferay generátor REST API z OpenAPI YAML. V deme ho nepoužívame (kapitola 08 vysvetlí prečo). | OpenAPI Generator |
| **Site / Page / Layout** | Web v portáli, jeho stránky a ich rozloženie. V DB sa stránka volá `Layout`. | – (rieši frontend) |
| **Company / Group** | V DB: `companyId` = inštancia portálu, `groupId` = site. Objavujú sa v každej tabuľke Service Buildera. | Tenant ID |

---

## Čo potrebuješ mať pripravené

Podrobne to rieši [kapitola 02](02_predpoklady_a_setup.md), tu je len zoznam:

- Docker Desktop s pridelenými aspoň ~4 GB RAM (samotný Liferay potrebuje ~3 GB)
- JDK 21
- Git Bash alebo PowerShell
- Približne 5 GB voľného miesta (Docker image Liferay má cez 1 GB, k tomu Gradle cache)
