# 13 – Otázky na pohovor

## Čo sa naučíš

- Najpravdepodobnejšie otázky o migrácii Liferay → Spring Boot, zoskupené podľa tém
- Krátke odpovede, ktoré vieš povedať za 30 až 60 sekúnd
- Pri každej téme odkaz na kód v repe, aby si vedel povedať „v demo projekte som to robil takto“
- „War stories“: skutočné problémy z buildu demo projektu, ktoré na pohovore znejú dôveryhodne

---

## Ako s touto kapitolou pracovať

- Odpovede sú **kostra**, nie text naspamäť. Povedz ich vlastnými slovami.
- Ku každej odpovedi sa snaž pridať **konkrétny príklad** z dema. „Videl som to v kóde“ znie lepšie ako „čítal som o tom“.
- Ak otázke nerozumieš alebo odpoveď nevieš, povedz, ako by si to zistil (Gogo shell, `diag`, dokumentácia, porovnanie výstupov). Úprimnosť je lepšia ako vymýšľanie.
- Nakoniec si prejdi sekciu **Cvičenie: simulácia pohovoru** a odpovedaj nahlas.

---

## 1. Liferay všeobecne

**Čo je Liferay a na čo sa používa?**
Java portálová platforma (CE zadarmo, DXP platená) na intranety, zákaznícke portály a firemné weby. Obsahuje používateľov, obsah, dokumenty, vyhľadávanie a workflow. Vývojári ju rozširujú OSGi modulmi. Viac v [kapitole 01](01_co_je_liferay.md).

**Aká je architektúra Liferay 7.x?**
Tomcat, v ňom webová aplikácia portálu, vnútri OSGi kontajner, kde beží väčšina funkcionality aj vlastné moduly. Dáta v relačnej DB, vyhľadávanie cez Elasticsearch. V deme: Docker image `liferay/portal:7.4.3.132-ga132` + PostgreSQL 16 ([`docker-compose.yml`](../liferay-workspace/docker-compose.yml)).

**Ako sa Liferay konfiguruje?**
`portal-ext.properties`. V Dockeri cez env premenné `LIFERAY_*`: bodka → `_PERIOD_`, veľké písmeno → `_UPPERCASE<X>`. Napríklad `jdbc.default.url` je `LIFERAY_JDBC_PERIOD_DEFAULT_PERIOD_URL`. Moduly majú OSGi konfigurácie (System Settings, `*.config` súbory).

---

## 2. OSGi

**Čo je OSGi Declarative Services a ako sa líši od Spring DI?**
DS registruje komponenty ako služby v OSGi service registry. Služby sa môžu objaviť a zmiznúť za behu (dynamické referencie, ranking, cardinality). Spring vytvorí statický kontext pri štarte. Obe robia IoC, ale OSGi rieši aj modularitu a životný cyklus modulov (install, start, stop, update bez reštartu JVM). Viac v [kapitole 05](05_osgi_a_gogo_shell.md).

**Čo robí `service.ranking` a policy `DYNAMIC` + `GREEDY`?**
Pri viacerých implementáciách vyhráva najvyšší ranking. `GREEDY` znamená, že sa komponent prepojí na lepšiu službu, keď sa objaví. `DYNAMIC` znamená, že to spraví bez reštartu komponentu. Predvolené `@Reference` je `STATIC` + `RELUCTANT`, teda sa neprepne. V deme: `DefaultGreetingService` (100) vs. `FriendlyGreetingService` (200), konzument [`GreetingCommand.java`](../liferay-workspace/modules/greeting-impl/src/main/java/com/example/greeting/impl/GreetingCommand.java). V Springu: `@Primary`, `@Qualifier`, `@Order`, `@ConditionalOnProperty`.

**Čo je Export-Package / Import-Package a čo sa stane, keď import chýba?**
Bundle vidí iba importované balíčky, ktoré iný bundle exportuje s vyhovujúcou verziou. Bez nich zostane `Installed`, nie `Resolved`/`Active`. `diag <id>` v Gogo shelli ukáže, čo chýba. V deme `greeting-api` exportuje rozhranie a `greeting-impl` neexportuje nič ([`bnd.bnd`](../liferay-workspace/modules/greeting-impl/bnd.bnd)).

**Ako zistíš, prečo modul nefunguje?**
Gogo shell: `lb` (je bundle `Active`?), `diag <id>` (chýbajúce importy), `services` (je služba zaregistrovaná?). V deme cez [`gogo.sh`](../liferay-workspace/gogo.sh), lebo Gogo počúva len vnútri kontajnera. Spring obdoba: Actuator (`/actuator/beans`, `/actuator/conditions`).

**Čo sa stratí, keď prejdeme z OSGi na Spring Boot?**
Hot deploy jednotlivých modulov a výmena služieb za behu. Spring kontext je po štarte statický, zmena = nové nasadenie celej aplikácie. Modularitu treba riešiť na úrovni buildu (Maven moduly, JPMS, ArchUnit testy).

---

## 3. Service Builder a perzistencia

**Čo generuje Service Builder a kam patrí vlastný kód?**
Generuje model, persistence (Hibernate), local/remote service, bázové implementácie, SQL skripty a výnimky. Vlastný kód patrí iba do `*LocalServiceImpl` / `*ServiceImpl` a `*Impl` modelu, ostatné sa pri `buildService` pregeneruje. V deme: [`service.xml`](../liferay-workspace/modules/task/task-service/service.xml) a [`TaskLocalServiceImpl.java`](../liferay-workspace/modules/task/task-service/src/main/java/com/example/task/service/impl/TaskLocalServiceImpl.java). Viac v [kapitole 06](06_service_builder.md).

**Ako by si migroval Service Builder entitu do Spring Bootu?**
JPA entita nad prevzatou štruktúrou tabuľky, Spring Data repository (findery → derived queries, `<finder name="Done">` → `findByDone`), service s `@Transactional`. DDL prevezme Flyway (V1 = súčasný stav, ďalej zmeny). Zachovať ID a posunúť sekvencie, vyriešiť `companyId`/`groupId`, UTC. V deme: [`Task.java`](../spring-boot-tasks/src/main/java/com/example/tasks/task/Task.java), [`TaskRepository.java`](../spring-boot-tasks/src/main/java/com/example/tasks/task/TaskRepository.java).

**Ako fungujú transakcie v Liferay vs. Spring?**
V Liferay automaticky: `*LocalServiceImpl` je registrovaná ako `AopService` a každá public metóda beží v transakcii cez AOP proxy. V Springu explicitne `@Transactional` ([`TaskService.java`](../spring-boot-tasks/src/main/java/com/example/tasks/task/TaskService.java)).

**Odkiaľ berie Liferay ID záznamov?**
Z tabuľky `Counter` cez `counterLocalService.increment()`, nie zo sekvencie DB. Po migrácii treba v novej DB posunúť identity sekvenciu za `max(id)`.

---

## 4. Portlety a web vrstva

**Aký je rozdiel medzi action, render a resource fázou portletu?**
- **Action** mení stav, beží iba pre jeden portlet, nasleduje redirect a render.
- **Render** je idempotentný, vracia HTML fragment pre každý portlet na stránke.
- **Resource** vracia dáta (JSON, súbor) mimo renderu stránky, typicky cez AJAX.
- V REST: action = POST/PUT/PATCH/DELETE, render aj resource = GET.

V deme: [`task-web/.../command/`](../liferay-workspace/modules/task-web/src/main/java/com/example/task/web/command). Viac v [kapitole 07](07_portlet_mvc.md).

**Prečo je trieda `TaskPortlet` prázdna?**
`MVCPortlet` deleguje prácu na `MVCRenderCommand`, `MVCActionCommand` a `MVCResourceCommand` komponenty podľa `javax.portlet.name` a `mvc.command.name`. Konfigurácia portletu (kedysi `portlet.xml`) je vo vlastnostiach `@Component` ([`TaskPortlet.java`](../liferay-workspace/modules/task-web/src/main/java/com/example/task/web/portlet/TaskPortlet.java)).

**Čo s PortletPreferences?**
Rozlíš, či ide o globálnu konfiguráciu (`application.yml`, `@ConfigurationProperties`), per-používateľské nastavenie (tabuľka + endpoint) alebo nastavenie UI komponentu (frontend, CMS). V deme sa `pageSize` z EDIT módu stal `tasks.*` v [`TaskProperties.java`](../spring-boot-tasks/src/main/java/com/example/tasks/config/TaskProperties.java). Dáta sú v Liferay v tabuľkách `PortletPreferences` a `PortletPreferenceValue`.

**Prečo stateless API namiesto portlet session?**
Horizontálne škálovanie bez sticky sessions, jednoduchšie cache a testy, jasný kontrakt pre Angular aj mobil. Stav nesie token (identita), URL a body. CSRF pri čisto token-based API (bez cookies) odpadá.

**Čo nahradí `SessionErrors` a `<liferay-ui:error>`?**
HTTP status + ProblemDetail (RFC 9457) z globálneho exception handlera ([`GlobalExceptionHandler.java`](../spring-boot-tasks/src/main/java/com/example/tasks/common/GlobalExceptionHandler.java)). Chybu zobrazí frontend.

---

## 5. REST

**REST Builder vs. JAX-RS komponent v Liferay?**
JAX-RS whiteboard je jednoduchý: jedna trieda, plná kontrola ([`TaskRestApplication.java`](../liferay-workspace/modules/task-rest/src/main/java/com/example/task/rest/application/TaskRestApplication.java)). REST Builder generuje z OpenAPI YAML DTO, resource, GraphQL, stránkovanie, filtre, batch a OAuth2 scopes. Je to štandard pre Liferay headless API (`/o/headless-*`), ale potrebuje dva moduly a generovanie. Pri migrácii sú headless API dobrý zdroj kontraktu pre nové Spring endpointy. Viac v [kapitole 08](08_rest_v_liferay.md).

**Ako je zabezpečený REST v deme?**
Basic Auth a `liferay.oauth2=false`, čo vypína kontrolu OAuth2 scope. **Len pre demo.** Anonym dostane 403 (`auth.verifier.guest.allowed=false`).

**Na čo si dať pozor pri JSON?**
Liferay serializuje `long` ako string (`"id":"1"`), Jackson ako číslo (`"id":1`). Klient, ktorý očakáva string, môže po migrácii padnúť. Treba to rozhodnúť v API kontrakte.

---

## 6. Security

**Ako nahradíš Liferay role a permissions?**
Identita a roly v IdP (Keycloak, OIDC), API ako OAuth2 resource server. Hrubé oprávnenia ako roly v JWT a `requestMatchers` / `@PreAuthorize`. Per-záznam oprávnenia (`ResourcePermission`) cez doménové pravidlá alebo ACL/ReBAC. V deme: `VIEW` → `tasks-viewer`, `ADD_ENTRY`/`UPDATE`/`DELETE` → `tasks-editor` ([`SecurityConfig.java`](../spring-boot-tasks/src/main/java/com/example/tasks/config/SecurityConfig.java), [`KeycloakRealmRoleConverter.java`](../spring-boot-tasks/src/main/java/com/example/tasks/config/KeycloakRealmRoleConverter.java)). Viac v [kapitole 11](11_migracia_dat_a_security.md).

**Čo s heslami používateľov?**
Liferay ich hashuje (PBKDF2 a ďalšie). Keycloak vie importovať hash, ak pozná algoritmus, inak vynútený reset. Ak Liferay používal LDAP, Keycloak sa napojí na ten istý LDAP (federation).

**Ako zabezpečiť SSO počas prechodu?**
Liferay aj nová aplikácia sú klienti toho istého Keycloak realmu (OIDC). Používateľ sa prihlási raz.

**Ako testuješ security?**
Integračný test s mockovaným `JwtDecoder` a `jwt()` zo spring-security-test, overenie 401/403/200/201 ([`SecurityIntegrationTest.java`](../spring-boot-tasks/src/test/java/com/example/tasks/config/SecurityIntegrationTest.java)).

---

## 7. Dáta a migrácia dát

**Ako migruješ dáta a zároveň udržíš oba systémy v prevádzke?**
Zdieľaná DB s oddelenou schémou (ako v deme), CDC (Debezium) z Liferay DB do novej, alebo dual-write cez outbox. Idempotentné migračné skripty, kontrolné súčty a počty, prepínanie po doménach.

**Ako to robí demo?**
Spring Boot beží nad tou istou PostgreSQL ako Liferay, vo vlastnej schéme `tasks`. [`V1__create_task.sql`](../spring-boot-tasks/src/main/resources/db/migration/V1__create_task.sql) vytvorí tabuľku, [`V2__import_liferay_tasks.sql`](../spring-boot-tasks/src/main/resources/db/migration/V2__import_liferay_tasks.sql) skopíruje `public.demo_task`, zachová ID, nahradí NULL hodnoty a posunie sekvenciu. Bez Liferay tabuľky sa import preskočí.

**Na čo si dať pozor pri Liferay dátach v DB?**
ID z `Counter`, `companyId`/`groupId`, lokalizované polia ako XML, `uuid_` a `externalReferenceCode` (dobré na idempotentný import), `ctCollectionId` (publications), soft-delete cez status (workflow), UTC dátumy.

**Ako testuješ migráciu?**
Unit testy služieb (Mockito), integračné testy s Testcontainers (reálny PostgreSQL + Flyway vrátane importu z legacy schémy, [`LiferayMigrationIntegrationTest.java`](../spring-boot-tasks/src/test/java/com/example/tasks/migration/LiferayMigrationIntegrationTest.java)), contract testy API. Porovnanie výstupov starého a nového API na kópii produkčných dát, smoke testy po prepnutí v gateway.

**Ako migruješ vyhľadávanie a Documents & Media?**
Search: vlastný index v Elasticsearch/OpenSearch, plnený eventmi (outbox) + reindex job, znova vyriešiť filtrovanie podľa oprávnení a lokalizáciu. D&M: binárky zo store do S3/MinIO, metadáta a verzie z `DLFileEntry`/`DLFileVersion` do vlastnej tabuľky, 301 pre staré `/documents/...` URL. Viac v [kapitole 12](12_migracna_strategia.md).

---

## 8. Stratégia

**Ako by si postupoval pri migrácii celej Liferay aplikácie?**
Inventár: portlety, služby, tabuľky, integrácie, oprávnenia, obsah. Strangler fig cez gateway: najprv read-only API nad rovnakou DB, potom presun zápisov, dát a používateľov. Frontend po častiach (Angular), vypnutie Liferay až nakoniec. Každý krok s cestou späť.

**Čo je strangler fig?**
Nová aplikácia rastie vedľa starej, reverse proxy na ňu postupne presmerúva URL. Malé, overiteľné kroky. Pri probléme stačí vrátiť smerovanie.

**Čo s URL?**
Mapovacia tabuľka starých (`/web/guest/...`, `p_p_id`) a nových URL, 301 presmerovania, kontrola odkazov v obsahu.

**Čo migruješ ako prvé?**
Niečo malé, s jasnými hranicami a bez väzby na funkcie portálu (napríklad aplikačný portlet ako Tasks). Najprv read-only čítanie, až potom zápisy.

---

## 9. Prečo a kedy (ne)migrovať

**Prečo firmy migrujú z Liferay na Spring Boot?**
Typicky kvôli licenčným nákladom DXP, zložitosti upgradov medzi verziami, nedostatku Liferay vývojárov, potrebe moderného SPA frontendu (Angular, React) a nasadzovaniu v kontajneroch. Formuluj to opatrne ako typické dôvody, nie ako fakt o konkrétnej firme.

**Kedy by si migráciu neodporúčal, alebo len čiastočnú?**
Keď aplikácia intenzívne využíva funkcie portálu: CMS a web content, Documents & Media, workflow, jemné oprávnenia, vyhľadávanie. Ich prepísanie môže stáť viac ako licencia. Rozumný kompromis: aplikačnú logiku presunúť do Spring Boot, obsah nechať v Liferay alebo v CMS a prepojiť ich cez API a SSO.

**Čo je najväčšie riziko takej migrácie?**
Podceniť funkcie, ktoré Liferay dával implicitne: používatelia a oprávnenia, CMS, D&M, vyhľadávanie, workflow, URL. Business logika v `*LocalServiceImpl` je zvyčajne tá jednoduchšia časť.

---

## War stories z buildu demo projektu

Toto sú **skutočné problémy**, ktoré sa objavili pri stavbe dema. Na pohovore fungujú dobre, lebo ukazujú praktickú skúsenosť. Formát: problém → príčina → riešenie → čo si z toho beriem.

**1. Service Builder vygeneroval kód, ktorý sa neskompiloval**
- Problém: kompilácia `task-service` padla, `TaskPersistence.cacheResult` v GA132 neexistuje.
- Príčina: Workspace plugin stiahol najnovší Service Builder (1.0.561), ktorý generuje kód pre novšie API portálu.
- Riešenie: pin na 1.0.496 (verzia z tagu GA132) + `resolutionStrategy.force` ([`task-service/build.gradle`](../liferay-workspace/modules/task/task-service/build.gradle)).
- Ponaučenie: v Liferay musia verzie nástrojov sedieť s verziou portálu. Pri migrácii aj pri upgrade treba verzie pinovať.

**2. `Validator.isBlank("   ")` vracia v GA132 `false`**
- Problém: unit test odhalil, že title zložený z medzier prejde validáciou.
- Riešenie: čisté Java `String.isBlank()` ([`TaskLocalServiceImpl.java`](../liferay-workspace/modules/task/task-service/src/main/java/com/example/task/service/impl/TaskLocalServiceImpl.java)).
- Ponaučenie: Liferay utility nesprávajú vždy tak, ako napovedá názov. Pri migrácii logiky treba testy, ktoré zachytia súčasné správanie.

**3. EDIT mód portletu zobrazoval view**
- Problém: `MVCRenderCommand` s `mvc.command.name=/` sa použije vo všetkých portlet módoch, takže `edit-template` sa nikdy neuplatnil.
- Riešenie: kontrola `PortletMode.EDIT` v [`ViewTasksMVCRenderCommand.java`](../liferay-workspace/modules/task-web/src/main/java/com/example/task/web/command/ViewTasksMVCRenderCommand.java).
- Ponaučenie: konvencie Liferay frameworku majú skryté pravidlá. Ďalší dôvod pre jednoduchý REST kontrakt.

**4. Unit testy Liferay kódu mimo portálu**
- Problém: testy padali mimo bežiaceho portálu.
- Riešenie: `--add-opens java.base/java.lang.invoke` (kvôli `StringBundler`) v [`build.gradle`](../liferay-workspace/build.gradle) a `PropsUtil.setProps(mock)` pre `ParamUtil`.
- Ponaučenie: Liferay kód je silno previazaný so statickými utilitami portálu. Testovateľnosť je jeden z argumentov pre migráciu. Viac v [kapitole 09](09_testovanie_liferay.md).

**5. `long` ako string v JSON**
- Problém: Liferay vracia `"id":"1"`, Spring Boot (Jackson) `"id":1`.
- Ponaučenie: potenciálny breaking change pre klientov, treba ho riešiť v API kontrakte.

**6. Posun času (UTC vs. lokálny čas)**
- Problém: bez úpravy by sa migrované a nové záznamy líšili o posun lokálnej zóny (v lete v SR 2 hodiny).
- Príčina: Liferay JVM beží s `-Duser.timezone=GMT`, `createDate` je v DB v UTC. Spring by bez nastavenia ukladal lokálny čas.
- Riešenie: `Instant` + `hibernate.jdbc.time_zone=UTC` ([`application.yml`](../spring-boot-tasks/src/main/resources/application.yml)).
- Ponaučenie: pri migrácii dát vždy overiť časové pásma.

**7. Vytvorenie stránky cez JSON WS padalo**
- Problém: jednoduchší variant `add-layout` padal na `LayoutFriendlyURLException`.
- Riešenie: variant s mapami a `typeSettings` ([`create-demo-page.sh`](../liferay-workspace/create-demo-page.sh)).
- Ponaučenie: staré Liferay API (JSON WS) majú preťažené metódy s rôznym správaním. Lepšie je overiť variant pokusom.

### Zistenia pri písaní poznámok (overené v kapitolách 06–11)

**8. Kolízia ID pri súbežnom behu Liferay a Spring Boot**
- Problém: po importe dát Spring Boot aj Liferay pridelili ID 103 dvom rôznym úlohám.
- Príčina: Liferay Counter mal rezervovaný blok 101–200, nová sekvencia začínala za max importovaným ID (102). Systémy o sebe nevedia.
- Riešenie: cutover (zastaviť zápis v Liferay), oddelené rozsahy ID/UUID alebo jeden zdroj pravdy ([kapitola 11](11_migracia_dat_a_security.md)).
- Ponaučenie: jednorazový import a súbežný beh sa vylučujú. Stratégia synchronizácie musí byť rozhodnutá vopred.

**9. Liferay REST vrátil `200` s prázdnym telom pri nevalidnom JSON**
- Problém: `POST /o/tasks` s telom `nie json` → `HTTP 200`, `Content-Length: 0`. V logu `JSONException`.
- Príčina: nezachytená výnimka v JAX-RS metóde, bez globálneho exception handlera.
- Riešenie v Spring Boot: `ResponseEntityExceptionHandler` → `400 ProblemDetail` automaticky ([kapitola 08](08_rest_v_liferay.md)).
- Ponaučenie: pri migrácii API zdokumentovať aj chybové stavy, nielen šťastnú cestu. Klienti sa niekedy spoliehajú aj na chybné správanie.

**10. Chýbajúce kontroly oprávnení a únik medzi sites**
- Problém: neprihlásený hosť vedel cez portlet pridať aj prepnúť úlohu (v DB `userid` = guest). REST filter `done` vracal úlohy zo všetkých sites, lebo ignoroval `groupId`.
- Ponaučenie: pri migrácii nestačí preniesť logiku. Treba auditovať, **kde chýbajú** kontroly, a v novom riešení ich doplniť (Spring Security, testy). Kapitoly [07](07_portlet_mvc.md) a [08](08_rest_v_liferay.md).

**11. Diakritika rozbitá cez `curl` na Windows**
- Problém: `curl -d '{"title":"Kúpiť"}'` v Git Bash uložil do DB `K�pit`.
- Riešenie: JSON zo súboru (`--data-binary @task.json`).
- Ponaučenie: pri migrácii dát vždy kontrolovať kódovanie (UTF-8) celej cesty: klient, HTTP, DB.

---

## Cvičenie: simulácia pohovoru

Odpovedaj nahlas, max. 1 minúta na otázku. Potom si porovnaj odpoveď s textom vyššie.

1. Vysvetli mi, čo je portlet, akoby som bol frontend vývojár.
2. Máme v Liferay 40 portletov a 15 Service Builder entít. Ako začneš?
3. Čo je `service.ranking` a ako by si to isté urobil v Springu?
4. Tabuľka `DEMO_Task` má stĺpce `groupId` a `companyId`. Čo s nimi?
5. Klient REST API po migrácii hlási, že mu nefunguje parsovanie ID. Čo sa mohlo stať?
6. Ako by si overil, že migrované dáta sú správne?
7. Čo bol najzaujímavejší problém, na ktorý si narazil pri práci s Liferay? (Vyber si jednu war story.)
8. Kedy by si zákazníkovi migráciu neodporučil?

---

**Späť na začiatok:** [00 – Osnova](00_osnova.md)
