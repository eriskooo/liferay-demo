# 02 – Predpoklady a setup

## Čo sa naučíš

- Čo musí byť nainštalované, aby demo bežalo, a ako to overíš
- Ako obísť firemný Gradle init skript, ktorý mimo VPN rozbije build
- Ako sa orientovať v Liferay Workspace (čo je kde a načo)
- Ako nastaviť IntelliJ IDEA, aby fungovalo preklikávanie do kódu aj do Liferay tried

---

## Teória v skratke

Liferay vývoj potrebuje dve oddelené veci:

1. **Runtime**, teda bežiaci portál. V deme je to Docker kontajner `liferay/portal:7.4.3.132-ga132` + PostgreSQL. Na disk nič neinštaluješ.
2. **Build nástroje**, ktoré z modulov urobia JAR bundly. Je to Gradle projekt **Liferay Workspace** s Gradle wrapperom (`gradlew`), takže Gradle ani Liferay neinštaluješ, stiahne sa sám.

Spring Boot časť potrebuje len JDK 21 a Docker (Testcontainers). Maven je tiež cez wrapper (`mvnw`).

| Nástroj | Načo | Verzia v deme |
|---|---|---|
| Docker Desktop | beh Liferay + PostgreSQL, Testcontainers | overené na 28.3.3 |
| JDK | build modulov, Spring Boot | 21 |
| Gradle | build Liferay workspace | 8.9 (cez `gradlew`) |
| Maven | build Spring Boot | cez `mvnw` |
| Git Bash | skripty `gogo.sh`, `create-demo-page.sh` | – |

---

## Krok po kroku

### 1. Over Docker

```bash
docker version --format '{{.Server.Version}}'
```

Očakávaný výstup: číslo verzie, napr. `28.3.3`.

Ak namiesto toho vidíš:

```
error during connect: ... open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

Docker Desktop nebeží. Spusti ho a počkaj, kým ikona v lište zozelenie.

Over aj pamäť, ktorú má Docker k dispozícii:

```bash
docker info --format 'CPUs={{.NCPU}} Mem={{.MemTotal}}'
```

Očakávaný výstup napr. `CPUs=16 Mem=16066625536` (bajty, teda ~15 GB). Liferay má v `docker-compose.yml` nastavené `-Xmx3g`, takže Docker potrebuje **aspoň ~4 GB**. Menej sa nastavuje v Docker Desktop → Settings → Resources.

### 2. Over JDK 21

```bash
java -version
```

Očakávaný výstup (vendor sa môže líšiť):

```
openjdk version "21.0.9" 2025-10-21
```

### 3. Firemný Gradle init skript (dôležité!)

Gradle pri každom builde spúšťa skripty z `~/.gradle/init.d/`. Na firemnom notebooku je tam:

```bash
ls ~/.gradle/init.d
```

```
OKsystem-repo.gradle
```

Ten skript pridá do **každého** Gradle buildu firemný Maven repozitár `http://mavenrepo.oksystem.local/repo/default`. Mimo firemnej siete (bez VPN) je nedostupný a stiahnutie závislostí zlyhá.

Riešenie bez zásahu do globálnej konfigurácie: pre tento projekt použi **oddelený Gradle home**. Gradle potom `~/.gradle/init.d` vôbec nevidí.

```bash
# Git Bash
export GRADLE_USER_HOME=$HOME/.gradle-liferay-demo
```

```powershell
# PowerShell
$env:GRADLE_USER_HOME="$HOME\.gradle-liferay-demo"
```

Platí to len pre aktuálne okno terminálu. Pri každom novom okne to nastav znova, alebo to pridaj do `~/.bashrc`, ak chceš natrvalo.

> Na PC bez firemného init skriptu (napr. doma) tento krok nepotrebuješ. Neuškodí však, len sa závislosti stiahnu do iného priečinka.

### 4. Over Gradle build workspace

```bash
cd liferay-workspace
./gradlew projects -q
```

Prvé spustenie stiahne Gradle 8.9, Liferay Workspace plugin a ďalšie závislosti (minúty). Ďalšie behy trvajú ~20 s. Očakávaný výstup:

```
Root project 'liferay-workspace'
\--- Project ':modules'
     +--- Project ':modules:greeting-api' - greeting-api
     +--- Project ':modules:greeting-impl' - greeting-impl
     +--- Project ':modules:greeting-impl-alt' - greeting-impl-alt
     +--- Project ':modules:task'
     |    +--- Project ':modules:task:task-api' - task-api
     |    \--- Project ':modules:task:task-service' - task-service
     +--- Project ':modules:task-rest' - task-rest
     \--- Project ':modules:task-web' - task-web
```

To je 7 Gradle projektov = 7 bundlov z [kapitoly 01](01_co_je_liferay.md).

---

## Kód z repa: čo je v Liferay Workspace

```
liferay-workspace/
├── settings.gradle          aplikuje plugin com.liferay.workspace (verzia 17.1.11)
├── gradle.properties        liferay.workspace.product=portal-7.4-ga132  ← NAJDÔLEŽITEJŠÍ RIADOK
├── build.gradle             spoločné nastavenie unit testov pre všetky moduly
├── docker-compose.yml       Liferay + PostgreSQL (kapitola 03)
├── gogo.sh                  Gogo shell príkaz do kontajnera (kapitola 05)
├── create-demo-page.sh      vytvorí stránku s portletom (kapitola 07)
├── configs/                 konfigurácia portálu pre prostredia
│   ├── common/              pre všetky prostredia
│   ├── local/ dev/ uat/ prod/   portal-ext.properties podľa prostredia
│   └── docker/              pre Docker image
├── modules/                 NAŠE MODULY (OSGi bundly)
└── bundles/                 (nie je v gite) sem padajú zbuildené JARy, mountuje sa do kontajnera
```

### `gradle.properties`: jeden riadok určuje verziu Liferay

```properties
liferay.workspace.product=portal-7.4-ga132
```

Z tohto riadku workspace plugin odvodí:
- verziu Docker image (`liferay/portal:7.4.3.132-ga132`),
- verzie **všetkých** Liferay API, proti ktorým sa kompiluje (BOM `release.portal.api`).

Preto v `build.gradle` modulov nie sú verzie Liferay knižníc. Napríklad [`modules/greeting-impl/build.gradle`](../liferay-workspace/modules/greeting-impl/build.gradle):

```groovy
dependencies {
	compileOnly group: "com.liferay.portal", name: "release.portal.api"
	compileOnly project(":modules:greeting-api")
}
```

Všimni si **`compileOnly`**. Liferay API sa do JAR nebalí, pri behu ho dodá portál. Rovnako `greeting-api` sa nebalí do `greeting-impl`. Za behu ho nájde OSGi cez `Import-Package` ([kapitola 05](05_osgi_a_gogo_shell.md)).

### `build.gradle` (root): spoločné testy

[`build.gradle`](../liferay-workspace/build.gradle) pridá do všetkých modulov JUnit 5 + Mockito a `--add-opens` pre JVM. Prečo to tak je, vysvetľuje [kapitola 09](09_testovanie_liferay.md).

### `configs/`: konfigurácia podľa prostredia

Liferay Workspace pozná prostredia `local`, `dev`, `uat`, `prod`. Pri buildovaní distribúcie skopíruje `configs/common` + `configs/<prostredie>` do servera. V deme sa však konfigurácia pre Docker robí cez **premenné prostredia** v `docker-compose.yml` (kapitola 03), `configs/` je tu len ako ukážka štruktúry.

---

## IntelliJ IDEA: aby fungovalo preklikávanie

Ak otvoríš koreň repa (`liferay-demo`) ako obyčajný priečinok, IDEA nevie, kde sú zdrojáky a knižnice. Treba jej pripojiť oba projekty:

1. **Spring Boot (Maven):** klikni pravým na `spring-boot-tasks/pom.xml` a zvoľ **Add as Maven Project**.
2. **Gradle home** (kvôli kroku 3): **Settings → Build, Execution, Deployment → Build Tools → Gradle**
   - **Gradle user home** = `C:\Users\<ty>\.gradle-liferay-demo`
   - **Gradle JVM** = JDK 21
3. **Liferay workspace (Gradle):** klikni pravým na `liferay-workspace/build.gradle` a zvoľ **Link Gradle Project**.
4. Počkaj na sync (status bar dole). Prvý trvá niekoľko minút.

Overenie:
- V `TaskPortlet.java` s Ctrl klikni na `MVCPortlet`. Otvorí sa dekompilovaná Liferay trieda.
- V `TaskService.java` s Ctrl klikni na `TaskRepository`. Skočí na rozhranie.
- Vpravo sú panely **Gradle** a **Maven** s oboma projektmi.

> Existuje aj plugin **Liferay IntelliJ Plugin** (Marketplace), ktorý pridáva napr. dopĺňanie vlastností `@Component` a podporu pre `service.xml`. Pre toto demo nie je potrebný.

---

## Spring Boot ekvivalent

| Liferay Workspace | Spring Boot projekt (`spring-boot-tasks`) |
|---|---|
| `gradlew` (Gradle 8.9) | `mvnw` (Maven) |
| `liferay.workspace.product` → BOM `release.portal.api` | `spring-boot-starter-parent` → BOM verzií |
| `compileOnly` Liferay API (dodá portál) | `implementation` / `compile` (všetko sa balí do fat JAR) |
| 7 modulov = 7 JAR súborov nasadených zvlášť | 1 modul = 1 spustiteľný JAR |
| `configs/<env>/portal-ext.properties` | `application.yml` + profily (`application-keycloak.yml`) |

Hlavný rozdiel: v Liferay **kompiluješ proti platforme, ktorá už beží**. V Spring Boot si platformu (Tomcat, Spring, Hibernate) **pribalíš** do vlastného JAR.

---

## Časté chyby

| Príznak | Príčina | Riešenie |
|---|---|---|
| `Could not resolve ...` alebo timeout na `mavenrepo.oksystem.local` | Firemný init skript mimo VPN | `export GRADLE_USER_HOME=$HOME/.gradle-liferay-demo` (krok 3) |
| `error during connect ... dockerDesktopLinuxEngine` | Docker Desktop nebeží | Spustiť Docker Desktop |
| Gradle hlási nekompatibilnú Javu | `JAVA_HOME` ukazuje na inú verziu | Nastaviť `JAVA_HOME` na JDK 21, v IDEA Gradle JVM = 21 |
| `./gradlew: Permission denied` (Git Bash) | Chýba príznak spustiteľnosti | `sh gradlew ...` alebo `chmod +x gradlew` |
| IDEA nepozná `MVCPortlet`, všetko červené | Gradle projekt nie je pripojený alebo sync zlyhal | Sekcia IntelliJ IDEA vyššie |

---

## Otázky na pohovor

**Čo je Liferay Workspace?**
Gradle (alebo Maven) projekt so štandardnou štruktúrou pre Liferay vývoj: moduly, témy, konfigurácie pre prostredia a nástroje na build, deploy a Docker. Generuje sa cez Blade CLI.

**Ako sa určuje verzia Liferay API, proti ktorej kompiluješ?**
Vlastnosťou `liferay.workspace.product` v `gradle.properties`. Z nej sa odvodí BOM `release.portal.api` aj Docker image. Moduly potom nemajú verzie Liferay závislostí.

**Prečo sú Liferay závislosti `compileOnly`?**
Za behu ich poskytuje portál (OSGi kontajner) a modul ich importuje cez `Import-Package`. Ak by si ich pribalil, vznikli by duplicitné triedy a konflikty classloaderov.

**Čo sa zmení pri prechode na Spring Boot z pohľadu buildu?**
Z viacerých samostatne nasadzovaných bundlov proti bežiacej platforme sa stane jeden spustiteľný JAR, ktorý si platformu nesie so sebou. Jednoduchší build aj nasadenie (Docker image, Kubernetes), ale bez hot deployu jednotlivých modulov.

---

**Ďalej:** [03 – Spustenie Liferay](03_spustenie_liferay.md)
