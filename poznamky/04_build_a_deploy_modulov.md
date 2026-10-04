# 04 – Build a deploy modulov

## Čo sa naučíš

- Čo presne urobí `./gradlew deploy`
- Ako funguje hot deploy: JAR sa do bežiaceho portálu dostane bez reštartu
- Ako overiť, že modul naozaj nabehol (log, Gogo, DB)
- Ako modul odobrať a znova nasadiť
- Čo je v zbuildenom bundli (`MANIFEST.MF`)

**Predpoklad:** Liferay beží ([kapitola 03](03_spustenie_liferay.md)) a máš nastavený `GRADLE_USER_HOME` ([kapitola 02](02_predpoklady_a_setup.md)).

---

## Teória v skratke

Cesta kódu do portálu:

```
 modules/greeting-impl/src/...java
        |
        |  ./gradlew deploy
        |    1. compileJava       skompiluje triedy
        |    2. jar               bnd pridá OSGi hlavičky do MANIFEST.MF + OSGI-INF/*.xml
        |    3. deploy            skopíruje JAR do bundles/osgi/modules
        v
 liferay-workspace/bundles/osgi/modules/com.example.greeting.impl.jar     (tvoj disk)
        |
        |  Docker volume mount (docker-compose.yml)
        v
 /opt/liferay/osgi/modules/com.example.greeting.impl.jar                  (kontajner)
        |
        |  Liferay File Install (com.liferay.portal.file.install) sleduje priečinok
        |  nový JAR -> install + start, zmenený -> update, zmazaný -> uninstall
        v
 bundle ACTIVE v OSGi kontajneri, v logu "STARTED com.example.greeting.impl_1.0.0"
```

**Hot deploy** = portál beží ďalej, zmení sa len jeden bundle. Ostatné bundly a ostatní používatelia to nepocítia. Pri Spring Boot by si reštartoval celú aplikáciu.

Prečo názov súboru `com.example.greeting.impl.jar`, a nie `greeting-impl.jar`? Workspace pomenúva JAR podľa `Bundle-SymbolicName` z `bnd.bnd`.

---

## Krok po kroku

Z adresára `liferay-workspace`:

```bash
cd liferay-workspace
export GRADLE_USER_HOME=$HOME/.gradle-liferay-demo   # ak si to v tomto okne ešte nenastavil
```

### 1. Nasaď všetky moduly

```bash
./gradlew deploy
```

Očakávaný výstup (skrátený):

```
> Task :modules:greeting-impl-alt:deploy
Files of project ':modules:greeting-impl-alt' deployed to C:\projekty\tmp\liferay-demo\liferay-workspace\bundles\osgi\modules
> Task :modules:task-rest:deploy
...
> Task :modules:task:task-service:deploy
Files of project ':modules:task:task-service' deployed to ...\bundles\osgi\modules
BUILD SUCCESSFUL in 5s
```

### 2. Pozri, čo sa skopírovalo

```bash
ls -la bundles/osgi/modules
```

```
-rw-r--r-- 1 lorma 197609  1609 Oct  4 09:02 com.example.greeting.api.jar
-rw-r--r-- 1 lorma 197609  2551 Oct  4 09:02 com.example.greeting.impl.alt.jar
-rw-r--r-- 1 lorma 197609  4786 Oct  4 09:02 com.example.greeting.impl.jar
-rw-r--r-- 1 lorma 197609 18935 Oct  4 09:02 com.example.task.api.jar
-rw-r--r-- 1 lorma 197609  5224 Oct  4 09:02 com.example.task.rest.jar
-rw-r--r-- 1 lorma 197609 41615 Oct  4 09:02 com.example.task.service.jar
-rw-r--r-- 1 lorma 197609 16625 Oct  4 09:02 com.example.task.web.jar
```

Všimni si veľkosť: pár kilobajtov. Bundle obsahuje **len vlastný kód**, Liferay knižnice sú `compileOnly` a dodá ich portál.

### 3. Over v logu, že bundly nabehli

```bash
docker compose logs liferay --since 2m | grep -E "STARTED|ERROR"
```

Očakávaný výstup (do ~10 s po deployi):

```
... [fileinstall-directory-watcher][BundleStartStopLogger:68] STARTED com.example.greeting.api_1.0.0 [1391]
... [fileinstall-directory-watcher][BundleStartStopLogger:68] STARTED com.example.greeting.impl.alt_1.0.0 [1392]
... [fileinstall-directory-watcher][BundleStartStopLogger:68] STARTED com.example.greeting.impl_1.0.0 [1393]
... [fileinstall-directory-watcher][BundleStartStopLogger:68] STARTED com.example.task.web_1.0.0 [1397]
... [fileinstall-directory-watcher][BundleStartStopLogger:68] STARTED com.example.task.service_1.0.0 [1396]
... [fileinstall-directory-watcher][BundleStartStopLogger:68] STARTED com.example.task.api_1.0.0 [1394]
... [fileinstall-directory-watcher][BundleStartStopLogger:68] STARTED com.example.task.rest_1.0.0 [1395]
```

- `fileinstall-directory-watcher` je vlákno File Install, ktoré si JAR všimlo.
- Číslo v hranatých zátvorkách (`[1391]`) je **ID bundlu**. Budeš ho potrebovať v Gogo shelli (`diag 1391`, `stop 1391`).
- Ak by namiesto `STARTED` chýbal riadok alebo by sa objavil `ERROR` s `Unresolved requirement`, bundle sa nespustil. Riešenie je v [kapitole 05](05_osgi_a_gogo_shell.md).

### 4. Over stav v Gogo shelli

```bash
./gogo.sh "lb com.example"
```

```
g! START LEVEL 20
   ID|State      |Level|Name
 1391|Active     |   10|greeting-api (1.0.0)|1.0.0
 1392|Active     |   10|greeting-impl-alt (1.0.0)|1.0.0
 1393|Active     |   10|greeting-impl (1.0.0)|1.0.0
 1394|Active     |   10|task-api (1.0.0)|1.0.0
 1395|Active     |   10|task-rest (1.0.0)|1.0.0
 1396|Active     |   10|task-service (1.0.0)|1.0.0
 1397|Active     |   10|task-web (1.0.0)|1.0.0
```

Všetkých 7 musí byť **Active**. `gogo.sh` podrobne vysvetľuje kapitola 05.

### 5. Over, že Service Builder vytvoril tabuľku

Pri štarte bundlu `task-service` Liferay zistí, že tabuľka ešte neexistuje, a vytvorí ju podľa `tables.sql`:

```bash
docker compose exec -T postgres psql -U liferay -d lportal -c '\d demo_task'
```

```
                         Table "public.demo_task"
   Column   |            Type             | Collation | Nullable | Default
------------+-----------------------------+-----------+----------+---------
 taskid     | bigint                      |           | not null |
 groupid    | bigint                      |           |          |
 companyid  | bigint                      |           |          |
 userid     | bigint                      |           |          |
 createdate | timestamp without time zone |           |          |
 title      | character varying(75)       |           |          |
 done       | boolean                     |           |          |
Indexes:
    "demo_task_pkey" PRIMARY KEY, btree (taskid)
    "ix_8c21c283" btree (groupid)
    "ix_e1cbfbd" btree (done)
```

Index `ix_e1cbfbd` na stĺpci `done` vznikol z `<finder name="Done">` v `service.xml` ([kapitola 06](06_service_builder.md)). `title` má dĺžku 75, čo je predvolená dĺžka `String` stĺpca v Service Builderi.

### 6. Hot deploy naživo: odober a vráť modul

Najprv zavolaj službu cez Gogo príkaz z modulu `greeting-impl`:

```bash
./gogo.sh "greeting:hello Jano"
```

```
g! Ahoj Jano, vitaj v OSGi!
```

Odpovedá `greeting-impl-alt`, lebo má vyšší `service.ranking` (200).

**Odober** bundle `greeting-impl-alt`, stačí zmazať JAR:

```bash
rm bundles/osgi/modules/com.example.greeting.impl.alt.jar
```

V logu sa do pár sekúnd objaví:

```
... [fileinstall-directory-watcher][BundleStartStopLogger:71] STOPPED com.example.greeting.impl.alt_1.0.0 [1392]
```

A služba sa sama prepne na zvyšnú implementáciu (ranking 100):

```bash
./gogo.sh "greeting:hello Jano"
```

```
g! Hello, Jano!
```

**Vráť** ho späť, nasadí sa len jeden modul:

```bash
./gradlew :modules:greeting-impl-alt:deploy
```

```
> Task :modules:greeting-impl-alt:deploy
Files of project ':modules:greeting-impl-alt' deployed to ...\bundles\osgi\modules
BUILD SUCCESSFUL in 2s
```

V logu (môže to trvať až ~20 s, File Install priečinok kontroluje periodicky):

```
... STARTED com.example.greeting.impl.alt_1.0.0 [1398]
```

ID je teraz **1398**, nie 1392. Zmazaním sa bundle odinštaloval, takže je to nová inštalácia s novým ID.

```bash
./gogo.sh "greeting:hello Jano"
```

```
g! Ahoj Jano, vitaj v OSGi!
```

Celý ten čas portál bežal a nikto nič nereštartoval.

### 7. Nasadenie po zmene kódu

Bežný vývojový cyklus:

1. Zmeníš Java súbor v module.
2. `./gradlew :modules:<modul>:deploy` (alebo `./gradlew deploy` pre všetky).
3. File Install zistí zmenený JAR a urobí **update** bundlu (ID zostane rovnaké).
4. Overíš v logu (`STARTED`) a v prehliadači.

---

## Kód z repa: čo je v bundli

Pozri si manifest zbuildeného `greeting-impl`:

```bash
unzip -p bundles/osgi/modules/com.example.greeting.impl.jar META-INF/MANIFEST.MF
```

Výstup (skrátený):

```
Bundle-Name: greeting-impl
Bundle-SymbolicName: com.example.greeting.impl
Bundle-Version: 1.0.0
Import-Package: com.example.greeting.api;version="1.0",java.lang,java.
 lang.invoke,java.util,java.util.concurrent,java.util.function,java.ut
 il.stream
Private-Package: com.example.greeting.impl
Provide-Capability: osgi.service;objectClass:List<String>="com.example
 .greeting.api.GreetingService";...
Require-Capability: osgi.service;filter:="(objectClass=com.example.gre
 eting.api.GreetingService)";effective:=active;cardinality:=multiple,...
Service-Component: OSGI-INF/com.example.greeting.impl.DefaultGreetingS
 ervice.xml,OSGI-INF/com.example.greeting.impl.GreetingCommand.xml
Tool: Bnd-6.4.0.202211291949
```

Porovnaj s tým, čo je v [`greeting-impl/bnd.bnd`](../liferay-workspace/modules/greeting-impl/bnd.bnd): sú tam len 3 riadky (meno, symbolic name, verzia). Zvyšok **dopočítal bnd** z bytecode a anotácií:

| Hlavička | Odkiaľ sa vzala | Význam |
|---|---|---|
| `Import-Package` | importy v Java kóde | balíčky, ktoré musí niekto iný exportovať, inak bundle nenabehne |
| `Private-Package` | balíčky modulu bez exportu | implementácia skrytá pred ostatnými |
| `Provide-Capability` (osgi.service) | `@Component(service = ...)` | „ponúkam službu GreetingService“ |
| `Require-Capability` (osgi.service) | `@Reference` | „potrebujem službu GreetingService“ |
| `Service-Component` | `@Component` | XML popisy komponentov v `OSGI-INF/`, ktoré číta Declarative Services |

Riadky sú zalomené na 72 znakov a pokračujú medzerou na začiatku. To je štandardný formát `MANIFEST.MF`.

---

## Spring Boot ekvivalent

| Liferay | Spring Boot |
|---|---|
| `./gradlew deploy` → JAR do `osgi/modules` | `./mvnw package` → jeden fat JAR, `java -jar` |
| Hot deploy jedného bundlu za behu | Reštart celej aplikácie (vo vývoji pomôže `spring-boot-devtools`) |
| Malý JAR (len vlastný kód) | Fat JAR desiatky MB (Tomcat, Spring, Hibernate…) |
| Overenie: `STARTED ...` v logu, `lb` v Gogo | Overenie: `Started ...Application in ... seconds`, `/actuator/health` |
| Viac verzií modulov v jednom portáli | Jedna verzia všetkého v jednej appke |
| Nasadenie do produkcie: JARy do `osgi/modules` alebo vlastný Docker image | Docker image s JAR-om, rolling update v Kubernetes |

Hot deploy vyzerá ako výhoda, v praxi sa však produkcia aj tak nasadzuje celým image kvôli reprodukovateľnosti. Pri migrácii sa preto hot deploy zvyčajne nestráca nič podstatné.

---

## Časté chyby

| Príznak | Príčina | Riešenie |
|---|---|---|
| Po deployi v logu žiadny `STARTED` | Bundle čaká na závislosť (stav `Installed`) | `./gogo.sh "lb com.example"`, potom `diag <id>` ([kapitola 05](05_osgi_a_gogo_shell.md)) |
| `STARTED` je v logu, ale zmena sa neprejavila | Prehliadač má v cache JS/CSS, alebo si nasadil iný modul | Ctrl+F5, skontroluj čas súboru v `bundles/osgi/modules` |
| `bundles/osgi/modules` je prázdny, hoci deploy prebehol | Liferay sa spúšťa z iného priečinka (napr. `docker compose` z iného adresára) | `docker compose` vždy spúšťaj v `liferay-workspace` |
| Bundle je `Active`, ale portlet sa nezobrazuje v ponuke | Komponent portletu nie je aktívny (chýba `@Reference`) | `./gogo.sh "scr:info <trieda>"`, [kapitola 07](07_portlet_mvc.md) |
| Tabuľka `demo_task` nevznikla | Bundle `task-service` nenabehol | Over `lb` a log, hľadaj `ERROR` |

---

## Otázky na pohovor

**Ako sa v Liferay nasadzuje modul?**
JAR bundle sa skopíruje do `osgi/modules` (alebo `deploy`) v Liferay home. File Install ho za behu nainštaluje a spustí. Vo workspace to robí `./gradlew deploy` a v produkcii zvyčajne vlastný Docker image s modulmi.

**Čo je hot deploy a aké má obmedzenia?**
Výmena jedného bundlu bez reštartu portálu. Obmedzenia: konzumenti s `STATIC` referenciami sa reštartujú, stav v pamäti sa stratí, pri zmene API (exportovaných balíčkov) treba prenasadiť aj závislé bundly. V clusteri treba nasadiť na každý uzol.

**Čo generuje bnd?**
OSGi hlavičky `MANIFEST.MF` (`Import-Package` z bytecode, `Export-Package` podľa `bnd.bnd` a verzií balíčkov) a z DS anotácií XML popisy komponentov v `OSGI-INF/` + hlavičku `Service-Component`.

**Ako vznikne DB tabuľka Service Builder entity?**
Pri prvom štarte `*-service` bundlu Liferay spustí `tables.sql`, `indexes.sql` a `sequences.sql` z bundlu, ak tabuľka neexistuje. Ďalšie zmeny schémy sa robia **upgrade procesmi** (`UpgradeStepRegistrator`). Spring Boot ekvivalent sú Flyway alebo Liquibase migrácie.

---

**Ďalej:** [05 – OSGi a Gogo shell](05_osgi_a_gogo_shell.md)
