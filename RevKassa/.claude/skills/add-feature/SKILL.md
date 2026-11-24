---
name: add-feature
description: Ehita RevKassa funktsionaalsus otsast lõpuni — entiteet, repository, mapper, service, REST kontroller, testid ja Vue vaade — kursuse projekti stiilis. Kasuta, kui implementeerid midagi failidest docs/BACKEND-TASKS.md / FRONTEND-TASKS.md (tooted, ladu, ostud, maksed, liikmed, tasud, arved, tasemed, üritused, eelarve, abiline, admini vaated) ja kasutaja tahab, et Claude selle ise ära teeks (mitte õpilasena juhendatult — selleks on skill-rain-ai-backend / skill-rain-ai-frontend). Kasuta ka siis, kui kasutaja ütleb "ehita järgmine task", "tee järgmine samm", "build the next task", "move on with building" või nimetab taski id (B03, B35, F04 ...).
---

# Funktsionaalsuse ehitamine

## Töövoog: üks task korraga

1. **Vali task.** Kui kasutaja nimetas taski (nt "B35"), võta see. Muidu leia `docs/BACKEND-TASKS.md`-st
   (või `FRONTEND-TASKS.md`-st, kui kasutaja tahab frontendi) **esimene märkimata task verstapostide
   järjekorras, mille kõik "pärast ..." / "vajab ..." eeltingimused on märgitud** (`[x]`). Tasknumbrid on
   id-d, mitte järjekord. Ütle kasutajale ühe lausega, millise taski võtad ja miks; kui mitu sobivad
   võrdselt, vali esimene ja maini teisi.
2. **Vaata, mis on juba olemas.** `git status` ja olemasolev kood: kasutaja võib olla taski osaliselt
   ise teinud (nt B03 entiteet). Ehita sellele peale, ära kirjuta kasutaja tööd üle ega tee seda ilma
   põhjuseta ümber. Kui `docs/tasks/backend/instructions/<task>-*-juhend.md` on olemas, järgi selle
   disaini (paketid, klassinimed, sammud) — see on kokkulepitud lahendus.
3. **Loe taski allikad** (allpool "Loe enne alustamist"). Kui taskis on midagi, mida PLAN.md, DB-MODEL.md
   ja taskifail ei otsusta ja mõistlikku vaikimisi valikut pole, küsi enne koodi kirjutamist.
4. **Ehita** kihtide järjekorras koos testidega (allpool). Uued `Error` väärtused enum'i; uued seaded
   `application.properties`-i (saladused keskkonnamuutujast, arenduse vaikeväärtus ainult siis, kui
   task seda lubab).
5. **Kontrolli:** `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test` kaustas `backend/`
   (andmebaas peab töötama, vt `run-revkassa`). Kõik testid peavad läbima, mitte ainult uued. Endpointi
   puhul proovi see ka käivitatud rakenduses läbi (curl / Swagger).
6. **Lõpeta:** märgi task `docs/BACKEND-TASKS.md`-s `[x]` ainult siis, kui "Valmis, kui" kehtib. Uuenda
   `docs/`, kui API või mudel muutus taski kirjeldusest erinevaks. Ära commit'i, kui kasutaja ei palu.
   Anna lühike kokkuvõte: mis lisandus (failid), mis testid jooksid ja tulemus, kõrvalekalded taskist,
   mida turvalisuse mõttes arvestasid (ja kas midagi jäi lahtiseks), järgmine task. **Siis peatu** — järgmine task alles siis, kui kasutaja ütleb.

## Põhimõtted (kehtivad igale taskile)

### 1. Puhas kood
- Kood peab olema loetav ja hooldatav: kirjeldavad nimed (klass, meetod, muutuja ütleb, mida see teeb), üks
  vastutus meetodi ja klassi kohta, ei mingit maagilist arvu ega stringi (konstant või `Error` enum).
- **Pikk meetod jaotatakse alammeetoditeks**, mille nimed loevad nagu samm-sammuline kirjeldus
  (`validatePin(pin)`, `findTrustedDevice(request)`, `createSession(member)`). Hea märk: avalik meetod mahub
  ekraanile ja seda saab lugeda ilma alammeetoditesse hüppamata. Abimeetodid on `private` ja tulevad
  väljakutsuja järel väljakutsumise järjekorras (`skill-jarjesta-meetodid`).
- Varajane `return`/`throw` sügava `if`-pesastuse asemel; ei dubleeritud loogikat (ühine osa eraldi meetodisse);
  kommentaar ainult siis, kui see selgitab **miks**, mitte mida.
- Ära lisa koodi, mida task ei nõua (spekulatiivsed abstraktsioonid, kasutamata meetodid, ülemäärased liidesed).
- Enne lõpetamist loe oma muudatus läbi nagu koodiülevaataja ja korrasta, mis on kohmakas.

### 2. Projekti struktuur
- Failide paigutus ja nimed täpselt nagu `docs/backend/projekti-struktuur.md`, `backend/CLAUDE.md`,
  `docs/frontend/projekti-struktuur.md` ja `docs/frontend/vue-komponendi-struktuur.md` ütlevad
  (kihid, paketid, `<Ressurss>Controller` / `Service` / `Repository` / `Mapper`, DTO nimed, `views/`, `api-services/`).
- Enne uue klassi loomist kontrolli, kas sarnane juba on olemas (jagatud DTO `controller/common/dto/`,
  olemasolev service meetod, `Error` väärtus) — taaskasuta, ära dubleeri.
- Kui task tundub nõudvat struktuurist kõrvalekaldumist, küsi enne või põhjenda kokkuvõttes.

### 3. Turvalisus kirjutamise ajal
Mõtle iga endpointi ja meetodi juures läbi, kuidas seda saaks kuritarvitada:
- **Ligipääs:** kes tohib seda kutsuda? `/api/admin/**` ainult parooliga `ADMIN` sessioon. Liige näeb ja muudab
  ainult **oma** andmeid — liikme id tuleb sessioonist, mitte päringust (IDOR). Test ka keelatud juhtum.
- **Sisend:** kogu sisend valideeritakse (`@Valid` + Bean Validation, piirid, formaadid); ära usalda kliendi
  arvutatud väärtusi (summa, hind, saldo arvutab server). SQL ainult parameetritega (`@Query` named parameters).
- **Saladused:** PIN-e, paroole, tokeneid, `pin_hmac`/`password_hash`-i ega võtmeid ei logita ega tagastata
  vastuses; DTO-sse ainult vajalikud väljad (entiteeti ei tagastata kunagi). Saladused keskkonnamuutujatest,
  mitte koodist ega SQL skriptidest. Tokenid andmebaasis ainult räsina; juhuslikkus `SecureRandom`-ist.
- **Info leke:** veateated ei paljasta, kas e-post/PIN/kirje on olemas, kui see pole vajalik (sama vastus
  valede andmete korral); `ApiError.message` ei sisalda stack trace'i ega sisemisi detaile.
- **Kuritarvitus:** sisselogimisel ja e-kirju saatvatel endpointidel katsete piirang; kirjutavad
  operatsioonid transaktsioonis ja kaitstud topeltsaatmise / võidujooksu eest (unikaalsus, `@Version`).
- **Küpsised/sessioon:** `HttpOnly`, `SameSite`, tootmises `Secure`; CSRF jääb sisse; sisselogimisel uus sessiooni id.
- Kui märkad olemasolevas koodis turvaprobleemi, ära paranda vaikides väljaspool taski ulatust — maini see
  kokkuvõttes (põhjalikuks auditiks on `skill-security-audit`).

### 4. Testid, mis päriselt kaitsevad
- Testi käitumist ja ärireegleid, mitte implementatsiooni detaile. Iga "Valmis, kui" punkt ja iga
  taskifaili veaolukord on vähemalt ühe testiga kaetud — ka keelatud ja piirjuhud (0, negatiivne,
  tühi, puuduv, teise liikme andmed, aegunud, topeltkatse).
- Testi nimi ütleb olukorra ja oodatud tulemuse (`wrongPin_returns403WithRemainingAttempts`).
  Üks test = üks põhjus ebaõnnestuda; arrange / act / assert selgelt eristatud.
- Aeg `Clock` beanist (fikseeritud kell testis), juhuslikkus ja välised teenused (e-post, AI) võltsitud.
  Testid ei sõltu üksteise järjekorrast ega `3_import.sql` muutuvatest andmetest rohkem kui vaja.
- Kontrolli DB-MODEL.md invariante pärast kirjutavaid operatsioone (saldo = ridade summa jne).

### 5. Andmete terviklus ja vead
- Kirjutav äritoiming on **üks** `@Transactional` meetod service'is: kas kõik read kirjutatakse või mitte
  midagi. Pearaamatusse ainult lisatakse.
- Viga visatakse projekti erindina (`ForbiddenException` + `Error` väärtus, `PrimaryKeyNotFoundException`),
  mitte ei neelata ega tagastata `null`-i / maagilist väärtust. Ära püüa `Exception`-it ilma põhjuseta.
- Andmebaasi piirangud (unikaalsus, `CHECK`, FK) on viimane kaitseliin — kontrolli reegel ka service'is,
  et kasutaja saaks arusaadava `errorCode`-i, mitte 500.
- `Optional` jääb repository/service piirile; kontrolleri ja DTO tasandil `null`-i ei levitata.

### 6. Jõudlus ja mastaap (mõistlikkuse piires)
- Nimekirjad, mis kasvavad ajas (ajalugu, ostud, arved), on lehekülgedega või piiratud.
- Väldi N+1 päringuid (`@ManyToOne(fetch = LAZY)` + vajadusel `join fetch` / projektsioon); ära laadi
  tervet tabelit mällu, et Javas filtreerida või summeerida — tee seda SQL-is.
- Optimeeri ainult siis, kui on reaalne põhjus; loetavus on esimene.

### 7. Logimine
- Logi olulised sündmused (sisselogimine, lukustus, makse kinnitamine, e-kirja saatmise viga) koos id-de
  ja ajaga, mitte isikuandmete ega saladustega. `INFO` sündmustele, `WARN` kahtlasele, `ERROR` ootamatule.
- Ära logi igat päringut ega suurt objekti tervikuna.

### 8. API ja dokumentatsiooni järjepidevus
- API kuju (teed, väljanimed camelCase'is, staatuskoodid, `ApiError`) järgib taskifaili ja PLAN.md-d;
  olemasolevat kontrakti ei muudeta vaikides. Kui pead kõrvale kalduma, uuenda samas muudatuses
  `docs/` (PLAN.md API tabel, taskifail, DB-MODEL.md) ja maini kokkuvõttes.
- Iga uus `Error` väärtus: ingliskeelne `message`, unikaalne `errorCode`, ja märge, et frontend vajab
  eestikeelset vastet (`utils/errorMessages.js`, kui frontend on olemas).
- Swaggeris (`@Operation`, `@ApiResponses`) on kõik taski veaolukorrad kirjas.
- Frontendis: UI tekst eesti keeles, ligipääsetav (päris `<button>`/`<label>`, fookus, kontrast,
  puutealad ≥ 44px), raha ja kuupäevad ainult `formatEuro` / `formatDate` kaudu.

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
- Sisselogimine: PIN (unikaalne, 6 numbrit, ainult `pin_hmac`, mitte kunagi lihtsalt ega logis) töötab **ainult usaldatud seadmes** (`REVKASSA_DEVICE` küpsis → `trusted_device`, andmebaasis ainult SHA-256); PIN-sessioon 1 min, lukustus seadme kaupa. Mujal e-post + bcrypt parool, sessioon 30 min, lukustus IP kaupa. `/api/admin/**` vajab parooliga sessiooni ja `ADMIN` rolli — PIN ei anna kunagi admini õigusi. Parooli määrab liige ühekordse e-posti lingiga (`password_token`, ainult SHA-256); tokeneid, PIN-e ega paroole ei logita.
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
