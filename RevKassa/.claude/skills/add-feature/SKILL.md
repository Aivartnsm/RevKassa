---
name: add-feature
description: Ehita RevKassa funktsionaalsus otsast lõpuni — entiteet, repository, mapper, service, REST kontroller, testid ja Vue vaade — kursuse projekti stiilis. Kasuta, kui implementeerid midagi failidest docs/BACKEND-TASKS.md / FRONTEND-TASKS.md (tooted, ladu, ostud, maksed, liikmed, tasud, arved, tasemed, üritused, eelarve, abiline, admini vaated) ja kasutaja tahab, et Claude selle ise ära teeks (mitte õpilasena juhendatult — selleks on skill-rain-ai-backend / skill-rain-ai-frontend).
---

# Funktsionaalsuse ehitamine

Loe enne alustamist:
- `docs/PLAN.md` (reeglid, API visand) ja `docs/DB-MODEL.md`
- `backend/CLAUDE.md` ja `docs/backend/projekti-struktuur.md` — backendi konventsioonid ja failide paigutus
- `docs/frontend/projekti-struktuur.md` ja `docs/frontend/vue-komponendi-struktuur.md` — frontendi konventsioonid
- taskifail `docs/tasks/backend|frontend/...` ja selle implementatsiooniplaan (`-IMPLEMENTATSIOON.md`), kui need on olemas
- "RevKassa Mockups" lõuend, kui ehitad UI-d

## Backend (`backend/src/main/java/ee/revkassa/`)

Ehita kihtide järjekorras: persistence (entiteet → repository → mapper) → service → controller → testid.
Kontrolli enne iga kihi juures, mis on juba olemas — sageli puudub ainult controller või üks service meetod.

- **Entiteet** `persistence/<entiteet>/<Entiteet>.java` — Lomboki `@Getter`/`@Setter`, ilma `@Data`-ta. `@Version`, kui tabelis on `version`. Enumid `EnumType.STRING`.
- **Repository** samas paketis, lühikesed meetodinimed + `@Query` named parameters stiilis.
- **Mapper** samas paketis, MapStruct `@Mapper(componentModel = "spring")`, iga target-väli eksplitsiitselt (`source` või `ignore = true`).
- **Service** `service/<Ressurss>Service.java` — äriloogika, `@Transactional` kirjutavatel meetoditel, `getValid<Entiteet>By(id)` muster.
- **Controller** `controller/<ressurss>/` või `controller/admin/<ressurss>/`, DTO-d `dto/` alampaketis (jagatud DTO-d `controller/common/dto/`). `@Operation` + `@ApiResponses` igal meetodil. Kontroller ei tagasta kunagi entiteeti.
- **Vead** `infrastructure/` erinditega, sõnum ja kood `Error` enum'is, vastus `ApiError { message, errorCode }`.

### Domeenireeglid, mida ei tohi rikkuda
- Raha on `long` sentides otsast lõpuni; eurodeks vormindatakse ainult UI-s.
- Saldo muutub ainult `AccountService.post(...)` kaudu (rida **ja** `member.balanceCents` samas transaktsioonis). Laoseis ainult `StockService.move(...)` kaudu.
- Pearaamatu ridu ei muudeta ega kustutata; parandused on uued read.
- Hind kopeeritakse ostureale. Üks hinnakiri; külalised ostavad võõrustava liikme arvele; võlalimiiti pole.
- Liikmemaks ja ürituse tasud lähevad saldole `AccountService.post` kaudu; üks `membership_charge` liikme ja kuu kohta; tühistused on uued read. Eelarveread ei puuduta saldosid ega ladu.
- AI abiline: ainult admin, `EventAssistant` kaudu, ainult ürituse andmed (mitte nimed, e-post, saldod), midagi ei kirjutata ilma admini nõusolekuta. Testides võlts-abiline.
- E-kirjad ainult `Mailer` liidese kaudu, saldot ei muuda; testides võlts-`Mailer`.
- Sisselogimine: liige ainult unikaalse 6-kohalise PIN-iga (`pin_hmac`, mitte kunagi lihtsalt ega logis), katsete piirang, 1 min tegevusetus. `/api/admin/**` vajab eraldi e-posti + parooli sisselogimist.
- UI tekst ja e-kirjad on eesti keeles; kood, enumid, tabelid, API väljad ja `ApiError.message` inglise keeles. Kasutajale näitab eestikeelse veateate frontend `errorCode` järgi (`utils/errorMessages.js`) — uus `Error` väärtus vajab sinna vastet.

### Testid
- Service loogika: `@SpringBootTest` päris Postgresi vastu (vt `run-revkassa`), iga test `@Transactional`. Kontrolli DB-MODEL.md invariante.
- Controller: `@WebMvcTest` + `MockMvc` valideerimise, staatuskoodide ja `ApiError` jaoks.

## Frontend (`frontend/src/`)

Ehita järjekorras: route + vaate fail → staatiline template mockupi järgi → `data` → service funktsioon `api-services/` all → sidumine (`beforeMount` või sündmus) → veaolukorrad → komponentideks eraldamine.

- Options API projekti järjekorras; axios ainult `api-services/` kaudu; `.then()/.catch()/.finally()`.
- Vaated `views/`, admini omad eesliitega `Admin`; korduvad osad `components/common|forms|modals|tables/`.
- Jagatud olek props/emits ja `sessionStorage` (`utils/session.js`, `utils/basket.js`) kaudu.
- Liikme vaated mobile-first (390px, siis `@media (min-width: 1024px)` tahvlile), admini omad desktop-first, aga telefonis kasutatavad.
- Raha ainult `formatEuro(cents)` abil.

## Valmis tähendab
`./gradlew test` (kaustas `backend/`) ja `npm run build` läbivad, `docs/` on uuendatud, kui API
või mudel muutus, ja funktsionaalsus on päriselt läbi proovitud (vt `run-revkassa`) —
teata, mida käivitasid. Skeemimuudatused: skill `muuda-andmebaasi-skeemi`.
Pärast muudatust võib meetodid järjestada skilliga `skill-jarjesta-meetodid`.
