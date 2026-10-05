# 03 – Spustenie Liferay

## Čo sa naučíš

- Spustiť Liferay + PostgreSQL jedným príkazom cez Docker Compose
- Spoznať, kedy je portál naozaj nabehnutý (a prečo to trvá 2 minúty)
- Prihlásiť sa a zorientovať v admin rozhraní
- Kde hľadať logy a ako sa pozrieť do databázy
- Ako sa Liferay konfiguruje premennými prostredia

---

## Teória v skratke

`docker-compose.yml` spustí dva kontajnery:

| Kontajner | Image | Port na tvojom PC | Načo |
|---|---|---|---|
| `postgres` | `postgres:16` | `5433` | databáza `lportal` (user/heslo `liferay`/`liferay`) |
| `liferay` | `liferay/portal:7.4.3.132-ga132` | `8080` | portál |

Port PostgreSQL je **5433**, nie štandardných 5432, aby nekolidoval s prípadným lokálnym PostgreSQL. Na tú istú DB sa neskôr pripojí aj Spring Boot ([kapitola 11](11_migracia_dat_a_security.md)).

**Čo sa deje pri prvom štarte** (preto ~2 minúty):

1. Liferay zistí, že DB je prázdna, a vytvorí ~440 tabuliek s počiatočnými dátami (default site, admin používateľ `test@liferay.com`, role).
2. OSGi kontajner (Eclipse Equinox) naštartuje ~1 400 bundlov.
3. Spustí sa **sidecar Elasticsearch**: Liferay si pre vývoj sám spustí Elasticsearch ako podproces. Do produkcie sa to nesmie použiť, tam je vždy samostatný Elasticsearch cluster.
4. Tomcat vypíše `Server startup in [...] milliseconds`.

Ďalšie štarty (keď DB už existuje) sú výrazne rýchlejšie, lebo krok 1 odpadne. Pri overovaní: prvý štart **130 s**, `docker compose restart liferay` **28 s**.

### Konfigurácia cez premenné prostredia

Liferay sa konfiguruje súborom `portal-ext.properties`. Docker image ponúka trik: každá premenná `LIFERAY_*` sa preloží na property. Z [`docker-compose.yml`](../liferay-workspace/docker-compose.yml):

```yaml
LIFERAY_JDBC_PERIOD_DEFAULT_PERIOD_URL: jdbc:postgresql://postgres:5432/lportal
LIFERAY_SETUP_PERIOD_WIZARD_PERIOD_ENABLED: "false"
LIFERAY_JVM_OPTS: "-Xms2g -Xmx3g"
```

Pravidlá prekladu:
- `_PERIOD_` = bodka
- `_UPPERCASEX` = veľké písmeno X

Takže `LIFERAY_JDBC_PERIOD_DEFAULT_PERIOD_URL` = `jdbc.default.url`. Škaredé, ale funguje to bez vlastného image. `LIFERAY_JVM_OPTS` je výnimka, nastavuje parametre JVM.

`setup.wizard.enabled=false` vypne úvodného sprievodcu, takže sa po štarte rovno prihlásiš.

---

## Krok po kroku

Všetky príkazy sú pre PowerShell ([kapitola 02](02_predpoklady_a_setup.md)) a spúšťajú sa z adresára `liferay-workspace`:

```powershell
cd liferay-workspace
```

### 1. Spusti kontajnery

```powershell
docker compose up -d
```

Očakávaný výstup (prvýkrát sa ešte sťahuje image, ~2,4 GB):

```
 Container liferay-workspace-postgres-1  Started
 Container liferay-workspace-postgres-1  Waiting
 Container liferay-workspace-postgres-1  Healthy
 Container liferay-workspace-liferay-1  Starting
 Container liferay-workspace-liferay-1  Started
```

Liferay čaká, kým je PostgreSQL `Healthy` (`depends_on` + `healthcheck` v compose súbore).

### 2. Sleduj štart

```powershell
docker compose logs -f liferay
```

Uvidíš veľa riadkov. Zaujímavé sú tieto:

```
Starting Liferay Community Edition Portal 7.4.3.132 CE GA132 (February 18, 2025)
... [SidecarManager:70] Liferay automatically starts a child process of Elasticsearch named sidecar ...
... [Sidecar:100] Sidecar Elasticsearch 7.17.26 liferay_sidecar started at 127.0.0.1:9201
...
... org.apache.catalina.startup.Catalina.start Server startup in [130212] milliseconds
```

Po riadku **`Server startup in`** je portál pripravený (u mňa 130 s). Sledovanie logu ukončíš `Ctrl+C`, kontajner beží ďalej.

Varovanie o sidecare (`WARN`) je v poriadku. Liferay len pripomína, že takto sa to nemá robiť v produkcii.

### 3. Over stav kontajnerov

```powershell
docker compose ps
```

Očakávaný výstup (skrátený):

```
NAME                           IMAGE                            STATUS                   PORTS
liferay-workspace-liferay-1    liferay/portal:7.4.3.132-ga132   Up 2 minutes (healthy)   0.0.0.0:8080->8080/tcp
liferay-workspace-postgres-1   postgres:16                      Up 3 minutes (healthy)   0.0.0.0:5433->5432/tcp
```

Oba musia byť `(healthy)`. Kým je Liferay `(health: starting)`, ešte štartuje.

### 4. Over, že odpovedá HTTP

```powershell
curl.exe -s -o NUL -w '%{http_code}\n' http://localhost:8080/
```

Očakávaný výstup: `200`. Prvá požiadavka po štarte môže trvať aj niekoľko sekúnd.

`curl.exe` (nie `curl`) preto, lebo `curl` je v PowerShell 5.1 alias na `Invoke-WebRequest` a parametre `-s`, `-o` by nepochopil. `NUL` je windowsový ekvivalent `/dev/null` (výstup sa zahodí).

### 5. Over prihlásenie cez REST API

Liferay má zabudované REST API (tzv. **headless API**) na `/o/headless-*`. Najjednoduchší test prihlásenia:

```powershell
curl.exe -s -u test@liferay.com:test http://localhost:8080/o/headless-admin-user/v1.0/my-user-account | Select-String '"(emailAddress|alternateName)"'
```

`Select-String` je PowerShell obdoba `grep`, regulárny výraz berie rovnako.

Očakávaný výstup:

```
  "alternateName" : "test",
  "emailAddress" : "test@liferay.com",
```

Bez prihlásenia (`-u` vynecháš) dostaneš `403`.

### 6. Prihlás sa v prehliadači

1. Otvor <http://localhost:8080>
2. Vpravo hore klikni na **Sign In**
3. E-mail `test@liferay.com`, heslo `test`

Si administrátor. Na čo sa pozrieť (stačí 5 minút, nič neklikaj „naslepo“):

| Kde | Čo tam je | Prečo ťa to zaujíma pri migrácii |
|---|---|---|
| Ikona mriežky vpravo hore → **Control Panel → Users and Organizations** | používatelia | Používatelia žijú v Liferay DB. Pri migrácii idú napríklad do Keycloaku. |
| **Control Panel → Roles** | role (Administrator, Power User, User, Guest…) | Mapovanie na Spring Security role |
| Ikona mriežky → **Control Panel → Gogo Shell** | Gogo shell v prehliadači | Rovnaké príkazy ako `.\gogo.ps1` ([kapitola 05](05_osgi_a_gogo_shell.md)) |
| Ľavé menu (Product Menu) → **Site Builder → Pages** | stránky site | Sem sa pridáva náš portlet ([kapitola 07](07_portlet_mvc.md)) |
| Ikona mriežky → **Control Panel → Server Administration** | logy, cache, skripty | Prevádzka portálu |

> Názvy položiek menu sú z Liferay 7.4. Ak sa v UI niečo volá trochu inak, hľadaj podľa významu.

### 7. Pozri sa do databázy

```powershell
docker compose exec -T postgres psql -U liferay -d lportal -tAc `
  "select count(*) from information_schema.tables where table_schema='public'"
```

Očakávaný výstup: `443` (počet Liferay tabuliek po prvom štarte).

```powershell
docker compose exec -T postgres psql -U liferay -d lportal -c `
  "select userid, emailaddress, screenname from user_ where emailaddress like '%liferay.com'"
```

Backtick `` ` `` na konci riadku je v PowerShelli pokračovanie príkazu na ďalšom riadku (v bashi `\`).

Očakávaný výstup (ID a poradie riadkov sa môžu líšiť):

```
 userid |            emailaddress             |       screenname
--------+-------------------------------------+-------------------------
  20096 | default@liferay.com                 | 20096
  20382 | default-service-account@liferay.com | default-service-account
  20123 | test@liferay.com                    | test
(3 rows)
```

Tabuľka sa volá `user_` s podčiarkovníkom, lebo `user` je v SQL rezervované slovo. Naše tabuľky (`demo_task`) tu zatiaľ nie sú. Vzniknú až po nasadení modulu `task-service` v [kapitole 04](04_build_a_deploy_modulov.md).

Z IntelliJ (Database tool) alebo DBeaveru sa pripojíš na `localhost:5433`, DB `lportal`, user `liferay`, heslo `liferay`.

### 8. Kde sú logy

- `docker compose logs liferay` – to, čo Liferay vypisuje na konzolu (najpraktickejšie)
- V kontajneri `/opt/liferay/logs/liferay.<dátum>.log` – rovnaký obsah ako súbor

```powershell
docker compose exec -T liferay ls /opt/liferay/logs
```

```
companies
liferay.2026-10-04.log
liferay.2026-10-04.xml
```

`ls` tu beží **vnútri Linux kontajnera**, preto je to linuxový príkaz a cesta `/opt/...`. PowerShell cestu nemení (na rozdiel od Git Bash, kde bolo treba `MSYS_NO_PATHCONV=1`).

### 9. Zastavenie a upratovanie

```powershell
docker compose stop        # zastaví, dáta zostanú (ďalší štart je rýchlejší)
docker compose start       # znova spustí
docker compose down        # zmaže kontajnery, dáta (volume) zostanú
docker compose down -v     # zmaže aj volume = DB aj Liferay data, ďalší štart je opäť „prvý“
```

---

## Kód z repa: čo je v kontajneri

```
/opt/liferay/                     = "Liferay home"
├── tomcat/                       Tomcat s portálom (webapp ROOT)
├── osgi/
│   ├── modules/                  ← namountované z ./bundles/osgi/modules (sem ide náš deploy)
│   ├── portal/, marketplace/     Liferay bundly
│   └── configs/                  OSGi konfigurácie (.config súbory)
├── deploy/                       ďalší hot-deploy priečinok (JAR/WAR sem skopírovaný sa nainštaluje)
├── data/                         dokumenty, indexy (volume liferay-data)
├── elasticsearch-sidecar/        sidecar Elasticsearch
└── logs/                         logy
```

Mount `./bundles/osgi/modules:/opt/liferay/osgi/modules` v `docker-compose.yml` je kľúčový. Keď v [kapitole 04](04_build_a_deploy_modulov.md) spustíš `.\gradlew deploy`, JARy sa skopírujú na tvoj disk do `bundles/osgi/modules` a Liferay ich v kontajneri okamžite uvidí a nainštaluje.

---

## Spring Boot ekvivalent

| Liferay | Spring Boot (`spring-boot-tasks`) |
|---|---|
| `docker compose up -d`, čakať ~2 min | `.\mvnw spring-boot:run`, pár sekúnd |
| Port 8080 | Port **8081** (aby mohli bežať naraz) |
| `portal-ext.properties` / premenné `LIFERAY_*` | `application.yml` / premenné `SPRING_*` (napr. `SPRING_DATASOURCE_URL`) |
| `Server startup in [...] milliseconds` | `Started SpringBootTasksApplication in ... seconds` |
| Kontajner `(healthy)` | `/actuator/health` (ak je pridaný Actuator) |
| DB vytvorí Liferay sám pri prvom štarte | Schému vytvorí Flyway migrácia (`V1__create_task.sql`) |
| Sidecar Elasticsearch | Nič, ak vyhľadávanie potrebuješ, pridáš ho sám |

Obe platformy podporujú konfiguráciu cez premenné prostredia, čo je dôležité pre Docker a Kubernetes. Spring Boot má však prirodzenejšie mapovanie (`SPRING_DATASOURCE_URL` → `spring.datasource.url`), bez `_PERIOD_`.

---

## Časté chyby

| Príznak | Príčina | Riešenie |
|---|---|---|
| `Bind for 0.0.0.0:8080 failed: port is already allocated` | Na 8080 už niečo beží (iný Tomcat, Spring appka) | Zastav to, alebo zmeň mapovanie na `"8090:8080"` |
| Liferay kontajner skončí, v logu `OutOfMemoryError` alebo `exited (137)` | Docker má málo RAM | Docker Desktop → Resources → aspoň 4–6 GB |
| `http://localhost:8080` neodpovedá | Portál ešte štartuje | Čakaj na `Server startup in` v logu |
| Prihlásenie `test@liferay.com`/`test` nefunguje | DB je z iného behu, kde sa heslo zmenilo | `docker compose down -v` a štart od nuly |
| V logu chyby o Elasticsearch po veľmi rýchlom reštarte | Sidecar ešte nedobehol alebo zostal starý proces | `docker compose restart liferay` |
| Pri prvom otvorení stránky v logu `ERROR ... BatchUpdateException ... insert into PortletPreferences ... already exists` | Pri prvom zobrazení sa súbežne inicializuje viac portletov a dva requesty zapisujú to isté (videné aj pri overovaní tejto kapitoly) | Neškodné, nič nerob. Portál funguje normálne. |
| `psql: ... the input device is not a TTY` | `docker compose exec` bez `-T` v skripte alebo pri presmerovanom výstupe (pipe) | Pridať `-T` |
| `Invoke-WebRequest : A parameter cannot be found that matches parameter name 's'` | Použil si `curl` namiesto `curl.exe` | Písať `curl.exe` ([kapitola 02](02_predpoklady_a_setup.md)) |
| `grep : The term 'grep' is not recognized ...` | PowerShell nemá `grep` | `Select-String` |

---

## Otázky na pohovor

**Ako sa Liferay konfiguruje?**
Hlavne cez `portal-ext.properties` (globálne nastavenia portálu, DB), OSGi konfigurácie (`.config` súbory alebo System Settings v UI, pre jednotlivé moduly) a nastavenia v UI na úrovni inštancie, site alebo portletu. V Dockeri sa properties dajú zadať premennými `LIFERAY_*`.

**Prečo Liferay štartuje tak dlho?**
Pri štarte inicializuje DB (pri prvom štarte vytvára schému), spúšťa ~1 400 OSGi bundlov, robí verify procesy a v dev režime aj Elasticsearch. Spring Boot appka štartuje len to, čo potrebuje.

**Čo je sidecar Elasticsearch?**
Elasticsearch, ktorý si Liferay v dev režime spustí sám ako podproces. V produkcii je nepodporovaný. Treba samostatný Elasticsearch a Liferay sa naň pripojí cez OSGi konfiguráciu. V repe je príklad v `configs/prod/osgi/configs/`.

**Čo všetko je v Liferay databáze?**
Stovky tabuliek: používatelia (`user_`), role, sites (`group_`), stránky (`layout`), obsah, dokumenty (metadáta, súbory sú v `data/document_library`), oprávnenia (`resourcepermission`) a tabuľky vlastných modulov (napr. `demo_task`). Pri migrácii je dôležité vedieť, ktoré z nich aplikácia naozaj používa.

---

**Ďalej:** [04 – Build a deploy modulov](04_build_a_deploy_modulov.md)
