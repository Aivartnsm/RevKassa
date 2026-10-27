# RevKassa — projekti plaan

Väike iseteeninduslik baarikassa ühele organisatsioonile: liige võtab õlle, märgib selle ise
üles ja summa läheb tema arvele. Adminid hoiavad laoseisu ja hinnad korras ning kinnitavad, kui
liige on oma võla tasunud. Igakuine liikmemaks läheb samale saldole ja adminid saavad
võlglastele e-postiga maksenõude saata. Lisaks korraldavad adminid üritusi: osalejad,
osalustasu liikmetaseme järgi ja ürituse eelarve, valikulise AI abilisega eelarve ja
retseptide jaoks.

Rakendus on **eesti keeles** (UI ja e-kirjad). Kood, API väljad ja andmebaasi tabelid on
inglise keeles; dokumentatsioon on eesti keeles.

Andmemudel: [DB-MODEL.md](DB-MODEL.md). Taskid: [backend](BACKEND-TASKS.md), [frontend](FRONTEND-TASKS.md).
Konventsioonid: [backend/CLAUDE.md](../backend/CLAUDE.md), [backendi struktuur](backend/projekti-struktuur.md),
[frontendi struktuur](frontend/projekti-struktuur.md), [Vue komponent](frontend/vue-komponendi-struktuur.md).
Mockupid: "RevKassa Mockups" disainilõuend.

## Ulatus (v1)

Sees:
- Liikme sisselogimine **ainult PIN-iga** (nime ega e-posti ei valita); rollid `MEMBER` ja
  `ADMIN` (admin on ka liige). Admini lehed nõuavad lisaks e-posti ja parooli.
- Iseteeninduslik ost: vali joogid, kinnita, summa läheb liikme saldole.
- Isiklik ostuvaade: iga liige märgib lemmikjoogid tärniga ja järjestab need; lemmikud on
  ees, kõik muu all.
- Liikme saldo: positiivne = ettemaks, negatiivne = võlg. **Võlalimiiti ei ole:** adminid
  jälgivad suuri võlgu ülevaatelehelt.
- Maksmine kaardi või pangaülekandega — võla tasumiseks või **ette maksmiseks**. Rakendus
  makseid **ei töötle**: liige teatab maksest, admin kinnitab selle pangaväljavõtte /
  kaarditerminali järgi.
- Laoseis toote kaupa pearaamatuna: tarned, müügid, inventuuri parandused, mahakandmised.
- Admin: tooted ja hinnad, tarned, inventuur, ootel maksed, liikmed, saldod.
- Külalistel kontot pole: liige ostab külalisele oma arvele ja vastutab tasumise eest. Üks
  hinnakiri kõigile.
- **Igakuine liikmemaks** liikmetaseme järgi (nt Noorliige maksab vähem). Admin muudab tasusid
  (ajalugu säilib), tasu läheb igal kuul automaatselt iga liikme saldole. Adminid näevad, kellelt
  igal kuul tasu võeti, ja saavad tasu tühistada.
- **Arve e-kirjad:** admin saab admini paneelist saata negatiivse saldoga liikmetele
  maksenõude e-postiga. Iga saadetud kiri salvestatakse.
- **Liikmetasemed:** igal liikmel on tase (alguses 5, nt Täisliige, Noorliige, Vilistlane,
  Toetajaliige, Auliige). Adminid nimetavad ümber, lisavad, järjestavad ja võtavad kasutusest
  maha; arv ei ole fikseeritud.
- **Ürituste haldus** (admin): loo üritus, määra osalustasu taseme kaupa, ava registreerimine;
  liikmed **registreeruvad ise** rakenduses või lisab admin nad (tasu läheb saldole); osalejat
  saab eemaldada ainult admin; ürituse eelarve kategooriate kaupa (joogid, snäkid, toit,
  meelelahutus, ruum, kaunistus, muu) planeeritud ja tegelike summadega võrreldes tasudest
  laekuvaga.
- **AI abiline (valikuline):** ürituse eelarves saab admin küsida keelemudelilt abi:
  eelarve mustand, ostukogused, retseptid osalejate arvu järgi. Ettepanekud lisatakse eelarvesse
  ainult siis, kui admin need vastu võtab. Väljas, kui API võtit pole seadistatud.

Väljas (praegu): veebimaksed, vaadi/mahu arvestus, mitu asukohta, käibemaksuarved /
raamatupidamise eksport, muud e-kirjad (kviitungid, automaatsed meeldetuletused), liikmete
iseregistreerimine (organisatsiooni liikmeks), liikme enda ürituselt loobumine, piletimüük /
ürituse maksed, AI mujal kui ürituse abilises.

## Reeglid

Sisselogimine
- Liige logib sisse, sisestades klahvistikul oma PIN-i. PIN üksi tuvastab liikme, seega on
  PIN-id **unikaalsed**, täpselt **6 numbrit** ning admin valib / lähtestab need (liige saab
  hiljem ise muuta). PIN salvestatakse HMAC-SHA256-na serveri salajase võtmega, et
  sisselogimisel saaks liikme selle järgi üles leida ilma PIN-i ennast salvestamata.
- Jõurünnaku kaitse: pärast 5 valet PIN-i samast seadmest/IP-st lukustub klahvistik
  1 minutiks (iga kordusega kahekordistub). Õnnestunud sisselogimised ja lukustused logitakse.
- PIN-sessioon lõpeb "Logi välja" / "Valmis, logi mind välja" peale või pärast **1 minutit
  tegevusetust** igas seadmes (ühine tahvel ja isiklikud telefonid). Seda jõustab server
  (sessiooni aegumine) ja peegeldab UI, mis naaseb PIN-i klahvistikule.
- Admini lehed vajavad teist sisselogimist e-posti ja parooliga (bcrypt), sessiooni aegumine
  30 minutit tegevusetust. PIN-sessioon üksi ei anna kunagi admini õigusi.

Raha
- Kõik summad on täisarvulised **eurosendid** (Javas `long`, SQL-is `bigint`). Mitte kunagi `double`.
- Saldo muutub ainult `account_entry` rea kaudu, mis kirjutatakse samas transaktsioonis
  vahemällu salvestatud `member.balance_cents` uuendusega.
- Ühiku hind kopeeritakse igale ostureale, et hilisemad hinnamuutused ajalugu ümber ei kirjutaks.

Ostud
- Liige võib oma ostu tagasi võtta 10 minuti jooksul; admin võib tagasi võtta mis tahes ostu.
- Tagasivõtmine ei kustuta ridu: ost märgitakse `REVERSED` ja kirjutatakse kompenseerivad
  `account_entry` ja `stock_movement` read.

Maksed
- Liige võib enne ostmist ette maksta; positiivne saldo on ettemaks ja ostud vähendavad seda.
- Liige teatab summa, viisi (`CARD` / `BANK_TRANSFER`) ja valikulise viite → `PENDING`. Saldot
  muudavad ainult `CONFIRMED` maksed. Admin saab makse ka kohe kinnitatuna sisestada.

Ladu
- `product.stock_qty` on vahemälu tema `stock_movement.quantity_delta` summast.
- Müüki ei blokeerita, kui laoseis jõuab 0-ni — külmkapp on tõde. Negatiivset laoseisu
  näidatakse adminile kui "vajab inventuuri".
- Inventuur: admin sisestab loetud koguse; rakendus kirjutab vahe kohta `STOCKTAKE` liikumise.

Liikmemaks
- Tasu sõltub liikme **tasemest**: üks summa taseme kohta, salvestatud koos kuuga, millest see
  kehtib (`membership_fee`: tase + kuu + summa). Admin muudab tasusid, salvestades ühe või mitme
  taseme uued summad alates mingist kuust; vanad kuud jäävad vanade summadega. Taseme tasu 0 €
  tähendab, et sellelt tasemelt tasu ei võeta (nt Auliige). Uus tase on tasuta, kuni tasu määratakse.
- Tasu läheb liikme saldole nagu ost. "Makstud" tähendab seega, et saldo ei ole negatiivne;
  tasumata tasud on nähtavad võlana ja neid katavad arve e-kirjad.
- Iga kuu 1. kuupäeval võtab rakendus tasu igalt liikmelt, kes on **aktiivne, ei ole
  tasust vabastatud** ja liitus enne seda kuud — **tema taseme tasu sel päeval** (tase ja summa
  kopeeritakse tasureale). Taseme muutus kehtib järgmisest tasust. Töö jookseb ka iga päev ja
  käivitumisel ning täidab ainult puuduvad tasud, nii et 1. kuupäeval maas olnud server teeb
  need järele. Unikaalne `(member, month)` rida teeb topelttasu võimatuks.
- Admin võib liikme tasust vabastada (nt auliikmed) ja üksiku tasu märkusega **tühistada**,
  mis kirjutab kompenseeriva rea. Tühistatud tasu ei saa uuesti tühistada.
- Liige näeb saldo ajaloos "Liikmemaks · oktoober 2026".

Arve e-kirjad
- Liikmel on valikuline `email` (adminil kohustuslik). E-postita liikmed on nimekirjas, aga
  neid ei saa valida.
- Admini "Arved" vaade näitab negatiivse saldoga liikmeid koos e-posti ja viimase arve
  kuupäevaga. Admin märgib liikmed (vaikimisi kõik, kellel on e-post ja kellele pole viimase
  7 päeva jooksul arvet saadetud), vaatab ühe kirja eelvaadet ja saadab.
- Arve on maksenõue, mitte käibemaksuarve, ja **saldot ei muuda**. Sellel on number
  (`2026-0001`, aasta kaupa), saatmise hetke võlasumma, saldo read eelmisest arvest alates
  (kuni viimased 60 päeva), maksetähtaeg (14 päeva) ja makseinfo: organisatsiooni nimi, IBAN ja
  selgitus "Arve 2026-0001" (mitte viitenumber).
- Kirjad on **eesti keeles**, eesti vormingutega (`27,40 €`, `30.10.2026`, "Liikmemaks ·
  oktoober 2026"). Liige teatab maksest ikka rakenduses ja admin kinnitab selle tavapäraselt.
- Lihttekstiga e-kiri SMTP kaudu. Iga kiri saadetakse ja salvestatakse eraldi, nii et üks halb
  aadress ei peata teisi; ebaõnnestunud kirjad näitavad viga ja neid saab uuesti saata.
- Seaded tulevad konfiguratsioonist, mitte andmebaasist: `revkassa.invoice.org-name`, `.iban`,
  `.from`, `.reply-to`, `.due-days` ning standardsed `spring.mail.*` SMTP seaded (saladused
  keskkonnamuutujatest). Ilma `spring.mail.host`-ita (arendus) kirjutatakse kirjad logisse.

Keel
- Kogu UI tekst on eesti keeles, kirjutatud otse Vue komponentidesse (i18n teeki pole; üks
  keel). Raha `27,40 €`, kuupäevad `16.10.2026`, kõikjal `et-EE` vorming.
- API veateated (`ApiError.message`) on eesti keeles, sest UI näitab neid. Enumid,
  väljanimed, logid ja kood on inglise keeles.

Liikmetasemed
- `membership_level` read: nimi, järjekord, aktiivne. Viis taset luuakse koos skeemiga
  (`2_create.sql`); adminid haldavad neid liikmete vaates. Kasutusel olevat taset saab kasutusest
  maha võtta (uutele valikutele peidetud), aga mitte kustutada. Igal liikmel on täpselt üks
  tase; uus liige saab esimese.
- Tasemed määravad nii igakuise liikmemaksu kui ürituste osalustasud.

Üritused
- Üritusel on nimi, algusaeg, koht, kirjeldus ja staatus `PLANNED` → `CLOSED` (pärast
  toimumist; muudatusi enam ei saa) või `CANCELLED`.
- Osalustasu: üks summa taseme kohta (0 € lubatud). Osaleja lisamisel kopeeritakse tema
  praegune tase ja selle taseme tasu osaleja reale ning tasu läheb saldole (`EVENT_FEE` rida).
  Hilisemad tasumuudatused kehtivad ainult pärast seda lisatud osalejatele; vaade hoiatab selle eest.
- Osaleja eemaldamine enne ürituse sulgemist kirjutab `EVENT_FEE_REVERSAL` rea. Ürituse
  tühistamine tagastab kõigi järelejäänud osalejate tasud. Midagi ei kustutata.
- Registreerimine: üritusel on **registreerimine avatud** lüliti. Kuni üritus on `PLANNED` ja
  registreerimine avatud, näevad liikmed seda rakenduses "Üritused" all koos oma tasuga ja
  saavad ühe kinnitusega registreeruda ("Tasu 15,00 € lisatakse sinu saldole").
  Registreerumine töötab nagu lisamine: tase ja tasu kopeeritakse, `EVENT_FEE` rida. Saldo
  limiiti pole, nagu mujalgi.
- Liikmed **ei saa** registreerumist tühistada: rakendus ütleb, et tuleb adminiga ühendust
  võtta. Osalejaid eemaldab ainult admin (tasu tagastatakse). Admin saab liikmeid lisada ka
  pärast registreerimise sulgemist.
- Osalejate nimekiri näitab, kes registreerus ise ja kelle lisas admin. Liikmed näevad
  ajaloos "Ürituse tasu · <üritus>". Liikme külalised kaetakse eelarvest, mitte osalejatena.

Ürituse eelarve
- Eelarveread: kategooria, kirjeldus, planeeritud summa, tegelik summa (täidetakse pärast
  ostmist), valikuline märkus ja kas rida tuli AI abiliselt.
- Eelarvevaade näitab planeeritud ja tegelikke summasid kategooriate kaupa, tasudest
  laekuvat (osalejate tasude summa) ja tulemust (tulu − kulud). See on planeerimisleht:
  eelarveread ei puuduta kunagi saldosid ega laoseisu.

AI abiline (valikuline)
- Kutsub backendist Anthropic Messages API-t (Anthropic Java SDK), mudel konfiguratsioonist
  (`revkassa.ai.model`, vaikimisi `claude-sonnet-5-5`). Sees ainult siis, kui
  `ANTHROPIC_API_KEY` on seatud; muidu peidab UI abilise.
- Ainult adminile, korraga ühe ürituse kohta. Kiirtegevused: "Eelarve mustand", "Kui palju
  osta", "Retsept N inimesele", lisaks vaba küsimus. Vastused on eesti keeles.
- Päring sisaldab ainult ürituse andmeid: nimi, kuupäev, osalejate arv taseme kaupa, praegused
  eelarveread ja admini küsimus. **Mitte kunagi liikmete nimesid, e-posti ega saldosid.**
- Vastus tuleb tekstina koos soovitatud eelarveridadega (struktureeritud JSON). Igal
  ettepanekul on "Lisa eelarvesse"; midagi ei lisata automaatselt. Vastu võetud read
  märgitakse AI-ks.
- Piirangud: 20 päringut admini kohta päevas, väljundi maht piiratud, aegumised ja vead
  näidatakse kujul "Abiline ei vastanud" ilma lehte katki tegemata. Päringuid ja vastuseid ei
  salvestata; logisse läheb ainult kes küsis, millal ja tokenite arv.

## Arhitektuur

- Backend: Spring Boot 4 (Java 21), kaustas `backend/`, pakett `ee.revkassa`. Web MVC REST API
  `/api` all, Spring Data JPA, MapStruct, Bean Validation, springdoc (Swagger UI), Spring
  Security (sessiooniküpsis + CSRF).
- Kihid: `controller/<ressurss>/` (+ `dto/`), `controller/admin/<ressurss>/`, `service/`,
  `persistence/<entiteet>/` (entiteet, repository, mapper), `infrastructure/` (vead,
  turvalisus, konfiguratsioon, e-post, AI). Vt [backendi struktuur](backend/projekti-struktuur.md).
- Vead: `infrastructure/` erindid (`PrimaryKeyNotFoundException`, `DataNotFoundException`,
  `ForbiddenException`), vastuse keha `ApiError { message, errorCode }`, sõnumid ja koodid
  ühes `Error` enum'is.
- Andmebaas: PostgreSQL 16 WSL-is (`localhost:5432`, andmebaas/kasutaja/parool `revkassa`).
  Skeem on failides `docs/database/1_drop.sql`, `2_create.sql`, `3_import.sql`, mida käivitatakse
  käsitsi (IntelliJ Database konsool või `psql`); Hibernate ainult valideerib.
  Tootmises tehakse skeemimuudatus käsitsi muudatusskriptiga (`docs/database/muudatused/`).
- E-post: `spring-boot-starter-mail`; igakuine tasutöö kasutab `@Scheduled` (üks instants,
  eraldi ajastamise teeki pole).
- AI: Anthropic Java SDK (`com.anthropic:anthropic-java`) `EventAssistant` liidese taga;
  ilma võtmeta väljalülitatud implementatsioon, testides võlts.
- Frontend: Vue 3 + Vite kaustas `frontend/`, Options API, Vue Router, axios (`api-services/`).
  Jagatud olek props/emits ja `sessionStorage` abil, Pinia't pole. Arenduses suunab Vite `/api`
  pordile `:8080`. Väljalaskeks kopeeritakse ehitatud rakendus Springi staatilistesse
  ressurssidesse, nii et kõik läheb välja ühe jar-failina. Vt [frontendi struktuur](frontend/projekti-struktuur.md).
- **Liikme vaated on mobile-first:** kujundatud ~390px telefonile ja suurendatud
  `min-width` meediapäringutega. Telefon: üks veerg, alumine tabiriba (Osta · Üritused ·
  Konto · Logi välja), tellimuse summa kleepuval alumisel ribal, puutealad ≥ 44px.
- **Ühine baaritahvel:** 11" 1920×1200 landscape, mida brauser näitab umbes
  **1280×800 CSS px**-ina (1,5× skaleerimine). Alates `min-width: 1024px` lähevad liikme vaated
  tahvli paigutusse: päis nime, saldo ja Osta · Üritused · Konto · Logi välja nuppudega;
  ostuvaade 4-veerulise joogiruudustiku ja paremal alati nähtava tellimuspaneeliga (eraldi
  tellimuse sammu pole); konto kahes veerus; suurem klahvistik ja puutealad (≥ 52px).
- Admini vaated on desktop-first, aga peavad olema telefonis kasutatavad.

## API visand

| Meetod ja tee | Kes | Eesmärk |
|---|---|---|
| `POST /api/auth/pin` | kõik | `{pin}` → liikme sessioon (piiratud katsete arv) |
| `POST /api/auth/admin` | admin | `{email, password}` → admini sessioon |
| `POST /api/auth/logout` | kõik | lõpeta sessioon |
| `GET /api/me` | liige | profiil ja saldo |
| `GET /api/products` | liige | aktiivsed tooted hinna ja laoseisuga |
| `POST /api/purchases` | liige | `{lines:[{productId, quantity}]}` → ost |
| `POST /api/purchases/{id}/undo` | liige (oma, ≤10 min) / admin | võta ost tagasi |
| `GET /api/me/entries` | liige | saldo ajalugu |
| `GET /api/me/favourites` | liige | lemmiktoodete id-d järjekorras |
| `PUT /api/me/favourites` | liige | `{productIds:[...]}` — asendab järjestatud nimekirja |
| `POST /api/payments` | liige | teata maksest |
| `GET /api/me/payments?status=PENDING` | liige | oma kinnitamist ootavad maksed |
| `GET /api/events` | liige | eelseisvad registreerimiseks avatud üritused, oma tasu ja kas registreerunud |
| `POST /api/events/{id}/signup` | liige | registreeru; võtab oma taseme tasu (tühistamise endpointi pole) |
| `GET/POST/PUT /api/admin/products` | admin | toodete ja hindade haldus |
| `POST /api/admin/stock/deliveries` | admin | tarne sisestamine (toode, kogus, ühiku hind) |
| `POST /api/admin/stock/stocktake` | admin | loetud kogused → parandused |
| `POST /api/admin/stock/write-offs` | admin | katki / aegunud |
| `GET /api/admin/payments?status=PENDING` | admin | kinnitamist ootavad maksed |
| `POST /api/admin/payments/{id}/confirm`, `/reject` | admin | otsusta makse üle |
| `GET/POST/PUT /api/admin/members` | admin | liikmed ja rollid |
| `POST /api/admin/members/{id}/pin` | admin | määra või lähtesta liikme PIN |
| `POST /api/admin/members/{id}/adjustments` | admin | käsitsi saldo parandus märkusega |
| `GET /api/admin/fees` | admin | praegune tasu taseme kaupa ja ajalugu |
| `POST /api/admin/fees` | admin | `{validFrom: "2026-11", fees:[{levelId, amountCents}]}` — uued tasud alates kuust |
| `GET /api/admin/fees/charges?month=2026-10` | admin | kellelt kuu tasu võeti, tühistatud |
| `POST /api/admin/fees/charges/{id}/waive` | admin | `{note}` → kompenseeriv rida |
| `GET /api/admin/invoices/candidates` | admin | negatiivse saldoga liikmed, e-post, viimane arve |
| `GET /api/admin/invoices/preview?memberId=` | admin | kirja pealkiri ja sisu nagu see saadetaks |
| `POST /api/admin/invoices` | admin | `{memberIds:[...]}` → saadab, tulemus liikme kaupa |
| `GET /api/admin/invoices?memberId=` | admin | saadetud arved, uuemad ees |
| `POST /api/admin/invoices/{id}/resend` | admin | ebaõnnestunud kirja uus katse |
| `GET/POST/PUT /api/admin/levels` | admin | liikmetasemed (nimi, järjekord, aktiivne) |
| `GET/POST /api/admin/events` | admin | ürituste nimekiri / loomine |
| `GET/PUT /api/admin/events/{id}` | admin | ürituse detail tasude, osalejate ja eelarve kokkuvõttega / muutmine (k.a `signupOpen`) |
| `POST /api/admin/events/{id}/close`, `/cancel` | admin | sulge, või tühista ja tagasta tasud |
| `PUT /api/admin/events/{id}/fees` | admin | `{fees:[{levelId, amountCents}]}` |
| `POST /api/admin/events/{id}/participants` | admin | `{memberIds:[...]}` → võtab tasud |
| `DELETE /api/admin/events/{id}/participants/{memberId}` | admin | eemalda, tagastab tasu |
| `GET/POST/PUT/DELETE /api/admin/events/{id}/budget-lines` | admin | eelarveread |
| `GET /api/admin/assistant/status` | admin | `{enabled}` |
| `POST /api/admin/events/{id}/assistant` | admin | `{kind: BUDGET\|QUANTITIES\|RECIPE\|QUESTION, question}` → `{answer, suggestedLines}` |
| `GET /api/admin/dashboard` | admin | ülevaate näitajad |
| `GET /api/admin/reports/sales?from=&to=` | admin | kogused ja tulu toote ja kuu kaupa |

## Vaated

Liige: PIN-i klahvistik · Ostmine (lemmikud ees, siis ülejäänud) · Ostuvaate kohandamine ·
Tellimus · Ost tehtud (tagasivõtmisega) · Üritused (eelseisvad, registreerumine) · Minu konto
(saldo, ajalugu, maksest teatamine). Telefoni tabiriba: Osta · Üritused · Konto · Logi välja.
Admin: Admini sisselogimine (e-post + parool) · Ülevaade (madal/negatiivne laoseis, ootel
maksed, suurimad võlglased) · Tooted ja ladu · Tarne sisestamine · Inventuur · Kinnitamist
ootavad maksed · Liikmed (k.a e-post, tasust vabastus) · Liikmemaksud (summa taseme kaupa,
kuised tasud, tühistamine) · Arved (vali liikmed, eelvaade, saada) · Liikmetasemed (liikmete
vaates) · Üritused (nimekiri, detail osalejate, tasude ja eelarvega, AI abilise paneel).

## Verstapostid

1. **Alus** — andmebaasi skriptid (kogu skeem), vead, turvalisus + sisselogimine, esimene
   admin, Vue router ja paigutus, API kliendi seaded.
2. **Tooted ja ladu** — admini toodete CRUD, tarned, laopearaamat, inventuur, mahakandmised.
3. **Ostmine** — ostuvaade isiklike lemmikutega, ostuteenus (saldo + ladu ühes transaktsioonis), tagasivõtmine.
4. **Konto ja maksed** — liikme ajalugu, maksest teatamine, admini kinnitamine/tagasilükkamine, parandused.
5. **Admini ülevaade** — ülevaateleht, liikmete haldus, lihtsad aruanded (müük toote/kuu kaupa).
6. **Tasud ja arved** — liikmemaks ja kuine tasutöö, tühistamised, e-kirjade saatmine, arvete vaade.
7. **Üritused** — liikmetasemed, üritused tasemepõhiste tasudega, osalejad, eelarve.
8. **AI abiline** (valikuline) — ürituse eelarve/retseptide abiline lüliti taga.
9. **Väljalase** — frontend jar-faili, teenusena käivitamine, andmebaasi varukoopiad, SMTP ja AI võtmed.

## Lahtised küsimused

Praegu pole.
