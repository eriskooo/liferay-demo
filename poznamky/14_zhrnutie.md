# 14 – Zhrnutie: 10 základných bodov

Desať najdôležitejších vecí z kapitol 01–13, na ktorých sa dá stavať. Pri každom bode je odkaz na kapitolu s podrobnosťami.

---

## 1. Liferay je hotový portál, do ktorého sa píšu pluginy

Spring Boot appku začínaš od nuly. Liferay už má používateľov, role, stránky, CMS, dokumenty, vyhľadávanie aj workflow. Vývojár k tomu len pridáva moduly. Celé to beží v Tomcate s PostgreSQL a Elasticsearchom.

→ [kapitola 01](01_co_je_liferay.md)

## 2. Základom všetkého je OSGi

Všetko, aj samotný Liferay, je rozdelené do **bundlov**, teda JAR súborov s extra hlavičkami v `MANIFEST.MF`, ktoré generuje `bnd.bnd`. Bundle sa dá nainštalovať, vymeniť alebo zastaviť **za behu**.

Na tom stojí hot deploy: `.\gradlew deploy` skopíruje JAR do `osgi/modules` a portál ho načíta bez reštartu.

→ [kapitola 04](04_build_a_deploy_modulov.md), [kapitola 05](05_osgi_a_gogo_shell.md)

## 3. Komponenty sa skladajú cez Declarative Services, nie cez Spring DI

| OSGi | Spring |
|---|---|
| `@Component` (iná anotácia!) | `@Component` / `@Service` |
| `@Reference` | `@Autowired` / konštruktorová injekcia |
| `service.ranking` | `@Primary` |

Hlavný myšlienkový posun je vzor „whiteboard“: framework nevoláš, len **zaregistruješ službu správneho typu** (`Portlet`, `Application`, `MVCActionCommand`) a portál si ju nájde sám.

Bežiaci kontajner diagnostikuješ v Gogo shelli príkazmi `lb`, `diag` a `services`.

→ [kapitola 05](05_osgi_a_gogo_shell.md)

## 4. API a implementácia sú v oddelených moduloch

Moduly ako `task-api` / `task-service` alebo `greeting-api` / `greeting-impl` fungujú tak, že ostatné moduly závisia **len od exportovaných rozhraní**. Vďaka tomu sa implementácia dá vymeniť.

→ [kapitola 05](05_osgi_a_gogo_shell.md), [kapitola 06](06_service_builder.md)

## 5. Service Builder generuje celú perzistentnú vrstvu

Zo súboru `service.xml` vznikne približne 20 tried a SQL.

**Zlaté pravidlo:** vlastný kód píšeš len do `*LocalServiceImpl` (logika) a `*Impl` (model). Všetko ostatné sa pri ďalšom generovaní prepíše.

ID neprideľuje sekvencia, ale tabuľka `Counter`, preto sú v nich diery. V Spring Boote tomu zodpovedá JPA entita, Spring Data repository a `@Service`.

→ [kapitola 06](06_service_builder.md)

## 6. Portlet má tri fázy: render, action a resource

| Fáza | `p_p_lifecycle` | Čo robí |
|---|---|---|
| **Render** | `0` | Volá sa pri každom zobrazení stránky. Nesmie meniť dáta. |
| **Action** | `1` | Mení dáta. Po nej vždy nasleduje render. |
| **Resource** | `2` | Vracia JSON alebo súbor. |

Pravidlo: **action mení, render zobrazuje**.

Ďalej treba poznať namespace parametrov, CSRF token `p_auth` a `PortletPreferences`, ktorým v Spring Boote zodpovedá `@ConfigurationProperties`.

Portlet je stavový a zviazaný s portálom, a preto sa pri migrácii prepisuje na bezstavové REST API a Angular.

→ [kapitola 07](07_portlet_mvc.md)

## 7. REST v Liferay sa dá robiť štyrmi spôsobmi

Sú to Headless API (`/o/headless-*`), REST Builder (generuje kód z OpenAPI), JAX-RS Whiteboard a staré JSON WS.

Demo používa **JAX-RS Whiteboard**: jedna komponenta `extends Application` s `osgi.jaxrs.application.base`, ktorá je dostupná na `/o/tasks`.

→ [kapitola 08](08_rest_v_liferay.md)

## 8. Liferay sa testuje ťažko, Spring Boot ľahko

Kód je zviazaný s bežiacim portálom (`@Reference`, statické `*Util`) a portál štartuje minúty. V Liferay projektoch je preto málo automatických testov a veľa ručného overovania. Pri migrácii je to riziko aj príležitosť.

V Spring Boote máš `@WebMvcTest`, `@SpringBootTest` a Testcontainers s reálnou DB.

→ [kapitola 09](09_testovanie_liferay.md), [kapitola 10](10_spring_boot_projekt.md)

## 9. Migrácia dát a security má svoje pasce

Service Builder tabuľky sú obyčajné SQL tabuľky. Demo ich importuje Flywayom do vlastnej schémy `tasks` v tej istej DB.

Na čo si dať pozor:

- zachovať pôvodné ID a posunúť sekvenciu za `max(id)`,
- `companyId` / `groupId` znamenajú multi-tenancy,
- časy sú v UTC,
- `long` sa v Liferay JSON serializuje ako string.

Používateľov a role preberá **Keycloak** a Spring Boot je **OAuth2 resource server**, ktorý overuje JWT.

Oprávnenia na konkrétny záznam (`ResourcePermission`) nemajú v Spring Security priamu obdobu. Riešia sa doménovým modelom alebo ACL.

→ [kapitola 11](11_migracia_dat_a_security.md)

## 10. Migruje sa postupne podľa vzoru strangler fig, nie naraz („big bang“)

Pred Liferay sa postaví reverse proxy, nová aplikácia beží vedľa neho a URL sa presmerúvajú **jedna po druhej**, s možnosťou vrátiť sa späť.

Prvým krokom je **inventár** toho, čo sa z Liferay naozaj používa. Business logika je tá ľahšia časť. Ťažšie sú:

- dáta,
- oprávnenia,
- URL (301 presmerovania),
- vyhľadávanie,
- Documents & Media,
- workflow.

Nahrádza sa len to, čo sa naozaj používa.

→ [kapitola 12](12_migracna_strategia.md)

---

## Hlavná myšlienka na pohovor

Z Liferay sa preberá **len vlastná business logika a dáta**. Všetko, čo portál dával „zadarmo“, treba vedome **nahradiť, nechať v Liferay alebo kúpiť** ako iný produkt.
