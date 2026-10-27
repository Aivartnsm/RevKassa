# RevKassa — backendi taskid

Tee ülevalt alla; iga task on üks ülevaadatav muudatus koos testidega. Reeglid ja API kuju
tulevad failist [PLAN.md](PLAN.md), tabelid failist [DB-MODEL.md](DB-MODEL.md), konventsioonid
failidest [backend/CLAUDE.md](../backend/CLAUDE.md) ja [backendi struktuur](backend/projekti-struktuur.md).
Suurus: S ≈ tunnid, M ≈ päev, L ≈ paar päeva. Tasknumbrid on id-d, mitte järjekord.
Märgi linnuke, kui "Valmis, kui" kehtib ja `./gradlew test` (kaustas `backend/`) läbib.

Detailne task (taskifail kaustas `docs/tasks/backend/`) tehakse skilliga `skill-loo-backend-task`,
implementatsiooniplaan skilliga `skill-loo-backed-taski-implementatsiooni-plaan`.

Ärireegli rikkumine (nt "tagasivõtmise aeg on möödas", "juba kinnitatud") visatakse
`ForbiddenException`-ina koos `Error` enum'i väärtusega; puuduv kirje on
`PrimaryKeyNotFoundException`; vananenud `version` on 409.

## Verstapost 1 — Alus

- [ ] **B01 · Andmebaasi skriptid ja sõltuvused** (M)
  `docs/database/1_drop.sql`, `2_create.sql` (kõik 16 tabelit, piirangud, FK-d, indeksid täpselt
  nagu DB-MODEL.md-s, k.a 5 liikmetaset) ja `3_import.sql` (näidisliikmed, -tooted, -tarned).
  `build.gradle`-sse MapStruct (+ `lombok-mapstruct-binding`) ja springdoc-openapi (Swagger UI).
  Valmis, kui: skriptid jooksevad tühjale andmebaasile vigadeta, rakendus käivitub
  `ddl-auto=validate`-ga, Swagger UI avaneb ja test kontrollib, et kõik tabelid on olemas.

- [ ] **B02 · Infrastruktuur ja vead** (S) — pärast B01
  `infrastructure/`: `ApiError { message, errorCode }`, `Error` enum (eestikeelsed sõnumid +
  koodid), `PrimaryKeyNotFoundException`, `DataNotFoundException`, `ForbiddenException`,
  `RestExceptionHandler` (`@RestControllerAdvice`, valideerimisvead 400 koos väljadega,
  optimistlik lukk 409). `Clock` bean (testid kontrollivad aega), JSON seaded (ajatemplid
  ISO-8601, raha täisarvuliste sentidena).
  Valmis, kui: `@WebMvcTest` tõestab iga erindi staatuse ja `ApiError` keha.

- [ ] **B03 · Liikme entiteet ja PIN-i räsi** (M) — pärast B01
  `Member` entiteet + repository (`persistence/member/`). `PinService`
  (`infrastructure/security/`): HMAC-SHA256 võtmega `revkassa.pin-secret` (keskkonnamuutuja,
  ilma selleta rakendus ei käivitu), `generateUniquePin()` (6 numbrit, kokkupõrkel uus katse),
  `findByPin()`. PIN-e ei logita kunagi.
  Valmis, kui: testid katavad räsi, unikaalsuse uue katse ja otsingu.

- [ ] **B04 · Turvalisus ja sisselogimine** (L) — pärast B02, B03
  Spring Security sessiooniküpsise + CSRF-iga (küpsise token SPA jaoks, `XSRF-TOKEN`).
  `POST /api/auth/pin` → liikme sessioon, aegumine **1 min**;
  `POST /api/auth/admin` (e-post + bcrypt parool) → admini sessioon, aegumine **30 min**;
  `POST /api/auth/logout`. `/api/admin/**` nõuab admini sisselogimise sessiooni — admini
  PIN-sessioon on ikkagi ainult liikme sessioon. `GET /api/me` (nimi, roll, saldo).
  Valmis, kui: MockMvc testid katavad mõlemad sisselogimised, valed andmed, sessiooni
  aegumised, PIN-sessiooni keelu `/api/admin/**` all ja mitteaktiivse liikme keelu.

- [ ] **B05 · PIN-i jõurünnaku lukustus** (S) — pärast B04
  Kliendi IP kaupa: pärast 5 valet PIN-i lukusta 1 minutiks; pärast seda lukustab iga järgmine
  vale PIN uuesti, tasemed 5 min → 10 min → 30 min → 1 h → 1 päev (edasi 1 päev). Õnnestumisel
  nulli loendur ja tase. Lukustuse ajal ei kontrollita PIN-i üldse. Mälus hoidmine sobib (üks
  instants). Logi ebaõnnestumised ja lukustused ilma PIN-ita. Lukustatud päring → 429, `ApiError`
  oma `errorCode`-iga ja `Retry-After` päis (sekundid), mille järgi UI näitab loendurit.
  Valmis, kui: fikseeritud `Clock`-iga testid läbivad esimese lukustuse 5. vea järel, iga
  taseme pikkuse, 1 päeva lae, lukustuse ajal tehtud katse (ei pikenda ega kontrolli PIN-i) ja
  nullimise õnnestumisel.

- [ ] **B06 · Esimese admini loomine** (S) — pärast B03
  Käivitumisel, kui ühtegi adminit pole, loo üks seadetest `revkassa.bootstrap.admin-email`,
  `-password`, `-name` (keskkonnamuutujad) ja logi selle genereeritud PIN üks kord.
  SQL skriptides paroole pole.
  Valmis, kui: test näitab, et see jookseb üks kord ja jäetakse vahele, kui admin on olemas.

## Verstapost 2 — Tooted ja ladu

- [ ] **B07 · Tooted** (M) — pärast B04
  `Product` entiteet. `GET /api/products` (ainult aktiivsed, `sort_order` järgi, hinna ja laoseisuga).
  Admin: `GET/POST/PUT /api/admin/products` (nimi, stiil, maht, hind, madala laoseisu piir,
  järjekord, aktiivne). Hind > 0; uuendamisel optimistlik lukk (vananenud versioon → 409).
  Valmis, kui: CRUD, valideerimine ning liikme vs admini ligipääs on testitud.

- [ ] **B08 · Laopearaamat ja tarned** (M) — pärast B07
  `StockMovement` entiteet; `StockService.move(product, type, delta, …)` kirjutab liikumise ja
  uuendab `product.stock_qty` ühes transaktsioonis — ainus viis laoseisu muuta.
  `POST /api/admin/stock/deliveries` (read: toode, kogus > 0, valikuline ühiku hind).
  Valmis, kui: invariandi test `stock_qty = sum(quantity_delta)` läbib pärast tarneid.

- [ ] **B09 · Inventuur ja mahakandmised** (S) — pärast B08
  `POST /api/admin/stock/stocktake` (loetud kogus toote kohta → `STOCKTAKE` liikumine vahe
  kohta, võrdsuse korral mitte midagi). `POST /api/admin/stock/write-offs` (kogus + märkus).
  Valmis, kui: vahed ja tühi juhtum on testitud.

## Verstapost 3 — Ostmine

- [ ] **B10 · Rahapearaamat** (S) — pärast B03
  `AccountEntry` entiteet; `AccountService.post(member, type, amount, …)` kirjutab rea ja
  uuendab `member.balance_cents` ühes transaktsioonis — ainus viis saldot muuta.
  Valmis, kui: invariandi test `balance_cents = sum(amount_cents)` läbib.

- [ ] **B11 · Ostud** (M) — pärast B08, B10
  `POST /api/purchases` `{lines:[{productId, quantity}]}`: üks transaktsioon kirjutab
  `purchase`, `purchase_line` (hind kopeeritud), ühe `PURCHASE` rea summaga −total ja ühe
  `SALE` liikumise rea kohta. Mitteaktiivne toode → `ForbiddenException`. Saldo limiiti pole;
  laoseis võib minna negatiivseks.
  Valmis, kui: testid kontrollivad iga kirjutatud rida, mõlemat invarianti ning et sama
  toote/liikme samaaegsed ostud ei kaota uuendusi.

- [ ] **B12 · Tagasivõtmine** (S) — pärast B11
  `POST /api/purchases/{id}/undo`: liige oma ostu 10 minuti jooksul, admin igal ajal.
  Märgib `REVERSED`, kirjutab `PURCHASE_REVERSAL` ja `SALE_REVERSAL` read. Teist korda →
  `ForbiddenException`.
  Valmis, kui: 10 minuti piir (fikseeritud `Clock`), omanikukontroll ja topelt-tagasivõtmine on testitud.

- [ ] **B13 · Lemmikud** (S) — pärast B07
  `MemberFavourite` entiteet. `GET /api/me/favourites` → järjestatud toote id-d;
  `PUT /api/me/favourites` `{productIds}` asendab nimekirja (positsioonid 0..n−1, ilma
  korduseta, tundmatu id → 400).
  Valmis, kui: asendamine, ümberjärjestamine ja tühi nimekiri on testitud.

## Verstapost 4 — Konto ja maksed

- [ ] **B14 · Saldo ajalugu** (S) — pärast B11
  `GET /api/me/entries?page=` uuemad ees, loetava kirjeldusega (nt "2 × Saku
  Originaal", "Makse · pangaülekanne").
  Valmis, kui: lehekülgede kaupa laadimine ja kirjeldused on testitud.

- [ ] **B15 · Maksest teatamine** (S) — pärast B10
  `POST /api/payments` (summa > 0, `CARD`/`BANK_TRANSFER`, valikuline viide) → `PENDING`,
  saldo ei muutu. `GET /api/me/payments?status=PENDING` "ootel" nimekirja jaoks.
  Valmis, kui: saldo jääb puutumata kuni kinnitamiseni.

- [ ] **B16 · Maksete kinnitamine / tagasilükkamine** (S) — pärast B15
  `GET /api/admin/payments?status=PENDING`; `POST /api/admin/payments/{id}/confirm` kirjutab
  ühe `PAYMENT` rea; `/reject` valikulise märkusega. Teist korda otsustamine → `ForbiddenException`.
  `POST /api/admin/payments` `{memberId, amountCents > 0, method, reference?}`: admin sisestab
  makse, mis salvestatakse kohe `CONFIRMED` (`decided_by` = admin) koos ühe `PAYMENT` reaga.
  Valmis, kui: testid tõestavad täpselt üht rida kinnitatud makse kohta, ka admini sisestatud makse puhul.

- [ ] **B17 · Liikmete haldus** (M) — pärast B04, B10
  `GET/POST/PUT /api/admin/members` (nimi, e-post, roll, aktiivne, tasust vabastus; adminil
  e-post + parool kohustuslik, liikmel e-post valikuline).
  Loomine ja `POST /api/admin/members/{id}/pin` tagastavad uue PIN-i vastuses **üks kord**.
  `POST /api/admin/members/{id}/adjustments` (nullist erinev summa, kohustuslik märkus).
  `GET /api/admin/members/{id}/purchases?page=` (pärast B12): liikme ostud uuemad ees — aeg,
  read ("2 × Saku Originaal"), summa, staatus; admin võtab ostu tagasi olemasoleva
  `POST /api/purchases/{id}/undo` kaudu.
  Valmis, kui: admini reeglid, PIN-i lähtestamine (vana PIN kehtetu), parandused ja ostude
  nimekiri (k.a tagasivõetud ost) on testitud.

- [ ] **B34 · Liige muudab oma PIN-i** (S) — pärast B05
  `PUT /api/me/pin` `{currentPin, newPin}`: praegune PIN peab klappima (vale → `ForbiddenException`
  ja loeb B05 lukustuse loendurisse), uus täpselt 6 numbrit (400), eelmisest erinev ja unikaalne
  (võetud → `ForbiddenException`, teade ei ütle, kellele see kuulub). Uuendab `pin_hmac` ja
  `pin_changed_at`; PIN-e ei logita.
  Valmis, kui: õnnestumine (vana PIN kehtetu, uus töötab), vale praegune PIN, vale formaat, sama
  PIN ja võetud PIN on testitud.

## Verstapost 5 — Admini ülevaade

- [ ] **B18 · Ülevaade** (S) — pärast B09, B16
  `GET /api/admin/dashboard`: avatud võlgade summa ja arv, ootel maksete arv ja summa,
  madala ja negatiivse laoseisuga tooted, suurimad võlad (top 5).
  Valmis, kui: näitajad klapivad testandmetega.

- [ ] **B19 · Müügiaruanne** (S) — pärast B11
  `GET /api/admin/reports/sales?from=&to=`: kogused ja tulu toote ja kuu kaupa,
  tagasivõetud ostud välja arvatud.

## Verstapost 6 — Tasud ja arved

- [ ] **B22 · Liikmemaks taseme kaupa** (S) — pärast B04, B27
  `MembershipFee` entiteet (tase, kuu, summa). `GET /api/admin/fees` → praegune tasu taseme
  kaupa + ajalugu. `POST /api/admin/fees` `{validFrom: "YYYY-MM", fees:[{levelId, amountCents ≥ 0}]}`:
  kuu peab olema tulevikus või jooksev kuu, kui selle eest pole veel tasu võetud; tasemel on
  selle kuu rida juba olemas → `ForbiddenException`. `feeFor(level, month)` = hiliseim
  `valid_from` ≤ kuu, muidu 0.
  Valmis, kui: ajaloo otsing taseme kaupa, tasudeta tase, kuu reeglid ja 0 € on testitud.

- [ ] **B23 · Kuine tasutöö** (M) — pärast B10, B17, B22 (tasemete tasud)
  `MembershipChargeService.chargeMonth(month)`: iga aktiivse, tasust vabastamata liikme kohta,
  kes on loodud enne kuud ja kellelt pole veel tasu võetud, võtab `feeFor(member.level, month)`
  (0 → jätab vahele), kirjutab `membership_charge` (tasemega) + ühe `MEMBERSHIP_FEE` rea
  `AccountService.post` kaudu (`created_by` null), üks transaktsioon liikme kohta.
  `@Scheduled` iga päev kell 06:00 (Europe/Tallinn) ja üks kord käivitumisel jooksva kuu jaoks.
  Unikaalne `(member_id, month)` kaitseb paralleelse jooksu eest. Ajaloo kirjeldus:
  "Liikmemaks · oktoober 2026".
  Valmis, kui: fikseeritud `Clock`-iga testid katavad 1. kuupäeva, hilisema järeletegemise,
  kaks jooksu (topelttasu pole), eri tasemetelt eri summad, vabastatud/mitteaktiivsed/uued
  liikmed vahele jäetud, tasu 0 ei kirjuta midagi, invariandid kehtivad.

- [ ] **B24 · Tasude nimekiri ja tühistamine** (S) — pärast B23
  `GET /api/admin/fees/charges?month=YYYY-MM` (liige, summa, staatus, kokkuvõtted).
  `POST /api/admin/fees/charges/{id}/waive` `{note}` (kohustuslik) → `WAIVED` +
  `MEMBERSHIP_FEE_WAIVER` rida summaga +summa. Teist korda → `ForbiddenException`.
  Valmis, kui: tühistamine, topelt-tühistamine ja invariant on testitud.

- [ ] **B25 · E-kirjade saatmine** (S) — pärast B02
  Lisa `spring-boot-starter-mail`. `Mailer` liides (`infrastructure/mail/`): SMTP
  implementatsioon, kui `spring.mail.host` on seatud, muidu logiv (arendus).
  `@ConfigurationProperties` `revkassa.invoice.*` (org-name, iban, from, reply-to,
  due-days = 14), valideeritakse käivitumisel, kui SMTP on sees.
  Valmis, kui: testid näitavad, et ilma hostita kasutatakse logivat saatjat ja seaded seotakse.

- [ ] **B26 · Arved** (M) — pärast B14, B17, B25
  `Invoice` entiteet. `GET /api/admin/invoices/candidates`: liikmed saldoga < 0, e-post,
  viimase arve kuupäev, `selectable` (e-post olemas). `GET /api/admin/invoices/preview?memberId=`.
  `POST /api/admin/invoices` `{memberIds}`: liikme kohta oma transaktsioon: järgmine number
  `YYYY-NNNN`, summa = −saldo, maksetähtaeg, lihttekstiga **eestikeelne** sisu (võlg, read
  eelmisest arvest või viimased 60 päeva, IBAN, selgitus "Arve <number>"; `et-EE` raha ja
  kuupäevad, eestikeelsed kuunimed), saada, salvesta `SENT` või `FAILED` + viga;
  tagastab tulemuse liikme kaupa. Saldo ≥ 0 või e-posti puudumine → vahele jäetud koos põhjusega.
  `GET /api/admin/invoices?memberId=`, `POST /api/admin/invoices/{id}/resend` (ainult
  `FAILED`, sama number). Saldod ei muutu kunagi.
  Valmis, kui: võlts-`Mailer`-iga testid katavad numeratsiooni üle aastavahetuse, ebaõnnestunud
  aadressi, mis teisi ei peata, vahelejätmise põhjused, uuesti saatmise ja kirja sisu.

## Verstapost 7 — Üritused

- [ ] **B27 · Liikmetasemed** (S) — pärast B17
  `MembershipLevel` entiteet. `GET/POST/PUT /api/admin/levels` (nimi unikaalne tõstutundetult,
  järjekord, aktiivne). Kasutusest mahavõtmine jätab liikmed alles; kustutamine ainult kasutamata
  tasemel (muidu `ForbiddenException`). `member.level_id` liikmete halduses; uus liige saab
  esimese aktiivse taseme.
  Valmis, kui: unikaalne nimi, mahavõtmine vs kustutamine ja vaiketase on testitud.

- [ ] **B28 · Üritused** (M) — pärast B27
  `Event` + `EventLevelFee` entiteedid. `GET/POST /api/admin/events`, `GET/PUT
  /api/admin/events/{id}` (detail tasudega taseme kaupa, osalejate arv, eelarve kokkuvõtted),
  `PUT /api/admin/events/{id}/fees`. `CLOSED`/`CANCELLED` üritused on ainult loetavad
  (`ForbiddenException`).
  Valmis, kui: CRUD, tasutabel, staatusereeglid ja optimistlik lukk on testitud.

- [ ] **B29 · Osalejad ja ürituse tasud** (M) — pärast B10, B28
  `POST /api/admin/events/{id}/participants {memberIds}`: liikme kohta kopeeri tase + tasu,
  kirjuta `EVENT_FEE` rida `AccountService.post` kaudu (0 korral mitte); juba aktiivne → vahele
  jäetud. `DELETE …/participants/{memberId}` → `REMOVED` + `EVENT_FEE_REVERSAL`. `POST …/cancel`
  tagastab kõik; `POST …/close` lukustab. Ajaloo kirjeldus "Ürituse tasu · <üritus>".
  Valmis, kui: lisamine/eemaldamine/uuesti lisamine, tasu 0, tühistamine, osaleja invariant ja
  saldo invariant on testitud.

- [ ] **B30 · Ürituse eelarve** (S) — pärast B28
  `EventBudgetLine` entiteet, CRUD `/api/admin/events/{id}/budget-lines` all; kokkuvõtted
  kategooria kaupa (planeeritud, tegelik), tasudest laekuv ja tulemus ürituse detailis.
  Valmis, kui: puuduvate tegelike summadega kokkuvõtted ja ainult loetavad olekud on testitud.

- [ ] **B33 · Liikme registreerumine** (S) — pärast B29
  `event.signup_open` (admin muudab). `GET /api/events`: `PLANNED` üritused avatud
  registreerimisega ja üritused, kus liige osaleb; igaühel oma tasu (taseme järgi), osalejate
  arv, `signedUp`. `POST /api/events/{id}/signup`: ainult `PLANNED` + avatud (muidu
  `ForbiddenException`), mitte juba aktiivne (`ForbiddenException`); sama service nagu admini
  lisamisel, `added_by` = liige. Liikmel tühistamise endpointi pole.
  Valmis, kui: avatud/suletud/tühistatud, topeltregistreerumine, tasu taseme järgi ja see, et
  liige ei saa end eemaldada (route'i pole; admini DELETE töötab), on testitud.

## Verstapost 8 — AI abiline (valikuline)

- [ ] **B31 · Abilise klient** (M) — pärast B30
  Lisa `com.anthropic:anthropic-java`. `EventAssistant` liides (`infrastructure/ai/`);
  `AnthropicEventAssistant`, kui `ANTHROPIC_API_KEY` on seatud (mudel `revkassa.ai.model`,
  vaikimisi `claude-sonnet-5-5`, max tokenid ja aegumine konfiguratsioonist), muidu
  `DisabledEventAssistant`. `GET /api/admin/assistant/status`.
  Valmis, kui: võtmega/võtmeta valitakse õige bean; võtmeta → staatus `{enabled: false}`.

- [ ] **B32 · Ürituse abilise endpoint** (M) — pärast B31
  `POST /api/admin/events/{id}/assistant {kind, question}`. Eestikeelne prompt ehitatakse
  ainult ürituse nimest, kuupäevast, osalejatest taseme kaupa, eelarveridadest ja küsimusest —
  **ilma liikmete nimede, e-posti ja saldodeta**. Küsib JSON-i `{answer, suggestedLines:[{category,
  description, plannedCents}]}`; vigane JSON → ainult vastuse tekst. 20 päringut admini kohta
  päevas (mälus), logi kes/millal/tokenid, mitte kunagi prompti. Ettepaneku vastuvõtmine
  kasutab tavalist eelarverea POST-i `source: AI`-ga.
  Valmis, kui: võlts-abilisega testid kontrollivad, et prompt ei sisalda isikuandmeid, JSON-i
  varulahendust, päevalimiiti ja väljalülitatud olekut.

## Verstapost 9 — Väljalase

- [ ] **B20 · Üks jar** (S) — kui frontend on olemas
  Gradle task ehitab `frontend/` ja kopeerib `dist/` jar-i staatilistesse ressurssidesse;
  tundmatud mitte-`/api` teed suunatakse `index.html`-ile.
  Valmis, kui: `./gradlew bootJar` + `java -jar` serveerib UI-d ja API-t.

- [ ] **B21 · Käivitamine ja varukoopiad** (S)
  Tootmise profiil (saladused keskkonnast, k.a SMTP parool ja `ANTHROPIC_API_KEY`, turvalised
  küpsised), systemd unit, öine `pg_dump` taastamise kontrolliga. Tootmise andmebaas luuakse
  `2_create.sql`-iga (ilma `3_import.sql`-ita), hilisemad muudatused `docs/database/muudatused/`
  skriptidega. Dokumenteeri kausta `docs/`.
