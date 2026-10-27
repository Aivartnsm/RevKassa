# RevKassa — frontendi taskid

Vue 3 + Vite kaustas `frontend/`. Vaated järgivad "RevKassa Mockups" lõuendit; API kuju tuleb
failist [PLAN.md](PLAN.md), backendi töö failist [BACKEND-TASKS.md](BACKEND-TASKS.md) (`B..` allpool).
Suurus: S ≈ tunnid, M ≈ päev, L ≈ paar päeva.

Detailne task (taskifail kaustas `docs/tasks/frontend/`) tehakse skilliga `skill-loo-frontend-task`,
juhendamine skilliga `skill-rain-ai-frontend`.

Põhireeglid (vt [frontendi struktuur](frontend/projekti-struktuur.md) ja
[Vue komponent](frontend/vue-komponendi-struktuur.md)):
- Options API ühtse järjekorraga; API kutsed ainult `src/api-services/` kaudu (axios),
  `.then()/.catch()/.finally()` muster, esmased andmed `beforeMount`-is.
- Vaated on `src/views/` all, admini omad eesliitega `Admin`; korduvad osad
  `components/common|forms|modals|tables/`; menüüd `navigation/` all.
- Jagatud olek props/emits ja `sessionStorage` abil (`utils/session.js`, `utils/basket.js`).
  Pinia't, composable'eid ega UI raamistikku pole.
- Liikme vaated on **mobile-first** (põhi-CSS ~390px), tahvli paigutus alates `min-width: 1024px`
  11" baaritahvlile (1920×1200 → ~1280×800 CSS px, landscape; vt "Member app — tablet"
  mockup). Admini vaated on desktop-first ja peavad telefonis toimima.
- Raha tuleb täisarvuliste sentidena; vorminda ainult `formatEuro()` abil (`27,40 €`).
- **Kogu UI tekst on eesti keeles**, kirjutatud otse komponentidesse (`<html lang="et">`, i18n
  teeki pole). Kuupäevad ühe `formatDate()` abil (`16.10.2026`, `16. okt`). Mockupid näitavad sõnastust.
- Veateade tuleb kujul `{ message, errorCode }`; kasutajale näidatakse `message`.
- Valmis, kui: `npm run build` (ja `utils/` muutmisel `npm test`) läbib ning vaade on läbi
  proovitud 390px laiusel, liikme vaated ka 1280×800 juures, admini vaated laua laiusel,
  töötava backendi vastu.

## Verstapost 1 — Alus

- [ ] **F01 · Puhas alus ja välimus** (S)
  Eemalda Vite'i demo (`HelloWorld.vue`, demo-pildid). Loo kaustad `views/`,
  `components/common|forms|modals|tables/`, `navigation/`, `api-services/`, `utils/`, `router/`.
  Globaalne CSS: värvid mockupitest (taust `#121A17`, pinnad `#1C2622`/`#26332E`, aktsent
  `#F2B33D`, ettemaksu/võla värvid), Barlow + Barlow Condensed, puutealad vähemalt 44px,
  fookuse stiilid. Lisa Vitest (`npm test`) `utils/` funktsioonide jaoks.
  Valmis, kui: tühi rakendus ehitub ja näitab põhistiile 390px juures.

- [ ] **F02 · axios ja abifunktsioonid** (S)
  Lisa `axios` ja `vue-router`. `main.js`: axios'e response interceptor — 401 → sessioon
  aegunud, suuna `NavigationService` kaudu sisselogimisse (CSRF päis `X-XSRF-TOKEN` tuleb
  axios'elt automaatselt `XSRF-TOKEN` küpsisest). `utils/format.js`: `formatEuro(cents)`
  (`et-EE`, `€`, negatiivsel miinusmärk) ja `formatDate()`.
  Valmis, kui: ühiktestid katavad vormindamise.

- [ ] **F03 · Router, sessioon ja navigatsioon** (M) — vajab B04
  Route'id: `/` PIN, `/buy`, `/buy/customise`, `/order`, `/done`, `/account`,
  `/admin/login`, `/admin/...`, `meta: { member: true }` / `meta: { admin: true }`.
  `utils/session.js` hoiab `GET /api/me` vastust `sessionStorage`'is. Guard'id: liikme route'id
  vajavad liikme sessiooni, `/admin/**` admini sessiooni. `navigation/MemberNavigation.vue`
  (päis nime + saldoga, alumine tabiriba Osta · Konto · Logi välja);
  `navigation/AdminNavigation.vue` (laual ülemenüü, telefonis alumised tabid, "Logi välja").
  `App.vue` valib navigatsiooni route'i `meta` järgi.
  Valmis, kui: guard'id suunavad õigesti ja mõlemad navigatsioonid töötavad mõlemal laiusel.

- [ ] **F04 · PIN-i klahvistik** (M) — vajab B04, B05
  `PinView.vue`: kuus täppi, 3×4 klahvistik (C, 0, ⌫), automaatne saatmine 6. numbri järel,
  vale PIN-i teade, lukustuse teade loenduriga (`errorCode` järgi), töötavad ka klaviatuuri numbrid.
  Õnnestumine → `/buy`.
  Valmis, kui: backendi vastu testitud, k.a lukustus.

- [ ] **F05 · Tegevusetuse väljalogimine** (S) — pärast F03
  `utils/idleLogout.js` (`start(ms, onExpire)`, `stop()`), käivitatakse `App.vue`-st: lähtestub
  puudutuse/klahvi/kerimise peale; aegumisel logib välja, tühjendab `sessionStorage`'i
  (sessioon + ostukorv) ja naaseb PIN-i vaatesse (liige, **1 min**) või admini sisselogimisse
  (admin, **30 min**). Serveri 401 teeb sama.
  Valmis, kui: ühiktest võltstaimeritega; käsitsi kontroll, et ostukorv tühjeneb.

- [ ] **F06 · Admini sisselogimine** (S) — vajab B04
  `AdminLoginView.vue`: e-posti + parooli vorm, veateade, märkus, et PIN-ist ei piisa.
  Õnnestumine → `/admin`.

## Verstapost 2 — Admin: tooted ja ladu

- [ ] **F07 · Tooted** (M) — vajab B07
  Nimekiri hinna, laoseisu, staatusesildi (OK / Vähe / Loe üle / Peidetud) ja näita/peida
  lülitiga; loomise/muutmise vorm (nimi, stiil, maht, hind eurodes, madala laoseisu piir,
  järjekord). Laual tabel, telefonis nimekiri → detail.

- [ ] **F08 · Tarne sisestamine** (S) — vajab B08
  Laual üks rida toote kohta koguse ja valikulise ühiku hinnaga. Telefonis toote detail
  −/+ ja +6 / +20 / +24 kiirnuppudega. Näitab tulemuseks tulevat laoseisu.

- [ ] **F09 · Inventuur ja mahakandmine** (S) — vajab B09
  Loetud koguse väljad elava vahega; tühjad read jäetakse vahele; mahakandmine märkusega.

## Verstapost 3 — Ostmine (liige, mobile-first)

- [ ] **F10 · Ostuvaade** (M) — vajab B07, B13
  Kõigepealt "Sinu lemmikud" ruudustik (liikme järjekorras), siis "Kõik muu"; ruudule
  vajutus lisab, koguse märk, "Viimased" vihje. Ostukorv `utils/basket.js` abil
  `sessionStorage`'is (tühjeneb väljalogimisel). Kleepuv alumine riba
  "Vaata tellimust · n tk · x €".
  Tahvel (≥1024px): 4-veeruline ruudustik ja paremal alati nähtav tellimuspaneel
  (read −/+, summa, saldo pärast, Kinnita nupp) — F11 tellimuse samm jääb seal vahele.
  Valmis, kui: töötab ühe käega 390px juures (2 veergu) ja 1280×800 juures (4 veergu + paneel).

- [ ] **F11 · Tellimus, kinnitus, valmis, tagasivõtmine** (M) — vajab B11, B12
  Tellimuse vaade −/+ ridadega, summa, saldo pärast, külaliste vihje; alumisel ribal
  "Lisa x € minu saldole". Valmis-vaade: summa, uus saldo, Osta veel, Valmis (logib välja),
  Võta tagasi (peidetud 10 min pärast). Topeltvajutus ei saada kaks korda (nupp keelatud päringu ajal).

- [ ] **F12 · Lemmikute kohandamine** (S) — vajab B13
  Tärn sisse/välja, liiguta üles/alla, "Valmis" salvestab `PUT /api/me/favourites` kaudu.

## Verstapost 4 — Konto ja maksed

- [ ] **F13 · Minu konto** (M) — vajab B14, B15
  Saldokaart ("Sinu võlg" / "Sinu ettemaks"), ootel maksed, ajalugu nupuga "Laadi veel".
  Tahvel: vasakul saldo ja maksevorm, paremal ootel maksed ja ajalugu.

- [ ] **F14 · Maksa või maksa ette** (S) — vajab B15
  Makseviisi valik (pangaülekanne / kaart), summa eurodes, valikuline viide; kinnitusteade;
  ilmub "Ootab kinnitust" alla.

- [ ] **F15 · Admin: maksed** (S) — vajab B16
  Ootel maksete nimekiri nuppudega Kinnita / Lükka tagasi (valikuline märkus); arvu märk admini menüüs.

- [ ] **F16 · Admin: liikmed** (M) — vajab B17
  Nimekiri saldo ja staatusega; e-posti ja tasust vabastuse väljad; liikme lisamine ja PIN-i
  lähtestamine näitavad uut PIN-i dialoogis **üks kord**; saldo parandus (summa + kohustuslik
  põhjus); rolli/aktiivsuse muutmine (adminiks tegemisel e-post + parool).

## Verstapost 5 — Admini ülevaade

- [ ] **F17 · Ülevaade** (S) — vajab B18
  Neli näitajat (avatud võlad, kinnitamiseks, vähe laos, loe üle), mis viivad oma vaadetesse;
  nimekirjad "Kontrolli laoseisu" ja "Suurimad võlad".

- [ ] **F18 · Müügiaruanne** (S) — vajab B19
  Kuude vahemiku valik ja tabel kogustest ja tulust toote kaupa.

## Verstapost 6 — Tasud ja arved

- [ ] **F20 · Admin: liikmemaksud** (S) — vajab B22, B24
  Praegune tasu taseme kaupa ja ajalugu; "Muuda tasusid" (summa eurodes taseme kohta, alates
  kuust, vaikimisi järgmine kuu).
  Kuu valik → tasude nimekiri (liige, summa, Võetud/Tühistatud) kokkuvõtetega; Tühista
  kohustusliku märkusega. Menüüpunkt "Tasud".

- [ ] **F21 · Admin: arvete saatmine** (M) — vajab B26
  Võlglased: nimi, võlg, e-post, viimane arve. Linnukesed, eelvalitud = e-post olemas ja
  7 päeva jooksul arvet pole saadetud; e-postita read keelatud lingiga "lisa e-post" liikme
  juurde. "Eelvaade" näitab ühe liikme kirja. "Saada n arvet" (kinnitusdialoog, nupp keelatud
  saatmise ajal) → tulemus liikme kaupa (Saadetud / Ebaõnnestus + põhjus, Saada uuesti).
  Arvete ajalugu liikme detailis. Menüüpunkt "Arved".
  Valmis, kui: töötab laua ja telefoni laiusel; testitud logiva saatjaga.

## Verstapost 7 — Üritused

- [ ] **F22 · Liikmetasemed** (S) — vajab B27
  Liikmete vaates paneel "Liikmetasemed" (ümbernimetamine, järjestamine, lisamine,
  mahavõtmine; liikmete arv taseme kohta); taseme valik liikme lisamisel/muutmisel ja veerg "Tase".

- [ ] **F23 · Ürituste nimekiri ja redaktor** (M) — vajab B28
  Menüüpunkt "Üritused": kaardid (nimi, kuupäev, staatus, osalejad, tasudest laekuv, eelarve
  tulemus), vorm "Uus üritus" (nimi, kuupäev ja kellaaeg, koht, kirjeldus). Detaili päises
  Sulge/Tühista (kinnitusdialoog; tühistamine selgitab, et tasud tagastatakse).

- [ ] **F24 · Tasud ja osalejad** (M) — vajab B29
  Tasude tabel taseme kaupa (€ väljad, salvesta; hoiatus, et varasemad osalejad jäävad oma tasuga).
  Lüliti "Registreerimine avatud". Osalejate nimekiri (nimi, tase, tasu, "ise" / "lisas admin",
  Eemalda) ja "Lisa osalejaid": otsitav liikmete nimekiri linnukestega, mis näitab iga liikme
  tasu enne lisamist. Kokkuvõtted: arv, tasudest laekuv.

- [ ] **F25 · Ürituse eelarve** (M) — vajab B30
  Read kategooriate kaupa (Joogid, Snäkid, Toit, Meelelahutus, Ruum, Kaunistus, Muu)
  planeeritud/tegeliku summa väljadega; lisa rida; kokkuvõte: kulud planeeritud/tegelik,
  tasudest laekuv, tulemus (punane, kui negatiivne). "AI" märk vastu võetud ettepanekutel.

- [ ] **F27 · Liige: üritused** (M) — vajab B33
  Liikme tab "Üritused" (telefoni tabiriba Osta · Üritused · Konto · Logi välja; tahvlil päises):
  eelseisvad üritused kuupäeva, koha, kirjelduse ja oma tasuga; "Registreeru" → kinnitusleht
  "Tasu X lisatakse sinu saldole" → "Oled kirjas". Registreeritud üritustel "Oled kirjas" ja
  "Loobumiseks võta ühendust adminiga" (tühistamise nuppu pole).

- [ ] **F28 · Üritused telefonis (admin)** (S) — koos F23–F25
  Admini ürituste vaated töötavad 390px juures: nimekiri → detail segmentidega Osalejad ·
  Tasud · Eelarve, registreerimise lüliti, osalejate lisamine/eemaldamine, eelarveread
  tegelike summadega, abilise kiirnupud. Admini mobiilne tabiriba: Ülevaade · Maksed · Ladu ·
  Liikmed · Tasud · Üritused.

## Verstapost 8 — AI abiline (valikuline)

- [ ] **F26 · Abilise paneel** (M) — vajab B32
  Näidatakse ainult siis, kui `GET /api/admin/assistant/status` ütleb, et sees. Külgpaneel
  eelarve juures: kiirnupud (Eelarve mustand · Kui palju osta · Retsept N inimesele), vaba
  küsimus, vastuse tekst, soovitatud read igaüks nupuga "Lisa eelarvesse". Märkus, et saadetakse
  ainult ürituse andmed ja ettepanekuid tuleb kontrollida. Laadimise ja vea olekud; nupp
  keelatud küsimise ajal.

## Verstapost 9 — Väljalase

- [ ] **F19 · Tootmise build** (S) — koos B20
  Build'i väljund ühendatud Gradle jar-iga; õige base path; kontrolli, et sügavlingid laevad
  jar-is uuesti (`/account`, `/admin/members`).
