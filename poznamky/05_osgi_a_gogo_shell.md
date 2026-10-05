# 05 – OSGi a Gogo shell

## Čo sa naučíš

- Čo je OSGi a prečo na ňom Liferay stavia
- Bundle, Export-Package / Import-Package, service registry, Declarative Services
- Prečo sa OSGi služba môže vymeniť za behu a ako to funguje v moduloch `greeting-*`
- Ako sa pozrieť do bežiaceho kontajnera cez Gogo shell (`lb`, `diag`, `services`)

---

## OSGi po lopate

### 1. Problém, ktorý OSGi rieši

Bežná Java appka (aj Spring Boot) má **jeden veľký classpath**. Všetky JAR súbory sú v jednom vreci:

- Každá trieda vidí každú triedu, aj tú, ktorú vidieť nemala.
- Dve verzie tej istej knižnice naraz mať nemôžeš.
- Zmena čohokoľvek znamená reštart celej aplikácie.

Pre malú appku to nevadí. Pre portál so stovkami modulov od rôznych tímov, ktorý má bežať nonstop, je to problém.

### 2. Riešenie: aplikácia z izolovaných krabičiek

**OSGi = Java aplikácia poskladaná z „krabičiek“ (bundlov), ktoré sa dajú za behu pridávať, vypínať a vymieňať.**

Analógia s mobilom: telefón (OSGi kontajner) beží stále a aplikácie (bundly) inštaluješ a odinštaluješ bez reštartu. Každá appka má vlastný priestor a s ostatnými komunikuje len cez povolené rozhrania.

### 3. Tri veci, ktoré musíš vedieť

#### a) Bundle = JAR + pár riadkov v `MANIFEST.MF`

Napríklad [`greeting-api/bnd.bnd`](../liferay-workspace/modules/greeting-api/bnd.bnd):

```
Bundle-Name: greeting-api
Bundle-SymbolicName: com.example.greeting.api
Bundle-Version: 1.0.0
Export-Package: com.example.greeting.api
```

Z toho nástroj **bnd** pri builde vygeneruje `MANIFEST.MF`. Je to obyčajný JAR, ktorý o sebe navyše hovorí, ako sa volá a akú má verziu.

#### b) Viditeľnosť: Export-Package / Import-Package

Každý bundle má **vlastný classloader**. Navonok vidno len to, čo bundle **exportuje**:

```
greeting-api      exportuje  com.example.greeting.api    (rozhranie GreetingService)
greeting-impl     importuje  com.example.greeting.api    -> vidí rozhranie
                  svoj balíček .impl NEEXPORTUJE          -> nikto nevidí DefaultGreetingService
```

Ostatní teda poznajú len **rozhranie**, implementácia je skrytá. V [`greeting-impl/bnd.bnd`](../liferay-workspace/modules/greeting-impl/bnd.bnd) preto `Export-Package` vôbec nie je a `Import-Package` si bnd dopočíta sám z bytecode.

Ak bundlu chýba import (nikto daný balíček neexportuje), bundle sa **nespustí** a zostane v stave `Installed` namiesto `Active`. Tento problém uvidíš najčastejšie. Príkaz `diag` ti povie, čo chýba.

#### c) Service Registry: „nástenka služieb“

Vnútri kontajnera je jedna spoločná nástenka:

- Bundle na ňu **vyvesí** službu: „ponúkam `GreetingService`, ranking 100“.
- Iný bundle sa **opýta**: „kto ponúka `GreetingService`?“ a dostane tú s najvyšším rankingom.

Ručne sa to robí cez API, nikto to tak nepíše. Používa sa **Declarative Services (DS)**, čiže anotácie:

```java
// greeting-impl: vyvesí službu na nástenku
@Component(property = "service.ranking:Integer=100", service = GreetingService.class)
public class DefaultGreetingService implements GreetingService { ... }

// konzument: „daj mi GreetingService z nástenky“
@Reference
private GreetingService greetingService;
```

V Spring svete je to `@Service` a `@Autowired`.

Pozor, `@Component` a `@Reference` sú z balíčka `org.osgi.service.component.annotations`, nie zo Springu.

### 4. Hlavný rozdiel oproti Springu: všetko je dynamické

V Springu sa beany vytvoria **raz pri štarte** a potom sa nemenia.

V OSGi môže služba **prísť a odísť za behu**. Presne to demonštruje demo s Gogo príkazom `greeting:hello`:

```
1. beží greeting-impl (ranking 100)
   greeting:hello Jano   ->  Hello, Jano!

2. beží aj greeting-impl-alt (ranking 200)
   -> konzument sa sám prepne na vyšší ranking, bez reštartu
   greeting:hello Jano   ->  Ahoj Jano, vitaj v OSGi!

3. zastavíš greeting-impl-alt (stop <id>)
   -> vráti sa späť na ranking 100
   greeting:hello Jano   ->  Hello, Jano!
```

**Dôležitý detail:** obyčajné `@Reference` sa za behu **neprepne**. Predvolená politika je `STATIC` + `RELUCTANT`: komponent si službu vyberie raz a novú, lepšiu ignoruje. Prepnutie funguje preto, že konzument [`GreetingCommand.java`](../liferay-workspace/modules/greeting-impl/src/main/java/com/example/greeting/impl/GreetingCommand.java) má referenciu nastavenú takto:

```java
@Reference(
	policy = ReferencePolicy.DYNAMIC,         // službu môže vymeniť za behu bez reštartu komponentu
	policyOption = ReferencePolicyOption.GREEDY  // a chce vždy tú s najvyšším rankingom
)
private volatile GreetingService _greetingService;  // volatile, lebo výmena príde z iného vlákna
```

To isté robí Liferay so svojimi modulmi: nasadíš nový JAR portletu a portál ho do pár sekúnd používa, server beží ďalej.

### 5. Životný cyklus bundlu (to, čo vidíš v `lb`)

```
INSTALLED --(všetky importy nájdené)--> RESOLVED --start--> ACTIVE
    ^                                                         |
    +-------------------------- stop / uninstall ------------+
```

| Stav | Význam | Čo robiť |
|---|---|---|
| `Active` | Všetko je v poriadku, bundle beží | nič |
| `Installed` | Niečo chýba (import, závislosť) | `diag <id>` |
| `Resolved` | Závislosti sú v poriadku, ale bundle nebeží (zastavený) | `start <id>` |

### 6. Ťahák do hlavy

| OSGi | Spring Boot | Jednou vetou |
|---|---|---|
| Bundle | JAR / Maven modul | krabička s menom a verziou |
| Export/Import-Package | (nič, všetko vidí všetko) | čo krabička ukazuje a čo potrebuje |
| Service Registry | ApplicationContext | nástenka služieb |
| `@Component(service = …)` | `@Service` | vyves službu |
| `@Reference` | `@Autowired` / konštruktor | zober službu z nástenky |
| `service.ranking` | `@Primary` | ktorá implementácia vyhrá |
| `DYNAMIC` + `GREEDY` referencia | (nič, beany sa za behu nemenia) | prepni sa na lepšiu službu za behu |
| Gogo `lb`, `diag`, `services` | Actuator `/actuator/beans` | pozri sa dnu za behu |
| Hot deploy bundlu | reštart appky | výmena za behu vs. všetko odznova |

### Na pohovor jednou vetou

> „OSGi je modulárny systém pre Javu: aplikácia sa skladá z bundlov s izolovanými classloadermi a explicitnými exportmi a importmi balíčkov. Bundly komunikujú cez service registry, ktoré sa dajú meniť za behu. Liferay na tom stavia celú platformu. Pri migrácii na Spring Boot sa z OSGi služieb stávajú Spring beany a z dynamických `@Reference` statická konštruktorová injekcia. Hot deploy jednotlivých modulov sa tým stráca a nahrádza ho nasadenie celej aplikácie.“

---

## Krok po kroku: Gogo shell

**Predpoklad:** Liferay beží a moduly sú nasadené ([kapitola 04](04_build_a_deploy_modulov.md)). Príkazy sú pre **PowerShell**, spúšťaj ich z `liferay-workspace`. Na spúšťanie `.ps1` skriptov treba povolenú execution policy ([kapitola 02](02_predpoklady_a_setup.md)).

### Ako sa do Gogo shellu dostaneš

Gogo shell je konzola do OSGi kontajnera. V Liferay počúva **len vnútri kontajnera** na `localhost:11311` (telnet), zvonku nie je dostupný. Máš tri možnosti:

1. **`.\gogo.ps1 "<príkaz>"`** – skript v repe pošle príkaz cez telnet vnútri kontajnera a vypíše výsledok. Používame ho v celej kapitole.
2. **V prehliadači:** Control Panel → Gogo Shell (prihlásený ako admin).
3. **Interaktívne:** `docker compose exec liferay telnet localhost 11311` (koniec cez `disconnect`).

Ako funguje [`gogo.ps1`](../liferay-workspace/gogo.ps1) (jadro skriptu):

```powershell
$command = $args -join ' '
$telnet = "(echo '$command'; sleep 2; echo 'disconnect'; sleep 1; echo y) | telnet localhost 11311 2>/dev/null"
docker compose -f "$PSScriptRoot\docker-compose.yml" exec -T liferay sh -c $telnet
```

Reťazec `$telnet` je shell príkaz, ktorý beží **vnútri Linux kontajnera** (preto `sh`, `sleep`, `/dev/null`). Skript pošle príkaz, počká 2 sekundy na výstup a odpojí sa. Preto každé volanie trvá ~3 s. Z výstupu potom odfiltruje úvodný banner a hlášky o odpojení, takže výstup začína `g!`, čo je prompt Gogo shellu. Vďaka `$PSScriptRoot` funguje skript z ľubovoľného priečinka.

### 1. `lb`: zoznam bundlov

```powershell
.\gogo.ps1 "lb com.example"
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

- `lb` bez parametra vypíše **všetky** bundly, v čerstvom Liferay ich je ~1 400.
- `lb com.example` filtruje podľa mena.
- **ID** sa líšia podľa toho, v akom poradí si nasadzoval. Vždy si ich prečítaj z `lb`, nekopíruj ich odtiaľto.

### 2. `services`: kto čo ponúka na nástenke

```powershell
.\gogo.ps1 "services com.example.greeting.api.GreetingService"
```

```
g! {com.example.greeting.api.GreetingService}={component.id=11623, component.name=com.example.greeting.alt.FriendlyGreetingService, service.id=21374, service.scope=bundle, service.ranking=200, service.bundleid=1398}
  "Registered by bundle:" com.example.greeting.impl.alt_1.0.0 [1398]
  "Bundles using service"
    com.example.greeting.impl_1.0.0 [1393]
{com.example.greeting.api.GreetingService}={component.id=11611, component.name=com.example.greeting.impl.DefaultGreetingService, service.id=21336, service.scope=bundle, service.ranking=100, service.bundleid=1393}
  "Registered by bundle:" com.example.greeting.impl_1.0.0 [1393]
  "Bundles using service"
    com.example.greeting.impl_1.0.0 [1393]
```

Ako to čítať:
- Na nástenke sú **dve** služby typu `GreetingService`.
- `service.ranking=200` je `FriendlyGreetingService` z bundlu 1398, `service.ranking=100` je `DefaultGreetingService` z bundlu 1393.
- „Bundles using service“: obe používa bundle `greeting-impl`, v ktorom je konzument `GreetingCommand`.

### 3. Zavolaj službu cez vlastný Gogo príkaz

Modul `greeting-impl` obsahuje [`GreetingCommand`](../liferay-workspace/modules/greeting-impl/src/main/java/com/example/greeting/impl/GreetingCommand.java), komponent, ktorý sa zaregistruje ako Gogo príkaz (vlastnosti `osgi.command.scope=greeting` a `osgi.command.function=hello,all`):

```powershell
.\gogo.ps1 "greeting:hello Jano"
```

```
g! Ahoj Jano, vitaj v OSGi!
```

```powershell
.\gogo.ps1 "greeting:all Jano"
```

```
g! DefaultGreetingService: Hello, Jano!
FriendlyGreetingService: Ahoj Jano, vitaj v OSGi!
```

- `hello` použije **jednu** službu, tú s najvyšším rankingom (pole `_greetingService`).
- `all` použije **všetky** registrované služby (metóda `addGreetingService` s `cardinality = MULTIPLE`).

### 4. Prepni implementáciu za behu: `stop` / `start`

Zastav bundle s rankingom 200 (ID si vezmi z `lb`):

```powershell
.\gogo.ps1 "stop 1398"
.\gogo.ps1 "greeting:hello Jano"
```

```
g! Hello, Jano!
```

```powershell
.\gogo.ps1 "greeting:all Jano"
```

```
g! DefaultGreetingService: Hello, Jano!
```

```powershell
.\gogo.ps1 "lb greeting"
```

```
g! START LEVEL 20
   ID|State      |Level|Name
 1391|Active     |   10|greeting-api (1.0.0)|1.0.0
 1393|Active     |   10|greeting-impl (1.0.0)|1.0.0
 1398|Resolved   |   10|greeting-impl-alt (1.0.0)|1.0.0
```

Bundle je **Resolved**: závislosti má v poriadku, ale nebeží. Jeho služba zmizla z nástenky a konzument sa prepol na ranking 100.

Spusti ho znova:

```powershell
.\gogo.ps1 "start 1398"
.\gogo.ps1 "greeting:hello Jano"
```

```
g! Ahoj Jano, vitaj v OSGi!
```

Toto je jadro „dynamických služieb“. Žiadny reštart, konzument sa prispôsobil sám.

### 5. `scr:info`: pozri sa dovnútra komponentu

```powershell
.\gogo.ps1 "scr:info com.example.greeting.impl.GreetingCommand"
```

Výstup (skrátený):

```
g! Component Description: com.example.greeting.impl.GreetingCommand
================================================================
Class:         com.example.greeting.impl.GreetingCommand
Bundle:        1393 (com.example.greeting.impl:1.0.0)
Enabled:       true
Immediate:     false
Services:      [java.lang.Object]
...
Component Configuration Id: 11626
---------------------------------
State:        SATISFIED
References:   (total 2)
  - GreetingService: com.example.greeting.api.GreetingService SATISFIED 0..n dynamic+greedy
    * Bound to [21378] from bundle 1393 (com.example.greeting.impl:1.0.0)
    * Bound to [21380] from bundle 1398 (com.example.greeting.impl.alt:1.0.0)
  - _greetingService: com.example.greeting.api.GreetingService SATISFIED 1..1 dynamic+greedy
    * Bound to [21380] from bundle 1398 (com.example.greeting.impl.alt:1.0.0)
```

Najužitočnejší príkaz pri ladení, keď „služba nefunguje“:
- **`References`**: každá `@Reference`, jej kardinalita (`0..n`, `1..1`), politika (`dynamic+greedy`) a na ktorú službu je naviazaná.
- **`State`**:
  - `ACTIVE` – komponent je vytvorený a beží.
  - `SATISFIED` – všetky závislosti sú splnené, ale inštancia sa ešte nevytvorila. Komponent so službou je štandardne „lenivý“ (`Immediate: false`) a vytvorí sa až pri prvom použití.
  - `UNSATISFIED REFERENCE` – chýba povinná `@Reference`. Komponent nebeží a jeho služba nie je na nástenke. **Toto je najčastejší dôvod, prečo sa portlet nezobrazí.**

Prehľad všetkých komponentov v bundli:

```powershell
.\gogo.ps1 "scr:list 1393"
```

```
g! com.example.greeting.impl.GreetingCommand in bundle 1,393 (com.example.greeting.impl:1.0.0) enabled, 1 instance.
    Id: 11626, State:SATISFIED
com.example.greeting.impl.DefaultGreetingService in bundle 1,393 (com.example.greeting.impl:1.0.0) enabled, 1 instance.
    Id: 11625, State:ACTIVE
```

### 6. `diag`: prečo bundle nenabehol

Najčastejší problém v praxi: bundle zostane v stave `Installed`. Nasimulujeme ho tak, že **odoberieme API bundle**, od ktorého implementácie závisia:

```powershell
Move-Item bundles\osgi\modules\com.example.greeting.api.jar $env:TEMP\
# počkaj ~20 s
.\gogo.ps1 "lb greeting"
```

```
g! START LEVEL 20
   ID|State      |Level|Name
 1393|Installed  |   10|greeting-impl (1.0.0)|1.0.0
 1398|Installed  |   10|greeting-impl-alt (1.0.0)|1.0.0
```

Obe implementácie spadli do `Installed`. Prečo?

```powershell
.\gogo.ps1 "diag 1393"
```

```
g! com.example.greeting.impl [1393]
  Unresolved requirement: Import-Package: com.example.greeting.api; version="1.0.0"
```

`diag` presne povie, čo chýba: nikto neexportuje balíček `com.example.greeting.api`. To je `Import-Package` z manifestu ([kapitola 04](04_build_a_deploy_modulov.md)). V logu Liferay je to isté ako výnimka:

```
BundleException: Could not resolve module: com.example.greeting.impl [1393]
  Unresolved requirement: Import-Package: com.example.greeting.api; version="1.0.0"
```

A Gogo príkaz zmizol, lebo jeho komponent nebeží:

```powershell
.\gogo.ps1 "greeting:hello Jano"
```

```
g! gogo: CommandNotFoundException: Command not found: greeting:hello
```

**Oprava:** vráť API bundle.

```powershell
Move-Item $env:TEMP\com.example.greeting.api.jar bundles\osgi\modules\
# počkaj ~20 s
.\gogo.ps1 "lb greeting"
```

```
g! START LEVEL 20
   ID|State      |Level|Name
 1393|Active     |   10|greeting-impl (1.0.0)|1.0.0
 1398|Active     |   10|greeting-impl-alt (1.0.0)|1.0.0
 1399|Active     |   10|greeting-api (1.0.0)|1.0.0
```

Implementácie sa samy spustili, len čo sa objavil exportovaný balíček.

> `diag` na zdravom bundli vypíše `No resolution report for the bundle.` To znamená, že nie je čo hlásiť.

### Ťahák Gogo príkazov

| Príkaz | Čo urobí |
|---|---|
| `lb [filter]` | zoznam bundlov a ich stav |
| `diag <id>` | prečo bundle nie je resolved (chýbajúce importy) |
| `bundle <id>` | detail bundlu: umiestnenie JAR, hlavičky manifestu (napr. `Export-Package = com.example.greeting.api;version="1.0.0"`) |
| `services <rozhranie>` | kto registruje danú službu, s akými vlastnosťami, kto ju používa |
| `scr:list [<id>]` | komponenty (v bundli) a ich stav |
| `scr:info <trieda>` | detail komponentu: referencie, stav, vlastnosti |
| `start <id>` / `stop <id>` | spustí alebo zastaví bundle |
| `greeting:hello <meno>` | náš vlastný príkaz z `GreetingCommand` |

---

## Spring Boot ekvivalent

V `spring-boot-tasks` nie je modul `greeting`, takže toto je ilustrácia, ako by vyzeral ten istý princíp v Springu:

```java
public interface GreetingService { String greet(String name); }

@Service
class DefaultGreetingService implements GreetingService { ... }

@Service
@Primary                      // ≈ vyšší service.ranking
class FriendlyGreetingService implements GreetingService { ... }

@Component
class GreetingCommand {
	private final GreetingService greetingService;         // dostane @Primary
	private final List<GreetingService> greetingServices;  // ≈ cardinality MULTIPLE

	GreetingCommand(GreetingService greetingService, List<GreetingService> greetingServices) { ... }
}
```

| OSGi (v deme) | Spring Boot |
|---|---|
| `@Component(service = GreetingService.class)` | `@Service` (typ je odvodený z implementovaných rozhraní) |
| `service.ranking=200` | `@Primary` alebo `@Order` |
| `@Reference` na jednu službu | konštruktorová injekcia |
| `@Reference(cardinality = MULTIPLE)` | `List<GreetingService>` v konštruktore |
| `DYNAMIC` + `GREEDY`, prepnutie za behu | **neexistuje**. Výber implementácie je pri štarte, zmena znamená reštart (alebo vlastný mechanizmus, napr. feature flag). |
| Export/Import-Package | nič (spoločný classpath). Najbližšie je Java Platform Module System alebo kontrola architektúry cez ArchUnit / Spring Modulith. |
| Gogo `lb`, `scr:info`, `services` | Actuator `/actuator/beans`, `/actuator/conditions` |
| `UNSATISFIED REFERENCE` (komponent ticho nebeží) | `NoSuchBeanDefinitionException`: aplikácia **nenaštartuje** (fail fast) |

Posledný riadok je dôležitý rozdiel. V OSGi chýbajúca závislosť znamená, že komponent ticho nebeží a portál ide ďalej. V Springu aplikácia spadne hneď pri štarte. Pri migrácii je to výhoda: chyby v zapojení sa ukážu skôr.

---

## Časté chyby

| Príznak | Príčina | Riešenie |
|---|---|---|
| Bundle v stave `Installed` | Chýba importovaný balíček (nikto ho neexportuje alebo nesedí verzia) | `diag <id>`, nasadiť chýbajúci bundle alebo opraviť `Export-Package`/verziu |
| Bundle je `Active`, ale služba nie je na nástenke | Komponent má `UNSATISFIED REFERENCE` | `scr:info <trieda>` → sekcia References |
| Po nasadení novej implementácie konzument stále používa starú | Referencia je `STATIC`/`RELUCTANT` (predvolené) | `policy = DYNAMIC, policyOption = GREEDY`, alebo reštart bundlu konzumenta |
| `CommandNotFoundException` pre vlastný Gogo príkaz | Bundle alebo komponent s príkazom nebeží | `lb`, `scr:info` |
| `gogo.ps1` nevypíše nič | Kontajner nebeží alebo príkaz trvá dlhšie ako 2 s | `docker compose ps`, prípadne zvýšiť `sleep 2` v skripte |
| `gogo.ps1 cannot be loaded because running scripts is disabled` | Execution policy `Restricted` (predvolená vo Windows) | `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` ([kapitola 02](02_predpoklady_a_setup.md)) |
| `Cannot coerce headers(Token) to any of [(Bundle[])]` | Niektoré príkazy (napr. `headers`) chcú objekt bundle, nie číslo | Použi `bundle <id>` alebo `diag`/`lb` |

---

## Otázky na pohovor

**Čo je OSGi bundle a čím sa líši od bežného JAR?**
Je to JAR s OSGi hlavičkami v manifeste (symbolic name, verzia, `Import-Package`, `Export-Package`). Má vlastný classloader a vidí len importované balíčky. Dá sa za behu inštalovať, spúšťať, zastavovať a aktualizovať.

**Čo je Declarative Services?**
OSGi špecifikácia na definíciu komponentov anotáciami (`@Component`, `@Reference`, `@Activate`). Runtime (SCR) komponenty vytvára, napája na služby a registruje ich do service registry. Je to obdoba Spring DI.

**Ako sa vyberá implementácia, ak je viac služieb rovnakého typu?**
Podľa `service.ranking` (vyšší vyhráva), pri zhode podľa nižšieho `service.id` (staršia registrácia). Dá sa aj filtrovať cez `target` v `@Reference`. V Springu `@Primary`, `@Qualifier`, `@Order`.

**Aký je rozdiel medzi STATIC a DYNAMIC referenciou?**
Pri `STATIC` sa pri zmene služby komponent deaktivuje a znova vytvorí. Pri `DYNAMIC` sa služba vymení za behu (pole musí byť `volatile` alebo treba bind/unbind metódy). `GREEDY` znamená, že sa prepne aj na lepšiu (vyšší ranking) službu, `RELUCTANT` drží pôvodnú.

**Bundle nenabehol. Ako postupuješ?**
`lb` na zistenie stavu. Pri `Installed` nasleduje `diag <id>` (chýbajúci import alebo verzia). Pri `Active` bez funkčnosti `scr:info` na komponent (nesplnená referencia, chyba v `@Activate`). Popritom pozerám log Liferay.

**Čo sa stane s OSGi pri migrácii na Spring Boot?**
Zmizne. OSGi služby sa stanú Spring beanmi, `@Reference` konštruktorovou injekciou a ranking `@Primary`. Prichádzame o dynamiku za behu a izoláciu modulov. Hranice modulov je dobré zachovať aspoň balíčkami a testami architektúry (ArchUnit, Spring Modulith), inak sa z modulárneho systému stane „big ball of mud“.

---

**Ďalej:** [06 – Service Builder](06_service_builder.md)
