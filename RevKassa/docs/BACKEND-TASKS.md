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

- [x] **B01 · Andmebaasi skriptid ja sõltuvused** (M)
  `docs/database/1_reset_database.sql`, `2_create.sql` (kõik tabelid — algselt 16, nüüd 18 koos `trusted_device` ja `password_token`, piirangud, FK-d, indeksid täpselt
  nagu DB-MODEL.md-s, k.a 5 liikmetaset) ja `3_import.sql` (näidisliikmed, -tooted, -tarned).
  `build.gradle`-sse MapStruct (+ `lombok-mapstruct-binding`) ja springdoc-openapi (Swagger UI).
  Valmis, kui: skriptid jooksevad tühjale andmebaasile vigadeta, rakendus käivitub
  `ddl-auto=validate`-ga, Swagger UI avaneb ja test kontrollib, et kõik tabelid on olemas.

- [x] **B02 · Infrastruktuur ja vead** (S) — pärast B01
  `infrastructure/`: `ApiError { message, errorCode }` + valikulised väljad, mis tulevad JSON-isse ainult
  väärtuse olemasolul (`@JsonInclude(NON_NULL)`): väljavead (400), `remainingAttempts`, `nextLockoutSeconds` (B05), `Error` enum (ingliskeelsed sõnumid +
  koodid; UI tekst tuleb frontendist koodi järgi), `PrimaryKeyNotFoundException`, `DataNotFoundException`, `ForbiddenException`,
  `RestExceptionHandler` (`@RestControllerAdvice`, valideerimisvead 400 koos väljadega,
  optimistlik lukk 409). `Clock` bean (testid kontrollivad aega), JSON seaded (ajatemplid
  ISO-8601, raha täisarvuliste sentidena).
  Valmis, kui: `@WebMvcTest` tõestab iga erindi staatuse ja `ApiError` keha.

- [x] **B03 · Liikme entiteet ja PIN-i räsi** (M) — pärast B01
  `Member` entiteet + repository (`persistence/member/`; `email` kohustuslik). `PinService`
  (`infrastructure/security/`): HMAC-SHA256 võtmega `revkassa.pin-secret` (keskkonnamuutuja,
  ilma selleta rakendus ei käivitu), `generateUniquePin()` (6 numbrit, kokkupõrkel uus katse),
  `findByPin()`. PIN-e ei logita kunagi.
  Valmis, kui: testid katavad räsi, unikaalsuse uue katse ja otsingu.

- [x] **B04 · Turvalisus ja sisselogimine** (L) — pärast B02, B03, B35
  Spring Security sessiooniküpsise + CSRF-iga (küpsise token SPA jaoks, `XSRF-TOKEN`).
  `POST /api/auth/pin` → liikme sessioon, aegumine **1 min** — **ainult usaldatud seadmest**
  (kehtiv `REVKASSA_DEVICE` küpsis, B35); ilma selleta 403 `DEVICE_NOT_TRUSTED` ja PIN-i ei
  kontrollita. Õnnestumisel uuenda `trusted_device.last_used_at`.
  `POST /api/auth/login` (e-post tõstutundetult — `MemberRepository` otsing `lower(email)` järgi — + bcrypt parool) → sessioon, aegumine **30 min**;
  `ADMIN` rolliga saab ka admini õiguse (`ROLE_ADMIN`). Tundmatu e-post, vale parool ja
  määramata parool (`password_hash` null) → sama 403. Asendab varasema `POST /api/auth/admin`-i.
  `POST /api/auth/logout`. `/api/admin/**` nõuab parooliga sessiooni ja `ADMIN` rolli — admini
  PIN-sessioon on ikkagi ainult liikme sessioon. `GET /api/me` (nimi, roll, saldo, `loginMethod`
  `PIN`/`PASSWORD`).
  Valmis, kui: MockMvc testid katavad mõlemad sisselogimised, PIN-i ilma seadmeküpsiseta /
  tühistatud seadmega, valed andmed, määramata parooli, sessiooni aegumised, PIN-sessiooni keelu
  `/api/admin/**` all, tavaliikme parooliga sessiooni keelu `/api/admin/**` all ja
  mitteaktiivse liikme keelu.

- [x] **B05 · Jõurünnaku lukustus** (S) — pärast B04
  PIN: **seadme kaupa** (`trusted_device.id`). Parool: **kliendi IP kaupa**, eraldi loendur.
  Mõlemal: pärast 5 valet katset lukusta 1 minutiks; pärast seda lukustab iga järgmine
  vale katse uuesti, tasemed 5 min → 10 min → 30 min → 1 h → 1 päev (edasi 1 päev). Õnnestumisel
  nulli loendur ja tase. Lukustuse ajal ei kontrollita PIN-i/parooli üldse. Mälus hoidmine sobib
  (üks instants). Logi ebaõnnestumised ja lukustused ilma PIN-i/paroolita. Vale katse enne
  lukustust → 403 `remainingAttempts`-iga. Lukustatud päring → 429, `ApiError`
  oma `errorCode`-iga ja `nextLockoutSeconds`-iga (järgmise lukustuse pikkus) ning `Retry-After` päis
  (sekundid), mille järgi UI näitab loendurit.
  Valmis, kui: fikseeritud `Clock`-iga testid läbivad esimese lukustuse 5. vea järel, iga
  taseme pikkuse, 1 päeva lae, lukustuse ajal tehtud katse (ei pikenda ega kontrolli) ja
  nullimise õnnestumisel; teise seadme / teise IP katsed ei ole mõjutatud.

- [x] **B06 · Esimese admini loomine** (S) — pärast B03
  Käivitumisel, kui ühtegi adminit pole, loo üks seadetest `revkassa.bootstrap.admin-email`,
  `-password`, `-name` (keskkonnamuutujad) ja logi selle genereeritud PIN üks kord.
  SQL skriptides paroole pole. Esimene admin logib sisse parooliga ja registreerib tahvli (B38).
  Tehtud (`service/AdminBootstrapService`): keskkonnamuutujad `REVKASSA_BOOTSTRAP_ADMIN_EMAIL`,
  `_PASSWORD`, `_NAME` (nimi vaikimisi "Admin"). "Adminit pole" = pole ühtegi **aktiivset** ADMIN-i.
  Seadeteta hoiatus logis; vigased seaded (pole e-post, parool rikub paroolireegleid, e-post kuulub juba liikmele)
  peatavad käivitumise. Tase = esimene aktiivne. Parooli ei logita; PIN logitakse ainult see üks kord.
  Valmis, kui: test näitab, et see jookseb üks kord ja jäetakse vahele, kui admin on olemas.

- [x] **B35 · Usaldatud seadmed: mehhanism** (M) — pärast B03; B04 kasutab
  `TrustedDevice` entiteet + repository (`persistence/trusteddevice/`). `DeviceService`
  (`infrastructure/security/`): `createToken()` = 32 juhuslikku baiti (`SecureRandom`, base64url),
  andmebaasi ainult SHA-256 hex (`hash()`); `findTrusted(token)` tagastab tühistamata seadme või tühja;
  `createDeviceCookie(token)` → `REVKASSA_DEVICE`: `HttpOnly`, `SameSite=Strict`, `Path=/`, `Max-Age`
  400 päeva, `Secure` seadest `revkassa.secure-cookies` (tootmises `true`). `GET /api/auth/device` →
  `{trusted, name}` (sisselogimata, `AuthController` / `AuthService`). Tokenit ei logita ega tagastata kehas.
  Valmis, kui: testid katavad räsi, tokeni kuju, `trusted` õige/vale/tühistatud/puuduva küpsisega ja
  küpsise atribuudid.

- [x] **B25 · E-kirjade saatmine** (S) — pärast B02
  Lisa `spring-boot-starter-mail`. `Mailer` liides (`infrastructure/mail/`): SMTP
  implementatsioon, kui `spring.mail.host` on seatud, muidu logiv (arendus).
  `@ConfigurationProperties` `revkassa.mail.*` (from, reply-to), `revkassa.app-url` (linkide
  alus, arenduses `http://localhost:5173`) ja `revkassa.invoice.*` (org-name, iban,
  due-days = 14), valideeritakse käivitumisel, kui SMTP on sees. (B39 viib org-name ja iban andmebaasi.)
  Tehtud: `Mailer.send(MailMessage(to, subject, body))`; SMTP viga → `MailSendFailedException` (lühike
  põhjus ilma sisu ja aadressita, arve saab selle salvestada). Seaded `RevkassaProperties` (`infrastructure/config/`).
  SMTP ajalõpud 10 s. SMTP + `localhost` app-url → hoiatus logis. Keskkonnamuutujad: `SPRING_MAIL_HOST/_PORT/
  _USERNAME/_PASSWORD`, `REVKASSA_APP_URL`, `REVKASSA_MAIL_FROM`, `REVKASSA_MAIL_REPLY_TO`,
  `REVKASSA_INVOICE_DUE_DAYS` (org-name ja iban viis B39 andmebaasi).
  Valmis, kui: testid näitavad, et ilma hostita kasutatakse logivat saatjat ja seaded seotakse.

- [x] **B36 · Parooli lingid** (M) — pärast B04, B25
  `PasswordToken` entiteet (`persistence/passwordtoken/`). `PasswordLinkService.send(member,
  purpose, createdBy)`: tühistab liikme kehtivad lingid (`used_at = now`), loob tokeni
  (32 baiti, andmebaasi SHA-256), kehtivus `INVITE` 7 päeva / `RESET` 1 tund, saadab
  **eestikeelse** lihttekstiga kirja lingiga `<app-url>/set-password?token=…`.
  `POST /api/auth/password-reset` `{email}`: aktiivsel liikmel saadab `RESET` lingi; vastus
  **alati 204** (ei paljasta, kas e-post on olemas); IP kaupa max 5 päringut tunnis (429).
  `POST /api/auth/set-password` `{token, password}`: parool vastab paroolireeglitele (400); tundmatu, aegunud,
  kasutatud link või mitteaktiivne liige → `ForbiddenException` (üks kood); salvestab bcrypt
  räsi, märgib lingi kasutatuks. Ei logi sisse. Tokenit ega parooli ei logita.
  `POST /api/admin/members/{id}/invite` → uus `INVITE` (B17 kutsub sama teenust liikme loomisel).
  Tehtud: vigane link → 403 `121 PASSWORD_LINK_INVALID`; IP limiit → 429 `122` + `Retry-After`; lisaks
  max 5 kirja tunnis sama aadressi kohta (vastus ikka 204, kirja ei saadeta). Paroolireeglid (PLAN.md):
  `infrastructure/validation/PasswordRules` + `@StrongPassword`, väljaviga nimetab esimese rikutud reegli. Kirja saatmise viga: kutse → 503 `123` ja midagi ei salvestata;
  lähtestamine → ikka 204 (logis ERROR). Token ja räsi `TokenService`-ist (sama mis seadmeküpsisel).
  Kutse teenus: `MemberService.sendInvite` (B17 kasutab `PasswordLinkService.send`-i liikme loomisel).
  Valmis, kui: võlts-`Mailer`-iga testid katavad kirja sisu ja lingi, aegumise (`Clock`),
  ühekordsuse, vana lingi tühistamise uue saatmisel, tundmatu e-posti (204, kirja pole), lühikese
  parooli ja selle, et pärast määramist töötab `POST /api/auth/login`.

- [x] **B37 · Liige muudab oma parooli** (S) — pärast B04
  `PUT /api/me/password` `{currentPassword, newPassword}`: praegune peab klappima (vale →
  `ForbiddenException`), uus vastab paroolireeglitele (400). Muud sessioonid jäävad kehtima (üks instants,
  lihtsus). Parooli ei logita.
  Tehtud: vastus 204; vale praegune → 403 `124 WRONG_CURRENT_PASSWORD` koos `remainingAttempts`-iga.
  Lisaks: valed praegused paroolid on piiratud **liikme kaupa** sama kasvava lukustusega nagu
  sisselogimine (5. viga → 429 `117`), et avatud sessiooniga ei saaks parooli ära arvata; see ei
  blokeeri sisselogimist. Uus parool `@StrongPassword`. Töötab ka PIN-sessioonis (praegune
  parool on niikuinii vaja). Lukustuse võti: `LockoutTypeEnum` (PIN_LOGIN, PASSWORD_LOGIN, PASSWORD_CHANGE).
  Valmis, kui: õnnestumine (vana parool ei tööta, uus töötab), vale praegune, lühike uus on testitud.

- [x] **B38 · Admin: seadmete haldus** (S) — pärast B04, B35
  (Eraldatud B35-st: vajab B04 admini sessiooni, et teada `created_by` / `revoked_by` ja kaitsta `/api/admin/**`.)
  `POST /api/admin/devices` `{name}` (parooliga admini sessioon) loob seadme (`DeviceService.createToken()`,
  andmebaasi räsi) ja paneb **sellele** brauserile küpsise `createDeviceCookie()` kaudu.
  `GET /api/admin/devices` (nimi, registreeritud, kes, viimati kasutatud, tühistatud),
  `POST /api/admin/devices/{id}/revoke` (juba tühistatud → `ForbiddenException`).
  Tehtud: 403 `119 DEVICE_ALREADY_REVOKED`; brauser, mis on juba usaldatud seade, ei saa end uuesti
  registreerida (403 `120 DEVICE_ALREADY_TRUSTED`) — muidu jääks vana rida kehtima ilma omanikuta.
  Tundmatu id → 404 `101`, vigane id (`/abc/`) → 400 `104`. Vastus (`DeviceDto`): id, name, createdAt,
  createdByName, lastUsedAt, revokedAt, revokedByName — tokenit ega räsi ei tagastata.
  Valmis, kui: testid katavad registreerimise (küpsis + räsi baasis, mitte token), nimekirja,
  tühistamise, topelt-tühistamise ja selle, et tavaliige ega PIN-sessioon ei saa seadet registreerida.

## Verstapost 2 — Tooted ja ladu

- [x] **B07 · Tooted** (M) — pärast B04
  `Product` entiteet. `GET /api/products` (ainult aktiivsed, `sort_order` järgi, hinna ja laoseisuga).
  Admin: `GET/POST/PUT /api/admin/products` (nimi, stiil, maht, hind, madala laoseisu piir,
  järjekord, aktiivne). Hind > 0; uuendamisel optimistlik lukk (vananenud versioon → 409).
  Tehtud: liige saab `ProductDto` (id, nimi, stiil, maht, hind, laoseis); admin `AdminProductDto` (+ piir,
  järjekord, aktiivne, ajad, `version`). `POST` → 201, laoseis algab 0-st; `PUT /api/admin/products/{id}`
  vajab laaditud `version`-it (vanem → 409 `105`). Laoseisu vormiga muuta ei saa (ainult laopearaamat, B08).
  Tooteid ei kustutata — peitmine `active = false`. Kinnitust/kustutamist pole.
  Valmis, kui: CRUD, valideerimine ning liikme vs admini ligipääs on testitud.

- [x] **B08 · Laopearaamat ja tarned** (M) — pärast B07
  `StockMovement` entiteet; `StockService.move(product, type, delta, …)` kirjutab liikumise ja
  uuendab `product.stock_qty` ühes transaktsioonis — ainus viis laoseisu muuta.
  `POST /api/admin/stock/deliveries` (read: toode, kogus > 0, valikuline ühiku hind).
  Tehtud: `StockService.move(product, type, delta, unitCostCents, note, createdBy)` tagastab uue laoseisu;
  `stock_qty` muudetakse **ühe SQL lausega** (`stock_qty + delta`), mis ei kaota paralleelseid muudatusi ega
  tõsta toote `version`-it; `Product` on `@DynamicUpdate`, nii et toote vorm ei kirjuta kunagi vana laoseisu üle.
  Tarne `{lines:[{productId, quantity, unitCostCents?}], note?}` → 201 + uus laoseis toote kohta; kõik read või
  mitte midagi (tundmatu toode → 404). Max 100 rida. `StockMovement.purchaseId` on praegu lihtne id — B11 teeb
  sellest seose `Purchase`-iga.
  Valmis, kui: invariandi test `stock_qty = sum(quantity_delta)` läbib pärast tarneid.

- [x] **B09 · Inventuur ja mahakandmised** (S) — pärast B08
  `POST /api/admin/stock/stocktake` (loetud kogus toote kohta → `STOCKTAKE` liikumine vahe
  kohta, võrdsuse korral mitte midagi). `POST /api/admin/stock/write-offs` (kogus + märkus).
  Tehtud: inventuur `{lines:[{productId, countedQty ≥ 0}], note?}` → 201 + toote kohta `previousStockQty`,
  `countedQty`, `difference`. Vahe arvutatakse **salvestamise hetke** laoseisust lukustatud ridadelt (vahepealne
  müük ei moonuta), lukud id järjekorras. Sama toode kaks korda → 403 `125`. Mahakandmine
  `{lines:[{productId, quantity > 0}], note}` — märkus kohustuslik, laoseis võib minna alla nulli.
  B11 peab müügil laoseisu muutma toodete id järjekorras (ummikseisu vältimiseks).
  Valmis, kui: vahed ja tühi juhtum on testitud.

## Verstapost 3 — Ostmine

- [x] **B10 · Rahapearaamat** (S/M) — pärast B03
  `AccountEntry` entiteet (seosed ostu/makse/tasu/osalejaga lihtsate id-dena, kuni need entiteedid
  tekivad). `AccountService.post(AccountPosting)` kirjutab rea ja muudab `member.balance_cents`-i **ühe
  SQL lausega** samas transaktsioonis (ei kaota paralleelseid muudatusi, `version` ei muutu; `Member` on
  `@DynamicUpdate`) ja tagastab uue saldo — ainus viis saldot muuta. `AccountPosting` kontrollib tüübi
  järgi märki ja seost; `ADJUSTMENT` / `MEMBERSHIP_FEE_WAIVER` vajavad märkust. Iga hilisem task lisab
  oma loomismeetodi (B10-s `adjustment`).
  Valmis, kui: invariant `balance_cents = sum(amount_cents)` kehtib iga liikme kohta pärast kandeid;
  kaks samaaegset kannet samale liikmele ei kaota midagi; vale märk, puuduv märkus ja summa 0 lükatakse tagasi.
  Tehtud: reeglid tüübi enum'is (`AccountEntryTypeEnum`: märk, nõutav seos, märkus), `AccountPosting`
  (`service/`) kontrollib neid juba loomisel. Logis ainult id-d ja tüüp (summad/saldod on liikme finantsandmed).

- [x] **B11 · Ostud** (M/L) — pärast B08, B10
  `POST /api/purchases` `{requestId, expectedTotalCents, lines:[{productId, quantity}]}` (1–50 rida, kogus
  1–99, sama toode liidetakse). Üks transaktsioon kirjutab `purchase` (+ `request_id`, unikaalne),
  `purchase_line` (hind kopeeritakse serverist), ühe `PURCHASE` rea summaga −total ja ühe `SALE` liikumise rea
  kohta. Lukud: liikme rida, siis tooted id järjekorras. Summa erineb `expectedTotalCents`-ist → 409 `127`
  (hinnad muutusid); peidetud toode → 403 `126`; tundmatu → 404; neil juhtudel ei kirjutata midagi. Sama
  `requestId` uuesti → juba salvestatud ost (200), uut ei tehta; teise liikme `requestId` → 403 `104`.
  Vastus: ost, read, uus saldo, `undoableUntil` (+10 min serveri ajas). `purchase_id` `account_entry`-s ja
  `stock_movement`-is on seos. Laoseisu muutused `StockMove` kirje kaudu (nagu `AccountPosting`). Saldo
  limiiti pole; laoseis võib minna negatiivseks. `Clock` tiksub mikrosekundites (nagu Postgres).
  Valmis, kui: testid kontrollivad iga kirjutatud rida, mõlemat invarianti, hinnamuutust, korduvat
  `requestId`-d ning et samaaegsed ostud (sama toode, sama liige, ost + inventuur) ei kaota uuendusi ega
  jää ummikusse.

- [x] **B12 · Tagasivõtmine** (S) — pärast B11
  `POST /api/purchases/{id}/undo`: liige oma ostu 10 minuti jooksul, admin (paroolisessioon) igal ajal;
  teise liikme ost → 404 nagu olematu ost.
  Märgib `REVERSED`, kirjutab `PURCHASE_REVERSAL` ja `SALE_REVERSAL` read. Teist korda →
  `ForbiddenException`. Lukud samas järjekorras nagu ostul (liige, siis tooted id järgi); lisa
  `AccountPosting.purchaseReversal` ja `StockMove.saleReversal`; 10 min piir `PurchaseService.UNDO_WINDOW`.
  Valmis, kui: 10 minuti piir (fikseeritud `Clock`), omanikukontroll ja topelt-tagasivõtmine on testitud.

- [x] **B13 · Lemmikud** (S) — pärast B07
  `MemberFavourite` entiteet. `GET /api/me/favourites` → järjestatud toote id-d (ka peidetud tooted —
  ostuvaade näitab ainult tootenimekirjas olevaid); `PUT /api/me/favourites` `{productIds}` asendab
  nimekirja ja tagastab selle (positsioonid 0..n−1, ilma korduseta, kuni 100, tundmatu id → 400).
  Valmis, kui: asendamine, ümberjärjestamine ja tühi nimekiri on testitud.

## Verstapost 4 — Konto ja maksed

- [x] **B39 · Ühingu andmed** (S) — pärast B04; B15 ja B26 kasutavad
  Saaja nimi ja IBAN on andmebaasis (admin muudab), mitte konfiguratsioonis. Tabel `organisation`
  (üks rida `id = 1`, `name`, `iban`, `updated_by`, `updated_at`, `version`); `2_create.sql` lisab tühja
  rea, `3_import.sql` arenduse väärtused. `GET /api/admin/organisation`, `PUT /api/admin/organisation`
  `{name ≤ 100, iban, version}`: IBAN normaliseeritakse (tühikud välja, suurtähed) ja kontrollitakse
  (ISO 13616 mod 97, ≤ 34 märki) → 400; vananenud `version` → 409. Muutus logitakse admini id-ga.
  IBAN-i muutumisel saadetakse kõigile aktiivsetele adminitele eestikeelne e-kiri (kes muutis, millal, vana
  ja uus IBAN); kirja saatmise viga ei tühista muudatust, see logitakse.
  `revkassa.invoice.org-name` / `.iban` kaovad seadetest (`RevkassaProperties`, `MailConfig`);
  `due-days` jääb.
  Valmis, kui: kehtiv/vigane IBAN, normaliseerimine, optimistlik lukk, ainult admini ligipääs ja
  teavituskiri (ainult IBAN-i muutumisel, kõigile aktiivsetele adminitele) on testitud.

- [x] **B14 · Saldo ajalugu** (S) — pärast B11
  `GET /api/me/entries?page=` uuemad ees, loetava kirjeldusega (nt "2 × Saku
  Originaal", "Makse · pangaülekanne"). 20 rida lehel, vastus `{entries, page, hasMore}`; kirjeldused
  `AccountEntryService.describe` — makse viisi (B15), liikmemaksu kuu (B23) ja ürituse nime (B29) lisavad
  need taskid.
  Valmis, kui: lehekülgede kaupa laadimine ja kirjeldused on testitud.

- [x] **B15 · Maksest teatamine** (S) — pärast B10, B39
  Liige maksab tavalise pangaülekandega (makseplatvormi ega pangalingi lepingut pole).
  `GET /api/payments/details` → `{recipient, iban, description}`: saaja ja IBAN `organisation` tabelist
  (B39; seadistamata → 404, kood 131), selgitus "RevKassa - Kaspar Kask" (lihtsad märgid, mida pangad
  ülekande selgituses ei muuda), et admin tunneks ülekande väljavõttel ära. `POST /api/payments` `{amountCents > 0, reference?}` (ülempiirita,
  viide ≤ 140 märki) → `PENDING` `BANK_TRANSFER`, saldo ei muutu; topeltsaatmine on lubatud (admin lükkab
  korduse tagasi). `GET /api/me/payments?status=` `PENDING` (vaikimisi) | `CONFIRMED` | `REJECTED`, uuemad
  ees, kuni 50 — tagasilükatul on admini `decisionNote`. Skeem: `CARD` kaob (`payment.method` jääb
  tulevase terminali jaoks, lubatud ainult `BANK_TRANSFER`; algandmete `CARD` read → `BANK_TRANSFER`).
  B14 jätk: kinnitatud makse ajaloos "Makse · pangaülekanne".
  Valmis, kui: saldo ja pearaamat jäävad puutumata kuni kinnitamiseni, liige näeb ainult oma makseid.

- [x] **B16 · Maksete kinnitamine / tagasilükkamine** (S) — pärast B15
  `GET /api/admin/payments?status=PENDING` (kuni 100; ootel vanemad ees, otsustatud viimati otsustatud ees,
  liikme nimega); `POST /api/admin/payments/{id}/confirm` kirjutab ühe `PAYMENT` rea; `/reject` valikulise
  märkusega (≤ 500). Teist korda otsustamine → `ForbiddenException` (132); otsus lukustab maksja rea.
  `POST /api/admin/payments` `{memberId, amountCents > 0, reference?}`: admin sisestab
  makse, mis salvestatakse kohe `CONFIRMED` (`decided_by` = admin) koos ühe `PAYMENT` reaga.
  Valmis, kui: testid tõestavad täpselt üht rida kinnitatud makse kohta, ka admini sisestatud makse puhul.

- [x] **B17 · Liikmete haldus** (M) — pärast B04, B10
  `GET/POST/PUT /api/admin/members` (nimi, e-post (kohustuslik, unikaalne tõstutundetult —
  võetud → `ForbiddenException`), roll, aktiivne, tasust vabastus; parooli admin ei määra).
  Loomine saadab liikmele kutselingi (B36) ja nimekiri näitab `passwordSet` (kas parool on
  määratud). Loomine ja `POST /api/admin/members/{id}/pin` tagastavad uue PIN-i vastuses **üks kord**.
  `POST /api/admin/members/{id}/adjustments` (nullist erinev summa, kohustuslik märkus).
  `GET /api/admin/members/{id}/purchases?page=` (pärast B12): liikme ostud uuemad ees — aeg,
  read ("2 × Saku Originaal"), summa, staatus; admin võtab ostu tagasi olemasoleva
  `POST /api/purchases/{id}/undo` kaudu.
  Tehtud: nimekiri nime järgi (kõik liikmed, `AdminMemberDto`: memberId, displayName, email, role, active,
  feeExempt, balanceCents, passwordSet, createdAt, version). Loomine → 201 `{member, pin}`; uus liige on aktiivne ja
  saab esimese aktiivse taseme (taseme valik B27); kirja viga → 503 `123` ja liiget ei salvestata. E-post võetud →
  403 `133 EMAIL_TAKEN`. `PUT` saadab tagasi `version`-i (vananenud → 409 `105`); viimast aktiivset adminit ei saa
  alandada ega deaktiveerida → 403 `134 LAST_ADMIN` (adminide read lukustatakse, et kaks adminit ei alandaks teineteist
  korraga). Uus e-post sulgeb vanale aadressile saadetud lingid. Adminiõiguse kaotanud liikme admini sessioon lõpeb
  järgmisel päringul (403 `118`, `ActiveMemberFilter`). PIN-i lähtestamine → 200 `{pin}` ja muudab `version`-it.
  Parandus → 201 liikmega uue saldoga; summa ≠ 0 (väljaviga `amountCentsNotZero`), kuni ±100 000 €, märkus kuni 500
  märki, ka mitteaktiivsele liikmele. Ostud: 20 lehel, `{purchases:[{purchaseId, createdAt, status, totalCents,
  lines, reversedAt, reversedByName}], page, hasMore}` — read struktuurina (`PurchaseLineDto`), UI paneb kokku
  "2 × Saku Originaal". PIN-i ei logita.

- [ ] **B34 · Liige muudab oma PIN-i** (S) — pärast B05
  `PUT /api/me/pin` `{currentPin, newPin}`: praegune PIN peab klappima (vale → `ForbiddenException`
  ja loeb B05 lukustuse loendurisse — seadme kaupa PIN-sessioonis, IP kaupa parooliga sessioonis), uus täpselt 6 numbrit (400), eelmisest erinev ja unikaalne
  (võetud → `ForbiddenException`, teade ei ütle, kellele see kuulub). Uuendab `pin_hmac` ja
  `pin_changed_at`; PIN-e ei logita.
  Valmis, kui: õnnestumine (vana PIN kehtetu, uus töötab), vale praegune PIN, vale formaat, sama
  PIN ja võetud PIN on testitud.

## Verstapost 5 — Admini ülevaade

- [x] **B18 · Ülevaade** (S) — pärast B09, B16
  `GET /api/admin/dashboard`: avatud võlgade summa ja arv, ootel maksete arv ja summa,
  madala ja negatiivse laoseisuga tooted, suurimad võlad (top 5), kaart "Enim ostetud sel kuul"
  (top 5 toodet jooksva kuu müüdud tükkide järgi, tagasivõetud ostud välja arvatud; viib müügiaruandesse).
  Valmis, kui: näitajad klapivad testandmetega.
  Tehtud: `DashboardDto` — `openDebtCents`, `debtorCount` (positiivsed võlasummad, **k.a mitteaktiivsed
  liikmed**), `pendingPaymentCount`, `pendingPaymentCents`, `lowStockCount` (laoseis 0…hoiatustase),
  `negativeStockCount` (< 0), `stockToCheck` (`LOW`/`NEGATIVE`, negatiivsed ees; ainult müügil tooted),
  `largestDebts` (top 5, `active = false` = endine liige), `month` + `topProducts` (top 5 tükkide järgi, kuu
  Tallinna ajas). Kõik arvutab andmebaas. Testid võrdlevad näitajaid enne ja pärast oma ridu (arenduse
  andmetest sõltumata). "Tulevad üritused" lisandub ürituste taskiga (B28).

- [x] **B19 · Müügiaruanne** (S) — pärast B11
  `GET /api/admin/reports/sales?from=YYYY-MM-DD&to=YYYY-MM-DD`: müüdud tükid ja tulu toote kaupa,
  enim müüdud ees, tagasivõetud ostud välja arvatud, kokkuvõte all. `from`/`to` on kalendripäevad
  Tallinna ajas, mõlemad kaasa arvatud (ost kell 00:30 läheb uuele päevale); `from` > `to` → 400.
  Kogused osturidadelt (`purchase_line` `COMPLETED` ostudel), tulu = kogus × ostuhetke hind.
  Valmis, kui: päeva piirid (Tallinna kesköö), tagasivõetud ost, järjestus ja vale vahemik on testitud.
  Tehtud: mõlemad kuupäevad valikulised (`from` puudub → esimesest müügist, `to` puudub → täna); `from` > `to`
  → 400 `142`, vigane kuupäev → 400 `104`. Vastus `{from, to, months, products:[{productId, name, active, quantity,
  revenueCents, costCents, marginCents, unitsWithoutCost, months:[{month, quantity}]}], totalQuantity,
  totalRevenueCents, totalCostCents, totalMarginCents, unitsWithoutCost}`: müüdud tooted tükkide järgi, siis
  aktiivsed müümata tooted nulliga (peidetud müümata välja). Nimi on toote praegune nimi.
  **Ostuhind ja marginaal** (kasutaja otsus): uus `product.cost_cents` (admin määrab toote vormis, valikuline,
  liikmele ei näidata) ja `purchase_line.unit_cost_cents` (ostuhind müügi hetkel, kopeeritakse ostul). Kulu =
  rea ostuhind, selle puudumisel toote praegune ostuhind; kui ka see puudub, on toote `costCents`/`marginCents`
  null ja tükid lähevad `unitsWithoutCost`-i (kokkuvõtte kulu/marginaal ainult teadaoleva ostuhinnaga toodetest).
  Muudatusskript `2026-11-26_ostuhind.sql`; tarne ühiku hind ostuhinda ei muuda.

## Verstapost 6 — Tasud ja arved

- [x] **B22 · Liikmemaks taseme kaupa** (S) — pärast B04, B27
  `MembershipFee` entiteet (tase, kuu, summa). `GET /api/admin/fees` → praegune tasu taseme
  kaupa + ajalugu. `POST /api/admin/fees` `{validFrom: "YYYY-MM", fees:[{levelId, amountCents ≥ 0}]}`:
  kuu peab olema tulevikus või jooksev kuu, kui selle eest pole veel tasu võetud; tasemel on
  selle kuu rida juba olemas → `ForbiddenException`. `feeFor(level, month)` = hiliseim
  `valid_from` ≤ kuu, muidu 0.
  Valmis, kui: ajaloo otsing taseme kaupa, tasudeta tase, kuu reeglid ja 0 € on testitud.
  Tehtud: `GET` → kõik tasemed järjekorras: `{levelId, name, active, currentAmountCents, upcoming, history}`
  (`upcoming` = lähim plaanitud muudatus või null; `history` uuemad ees, plaanitud read k.a; kuud kujul "YYYY-MM").
  `POST` → 200 sama vaatega; ainult osa tasemeid on lubatud, kõik salvestatakse või mitte midagi.
  Möödunud kuu → 403 `139`; kuu, mille eest sellelt tasemelt on juba tasu võetud → 403 `140` (kontroll taseme kaupa,
  nii et uuele/tasuta tasemele saab jooksva kuu tasu panna). Sama kuu uuesti salvestamine **asendab** rea, kuni seda
  kuud pole sellelt tasemelt võetud (vale summa saab parandada). Summa 0–10 000 €; sama tase kaks korda → 400
  (`fieldErrors.levelIdsUnique`); tundmatu tase → 404. Salvestamine lukustab tasemete read.
  `MembershipFeeService.feeFor(levelId, month)` B23 jaoks.

- [x] **B23 · Kuine tasutöö** (M) — pärast B10, B17, B22 (tasemete tasud)
  `MembershipChargeService.chargeMonth(month)`: iga aktiivse, tasust vabastamata liikme kohta,
  kes on loodud enne kuud ja kellelt pole veel tasu võetud, võtab `feeFor(member.level, month)`
  (0 → jätab vahele), kirjutab `membership_charge` (tasemega) + ühe `MEMBERSHIP_FEE` rea
  `AccountService.post` kaudu (`created_by` null), üks transaktsioon liikme kohta.
  `@Scheduled` iga päev kell 06:00 (Europe/Tallinn) ja üks kord käivitumisel jooksva kuu jaoks.
  Unikaalne `(member_id, month)` kaitseb paralleelse jooksu eest. Ajaloo kirjeldus:
  "Liikmemaks · oktoober 2026".
  B22-st: tasu loetakse **tasustatava kuu** järgi (`feeFor(level, month)`), mitte tänase tasu järgi — järeletehtud
  vana kuu saab selle kuu tasu, tasu tõus ei muuda varasemaid kuid. Loe taseme tasu taseme rea lukuga (sama, mida
  `POST /api/admin/fees` võtab), et samal ajal salvestatav uus tasu ei jääks poolikult kehtima.
  Valmis, kui: fikseeritud `Clock`-iga testid katavad 1. kuupäeva, hilisema järeletegemise,
  kaks jooksu (topelttasu pole), eri tasemetelt eri summad, vabastatud/mitteaktiivsed/uued
  liikmed vahele jäetud, tasu 0 ei kirjuta midagi, invariandid kehtivad.
  Tehtud: `MembershipChargeService.chargeMonth(month)` (iga liige oma transaktsioonis, viga logitakse ja järgmine
  jooks proovib uuesti; logirida `Monthly fees run: month, charged, skipped, failed`) ja `chargeMember(memberId,
  month)`: liikme rida lukus → tingimused uuesti → taseme rida jagatud lukuga → `feeFor(level, month)`.
  `MembershipChargeScheduler`: käivitumisel + iga päev 06:00 (Europe/Tallinn) **ainult jooksev kuu**; seade
  `revkassa.membership-fees.job-enabled` (testides `false`, sest testid kasutavad arenduse andmebaasi).
  "Loodud enne kuud" = `created_at` < kuu algus Tallinna ajas. Liige, kes muutub kuu keskel tasustatavaks
  (taasaktiveeritud, vabastus eemaldatud, tasemele pandi jooksva kuu tasu), saab järgmisel päeval terve kuu tasu —
  admin saab selle B24-ga tühistada. `account_entry.membership_charge_id` on nüüd seos (`MembershipCharge`),
  ajalugu näitab "Liikmemaks · november 2026". Testid kuuga parameetrina (fikseeritud kella konteksti pole vaja).

- [x] **B24 · Tasude nimekiri ja tühistamine** (S) — pärast B23
  `GET /api/admin/fees/charges?month=YYYY-MM` (liige, summa, staatus, kokkuvõtted).
  `POST /api/admin/fees/charges/{id}/waive` `{note}` (kohustuslik) → `WAIVED` +
  `MEMBERSHIP_FEE_WAIVER` rida summaga +summa. Teist korda → `ForbiddenException`.
  Valmis, kui: tühistamine, topelt-tühistamine ja invariant on testitud.
  Tehtud: `GET` (`month` valikuline → jooksev kuu) → `{month, charges:[{chargeId, memberId, memberName, levelName,
  amountCents, status, createdAt, waivedAt, waivedByName, waiveNote}], chargedCount, chargedTotalCents, waivedCount,
  waivedTotalCents}` nime järgi; vigane kuu → 400 `104`. `waive` → 200 uuendatud tasuga; märkus kohustuslik (≤ 500),
  liige näeb ajaloos "Liikmemaksu tühistus · november 2026 · <märkus>"; teist korda → 403 `141`; tundmatu → 404.
  Iga kuu ja ka mitteaktiivne liige; tühistus on lõplik (kuu eest ei võeta enam tasu, tagasi ainult parandusega).
  Liikme rida lukustatakse enne. Uus unikaalne indeks `ux_account_entry_membership_charge_id_type`
  (muudatusskript `2026-11-26_tasu-ridade-unikaalsus.sql`).

(B25 · E-kirjade saatmine on verstapostis 1, sest parooli lingid vajavad seda.)

- [ ] **B26 · Arved** (M) — pärast B14, B17, B25, B39
  `Invoice` entiteet. `GET /api/admin/invoices/candidates`: liikmed saldoga < 0, e-post,
  viimase arve kuupäev. `GET /api/admin/invoices/preview?memberId=`.
  `POST /api/admin/invoices` `{memberIds}`: liikme kohta oma transaktsioon: järgmine number
  `YYYY-NNNN`, summa = −saldo, maksetähtaeg, lihttekstiga **eestikeelne** sisu (võlg, read
  eelmisest arvest või viimased 60 päeva, saaja ja IBAN `organisation` tabelist, selgitus "Arve <number>"; `et-EE` raha ja
  kuupäevad, eestikeelsed kuunimed), saada, salvesta `SENT` või `FAILED` + viga;
  tagastab tulemuse liikme kaupa. Saldo ≥ 0 → vahele jäetud koos põhjusega.
  `GET /api/admin/invoices?memberId=`, `POST /api/admin/invoices/{id}/resend` (ainult
  `FAILED`, sama number). Saldod ei muutu kunagi.
  Valmis, kui: võlts-`Mailer`-iga testid katavad numeratsiooni üle aastavahetuse, ebaõnnestunud
  aadressi, mis teisi ei peata, vahelejätmise põhjused, uuesti saatmise ja kirja sisu.

## Verstapost 7 — Üritused

- [x] **B27 · Liikmetasemed** (S) — pärast B17
  `MembershipLevel` entiteet. `GET/POST/PUT /api/admin/levels` (nimi unikaalne tõstutundetult,
  järjekord, aktiivne). Kasutusest mahavõtmine jätab liikmed alles; kustutamine ainult kasutamata
  tasemel (muidu `ForbiddenException`). `member.level_id` liikmete halduses; uus liige saab
  esimese aktiivse taseme.
  Valmis, kui: unikaalne nimi, mahavõtmine vs kustutamine ja vaiketase on testitud.
  Tehtud: `GET` (järjekorras, `MembershipLevelDto`: levelId, name, sortOrder, active, memberCount = aktiivsed liikmed,
  deletable), `POST {name}` → 201 (aktiivne, viimaseks), `PUT /{id} {name, sortOrder, active}`, `DELETE /{id}` → 204.
  Vead: nimi võetud (tõstutundetult) → 403 `135`; kasutusel (liige, k.a mitteaktiivne, kuutasu, tasurida, ürituse
  tasu või osaleja) → 403 `136`; viimast aktiivset taset ei saa maha võtta ega kustutada → 403 `137` (aktiivsed
  read lukustatakse). Liikmete halduses `levelId` (loomisel valikuline → esimene aktiivne `sortOrder` järgi,
  muutmisel kohustuslik); mitteaktiivne tase → 403 `138`, v.a liikme praegune tase. `AdminMemberDto`-s
  `levelId` + `levelName`. Tasemel pole `version`-it: samaaegsel muutmisel jääb viimane salvestus.

- [ ] **B28 · Üritused** (M) — pärast B27
  B18-st: ülevaate kaart "Tulevad üritused" (nimi, aeg · koht, osalejate arv) lisa `DashboardDto`-sse siin.
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
  küpsised k.a `REVKASSA_DEVICE`, `revkassa.app-url` HTTPS aadressiga), systemd unit, öine `pg_dump` taastamise kontrolliga. Tootmise andmebaas luuakse
  `2_create.sql`-iga (ilma `3_import.sql`-ita), hilisemad muudatused `docs/database/muudatused/`
  skriptidega. Dokumenteeri kausta `docs/`.
