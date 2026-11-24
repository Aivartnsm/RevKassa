# PIN-koodiga sisselogimine

**Teenus:** `POST /api/auth/pin`

**Vaste mockupis:** [RevKassa Mockups](https://claude.ai/artifact/CiWBMreAkasT8zYQP9A222) — "Liikme rakendus — telefon" (`Main.dc.html`) ja "Liikme rakendus — tahvel" (`Tablet.dc.html`), ekraan "PIN-i klahvistik" (`isLogin`, lukustus `isLocked`)
**BE task:** B04 (sisselogimine ja sessioon), B05 (jõurünnaku lukustus), B35 (usaldatud seade) · **Kes:** sisselogimata kasutaja **usaldatud seadmes**

## Sisend

Request body (JSON), DTO `PinLoginRequestDto`:

| Väli | Tüüp | Kohustuslik | Reegel |
|---|---|---|---|
| `pin` | string | jah | täpselt 6 numbrit (`^\d{6}$`); string, mitte number, et algusnullid säiliksid |

```json
{
  "pin": "482915"
}
```

Lisaks nõuab päring CSRF tokenit: päis `X-XSRF-TOKEN` küpsise `XSRF-TOKEN` väärtusega (B04, Spring
Security cookie-põhine CSRF). PIN-i ei loeta query parameetrist ega logita kunagi.

Päringul peab olema **usaldatud seadme küpsis** `REVKASSA_DEVICE` (B35; `HttpOnly`, brauser saadab
selle ise). Server leiab `DeviceService.findTrusted(request)` kaudu tühistamata `trusted_device` rea;
kui küpsist pole, see on tundmatu või seade on tühistatud → 403 ja PIN-i ei kontrollita.

## Väljund

**Response (200 OK):** sisselogitud liikme andmed, millega UI täidab päise (nimi ja saldo) ja
suunab ostuvaatesse (`/buy`). DTO `MemberSessionDto` (`controller/common/dto/`, sama kuju
mis `POST /api/auth/login` ja `GET /api/me` vastusel). Vastusega tuleb uus `XSRF-TOKEN` küpsis (enne sisselogimist teada olnud CSRF token ei kehti edasi) ja `Set-Cookie` uue
sessiooniküpsisega (sessiooni id vahetatakse sisselogimisel).

```json
{
  "memberId": 1,
  "displayName": "Mari Tamm",
  "role": "ADMIN",
  "balanceCents": -1250,
  "loginMethod": "PIN"
}
```

- `loginMethod` — `PIN` (siin alati); parooliga sisselogimisel `PASSWORD`. UI valib selle järgi
  tegevusetuse aja (1 min / 30 min).
- `displayName` — `member.display_name`, näidatakse päises.
- `role` — `MEMBER` või `ADMIN`. Ka admini puhul on see **ainult liikme sessioon**: `/api/admin/**`
  nõuab parooliga sisselogimist (`POST /api/auth/login`).
- `balanceCents` — `member.balance_cents`, eurosentides; negatiivne = võlg (UI: "Võlgned 12,50 €").
- PIN-i ega `pin_hmac`-i vastuses ei ole.

Sessioon:
- Liikme sessioon aegub pärast **1 minutit tegevusetust** (`session.setMaxInactiveInterval(60)`
  selle sessiooni kohta; parooliga sessioonil on 30 min, seega ei sobi globaalne seade).
- Õnnestunud sisselogimine nullib **selle seadme** vale-PIN-ide loenduri ja lukustuse taseme (B05),
  uuendab `trusted_device.last_used_at`, pikendab seadmeküpsist (uus `Max-Age` 400 päeva) ning
  logitakse (liikme id, seadme id, IP, aeg — mitte PIN).

## Eesmärk

PIN-i klahvistik on liikme rakenduse esimene ekraan **ühisel baaritahvlil** (või muus admini
registreeritud seadmes). Mujal (isiklik telefon, arvuti) PIN-iga sisse ei saa — seal logitakse sisse
e-posti ja parooliga (`Parooliga-sisselogimine.md`), nii et ainult PIN-i teadja ei pääse kellegi
kontole väljastpoolt baari. Liige
sisestab ainult oma 6-kohalise PIN-i (nime ega e-posti ei valita); kuuenda numbri järel saadab UI
päringu ja PIN üksi tuvastab liikme. Server otsib liikme `PinService.findByPin()` kaudu (HMAC-SHA256
serveri võtmega → `pin_hmac` unikaalne otsing), loob liikme sessiooni ja kaitseb klahvistikku
jõurünnaku eest seadmepõhise kasvava lukustusega.

## Seotud andmebaasi tabelid

Vt `docs/database/2_create.sql` (allolev lõik on sealt lühendatult; FK-d ja indeksid on failis
eraldi `ALTER TABLE` / `CREATE INDEX` lausetena).

### member

Liige ja tema PIN-i räsi. Teenus loeb rea `pin_hmac` järgi (unikaalne indeks) ja kontrollib `active`.

```sql
create table member (
    id             bigint generated always as identity primary key,
    email          text        not null,
    display_name   text        not null,
    pin_hmac       text        not null,
    password_hash  text,
    role           text        not null check (role in ('MEMBER', 'ADMIN')),
    balance_cents  bigint      not null default 0,
    active         boolean     not null default true,
    fee_exempt     boolean     not null default false,
    level_id       bigint      not null references membership_level (id),
    pin_changed_at timestamptz,
    created_at     timestamptz not null default now(),
    version        bigint      not null default 0
);

CREATE UNIQUE INDEX ux_member_pin_hmac on member (pin_hmac);
CREATE UNIQUE INDEX ux_member_email on member (lower(email));
```

Teenus kasutab veerge `id`, `display_name`, `pin_hmac`, `role`, `balance_cents`, `active`.
`email` ja `password_hash` on parooliga sisselogimise jaoks (`POST /api/auth/login`) ega puutu siia.
Teenus andmebaasi ei kirjuta.

### trusted_device

Usaldatud seade (B35). Teenus loeb rea `token_hash` järgi (SHA-256 küpsise tokenist, unikaalne
indeks), kontrollib `revoked_at is null` ja kirjutab õnnestumisel `last_used_at`.

```sql
create table trusted_device (
    id           bigint generated always as identity primary key,
    name         text        not null,
    token_hash   text        not null,
    created_by   bigint      not null references member (id),
    created_at   timestamptz not null default now(),
    last_used_at timestamptz,
    revoked_by   bigint      references member (id),
    revoked_at   timestamptz
);

CREATE UNIQUE INDEX ux_trusted_device_token_hash on trusted_device (token_hash);
```

Lukustuse loendur (vigade arv, tase, lukustuse lõpp **seadme id kaupa**) hoitakse **mälus** (üks
instants, B05), mitte andmebaasis.

### Näidisandmed

Vt `docs/database/3_import.sql`. Näide on Mari Tamm (`id` 1, PIN `482915`, `ADMIN`, saldo
`-1250`) — sama, mis lõuendi `Main.dc.html` päises. Ta on admin, seega `role` on `ADMIN`, aga
sessioon on ikkagi ainult liikme sessioon. Tavaliikme näide: Kaspar Kask (`id` 2, PIN `111111`,
saldo `-2740`); mitteaktiivne: Kertu Mets (PIN `666666`). Arenduses `REVKASSA_PIN_SECRET=dev-pin-secret`.
Usaldatud seade: "Arenduse tahvel", küpsis `REVKASSA_DEVICE=dev-tablet-token` (Swaggeris / curl-iga
lisa see käsitsi); "Vana tahvel" (`old-tablet-token`) on tühistatud.

## Veaolukorrad

Veakoodid on ettepanekud — lõplikud väärtused määratakse `Error` enum'is (B02). `message` on inglise
keeles (arendajale ja logidele); UI näitab kasutajale eestikeelset teksti `errorCode` järgi.

| Olukord | Status code | Response body | UI tekst (eesti, `errorCode` järgi) |
|---|---|---|---|
| `pin` puudub, ei ole string või ei ole täpselt 6 numbrit. Lukustuse loendurisse ei lähe (PIN-i ei kontrollita). | 400 Bad Request | `{ "message": "Validate fields", "errorCode": 103, "fieldErrors": { "pin": "PIN must be exactly 6 digits" } }` (B02 üldine valideerimisviga) | PIN-kood peab olema täpselt 6 numbrit. |
| CSRF token puudub või on vale | 403 Forbidden | `{ "message": "Access denied.", "errorCode": 118 }` (`SecurityErrorHandler`) | Midagi läks valesti. Värskenda lehte. |
| Seadmeküpsis puudub, on tundmatu või seade on tühistatud. PIN-i ei kontrollita, loendurisse ei lähe. | 403 Forbidden | `{ "message": "PIN login is only allowed on a registered device.", "errorCode": 116 }` | Selles seadmes saab sisse logida ainult e-posti ja parooliga. |
| Vale PIN, lukustuseni on katseid jäänud (1.–4. viga järjest) | 403 Forbidden | `{ "message": "Wrong PIN. 4 attempts left before lockout.", "errorCode": 111, "remainingAttempts": 4 }` (arv väheneb: 4 → 1) | Vale PIN-kood. Veel N katset enne lukustust. (N = `remainingAttempts`; 1 → "Veel 1 katse") |
| PIN on õige, aga liige on mitteaktiivne (`active = false`). Loendurisse ei lähe. | 403 Forbidden | `{ "message": "Account is not active.", "errorCode": 112 }` | Konto ei ole aktiivne. Võta ühendust adminiga. |
| Vale PIN käivitab lukustuse: 5. viga järjest (1 min) või iga vale PIN pärast eelmise lukustuse lõppu (5 min → 10 min → 30 min → 1 h → 1 päev, edasi 1 päev) | 429 Too Many Requests, päis `Retry-After: <sekundid>` | `{ "message": "Too many wrong PINs. Try again later.", "errorCode": 113, "nextLockoutSeconds": 300 }` | Liiga palju valesid PIN-koode. Proovi uuesti hiljem. |
| Päring lukustuse ajal (ka õige PIN-iga). PIN-i ei kontrollita, lukustust ei pikendata. | 429 Too Many Requests, `Retry-After: <järelejäänud sekundid>` | `{ "message": "Too many wrong PINs. Try again later.", "errorCode": 113, "nextLockoutSeconds": 300 }` | Liiga palju valesid PIN-koode. Proovi uuesti hiljem. |
| Ootamatu serveri viga | 500 Internal Server Error | `{ "message": "Unexpected server error.", "errorCode": 500 }` | Midagi läks valesti. Proovi uuesti. |

Märkused:
- Vale PIN on ärireegli rikkumine (`ForbiddenException`, `backend/CLAUDE.md`), mitte 401.
- Vale PIN-i vastus ei ütle midagi selle kohta, kas mõni sarnane PIN on olemas.
- Seadme kontroll on enne PIN-i kontrolli: usaldamata seadmest ei saa PIN-e proovida isegi
  lukustuseni. (Vormingu valideerimine `@Valid` jookseb enne seda — 400 ei paljasta midagi.)
- Iga vale PIN ja iga lukustus logitakse seadme id, IP ja ajaga, **ilma PIN-ita**.
- **Valikulised väljad `ApiError`-is** (B02; JSON-is ainult siis, kui väärtus on olemas —
  `@JsonInclude(NON_NULL)`): UI ehitab eestikeelse teksti `errorCode` + nende väljade järgi, mitte
  ingliskeelsest `message`-st.
  - `remainingAttempts` (403, kood 111) — mitu valet PIN-i on enne lukustust veel lubatud (4 → 1).
    Ainult enne esimest lukustust; pärast seda lukustab iga vale PIN kohe (429).
  - `nextLockoutSeconds` (429, kood 113) — kui pikalt lukustab **järgmine** vale PIN pärast selle
    lukustuse lõppu (60 → 300 → 600 → 1800 → 3600 → 86400, edasi 86400). UI: "Järgmine vale PIN
    lukustab 5 minutiks." Järelejäänud aeg tuleb `Retry-After` päisest.

## Vastuvõtu kriteeriumid

B04 — sisselogimine:
- [x] Endpoint `POST /api/auth/pin` on olemas ja kättesaadav ilma sisselogimiseta (CSRF tokeniga).
- [x] Ilma seadmeküpsiseta, tundmatu või tühistatud seadme küpsisega → 403 koodiga 116 ka õige PIN-iga; sessiooni ei looda, lukustuse loendur ei muutu.
- [x] Õige PIN aktiivse liikmega usaldatud seadmes → 200, vastus `{ memberId, displayName, role, balanceCents, loginMethod: "PIN" }` andmebaasi väärtustega, `Set-Cookie` uue sessiooni id-ga; `trusted_device.last_used_at` uueneb.
- [x] Sessioon annab ligipääsu liikme endpointidele (nt `GET /api/me`) ja aegub 1 minuti tegevusetuse järel.
- [x] Admini PIN-iga loodud sessioon on liikme sessioon: `/api/admin/**` → keelatud.
- [x] Puuduv / vale formaadiga `pin` (`"12345"`, `"1234567"`, `"12a456"`, puuduv väli; JSON-arv `123456` teisendatakse stringiks ja on lubatud) → 400 `ApiError`-iga.
- [x] Vale PIN → 403 `ApiError`-iga, sessiooni ei looda.
- [x] Mitteaktiivse liikme PIN → 403 `ApiError`-iga, sessiooni ei looda.
- [x] PIN-i ei ole üheski logireas ega vastuses.

B05 — lukustus (fikseeritud `Clock`-iga):
- [ ] 1.–4. vale PIN → 403 koodiga 111 ja `remainingAttempts` 4 → 1.
- [ ] 5. vale PIN → 429, `Retry-After: 60`, `nextLockoutSeconds: 300`.
- [ ] Pärast lukustuse lõppu lukustab iga järgmine vale PIN kohe: 300 → 600 → 1800 → 3600 → 86400 s; edasi jääb 86400. `nextLockoutSeconds` näitab alati järgmist taset (lael 86400).
- [ ] Päring lukustuse ajal (ka õige PIN-iga) → 429 järelejäänud ajaga; PIN-i ei kontrollita ja lukustus ei pikene.
- [ ] Õnnestunud sisselogimine nullib loenduri ja taseme (järgmise vea järel on jälle 4 katset).
- [ ] Lukustus on seadme kaupa: teise usaldatud seadme päringud ei ole mõjutatud.
- [ ] Ebaõnnestumised, lukustused ja õnnestunud sisselogimised logitakse ilma PIN-ita.

Testid:
- [ ] `@WebMvcTest` + MockMvc: valideerimine, staatuskoodid, `ApiError` keha (k.a `remainingAttempts` / `nextLockoutSeconds`, mis teistes vastustes puuduvad) ja `Retry-After` päis.
- [ ] `@SpringBootTest` (päris Postgres, `@Transactional`): õnnestunud sisselogimine, usaldamata / tühistatud seade, vale PIN, mitteaktiivne liige, lukustuse tasemed `Clock`-iga.
