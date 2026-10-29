# PIN-koodiga sisselogimine

**Teenus:** `POST /api/auth/pin`

**Vaste mockupis:** [RevKassa Mockups](https://claude.ai/artifact/CiWBMreAkasT8zYQP9A222) — "Liikme rakendus — telefon" (`Main.dc.html`) ja "Liikme rakendus — tahvel" (`Tablet.dc.html`), ekraan "PIN-i klahvistik" (`isLogin`, lukustus `isLocked`)
**BE task:** B04 (sisselogimine ja sessioon), B05 (jõurünnaku lukustus) · **Kes:** kõik (sisselogimata kasutaja)

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

## Väljund

**Response (200 OK):** sisselogitud liikme andmed, millega UI täidab päise (nimi ja saldo) ja
suunab ostuvaatesse (`/buy`). DTO `MemberSessionDto` (`controller/common/dto/`, sama kuju
mis `POST /api/auth/admin` ja `GET /api/me` vastusel). Vastusega tuleb `Set-Cookie` uue
sessiooniküpsisega (sessiooni id vahetatakse sisselogimisel).

```json
{
  "memberId": 1,
  "displayName": "Mari Tamm",
  "role": "ADMIN",
  "balanceCents": -1250
}
```

- `displayName` — `member.display_name`, näidatakse päises.
- `role` — `MEMBER` või `ADMIN`. Ka admini puhul on see **ainult liikme sessioon**: `/api/admin/**`
  nõuab eraldi `POST /api/auth/admin` sisselogimist.
- `balanceCents` — `member.balance_cents`, eurosentides; negatiivne = võlg (UI: "Võlgned 12,50 €").
- PIN-i ega `pin_hmac`-i vastuses ei ole.

Sessioon:
- Liikme sessioon aegub pärast **1 minutit tegevusetust** (`session.setMaxInactiveInterval(60)`
  selle sessiooni kohta; admini sessioonil on 30 min, seega ei sobi globaalne seade).
- Õnnestunud sisselogimine nullib selle IP vale-PIN-ide loenduri ja lukustuse taseme (B05) ning
  logitakse (liikme id, IP, aeg — mitte PIN).

## Eesmärk

PIN-i klahvistik on liikme rakenduse esimene ekraan nii telefonis kui ühisel baaritahvlil. Liige
sisestab ainult oma 6-kohalise PIN-i (nime ega e-posti ei valita); kuuenda numbri järel saadab UI
päringu ja PIN üksi tuvastab liikme. Server otsib liikme `PinService.findByPin()` kaudu (HMAC-SHA256
serveri võtmega → `pin_hmac` unikaalne otsing), loob liikme sessiooni ja kaitseb klahvistikku
jõurünnaku eest IP-põhise kasvava lukustusega.

## Seotud andmebaasi tabelid

Vt `docs/database/2_create.sql` (allolev lõik on sealt lühendatult; FK-d ja indeksid on failis
eraldi `ALTER TABLE` / `CREATE INDEX` lausetena).

### member

Liige ja tema PIN-i räsi. Teenus loeb rea `pin_hmac` järgi (unikaalne indeks) ja kontrollib `active`.

```sql
create table member (
    id             bigint generated always as identity primary key,
    email          text,
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
    version        bigint      not null default 0,
    check (role <> 'ADMIN' or (email is not null and password_hash is not null))
);

CREATE UNIQUE INDEX ux_member_pin_hmac on member (pin_hmac);
CREATE UNIQUE INDEX ux_member_email on member (lower(email)) where email is not null;
```

Teenus kasutab veerge `id`, `display_name`, `pin_hmac`, `role`, `balance_cents`, `active`.
`email` ja `password_hash` on admini sisselogimise jaoks (`POST /api/auth/admin`) ega puutu siia.
Teenus andmebaasi ei kirjuta.

Lukustuse loendur (vigade arv, tase, lukustuse lõpp IP kaupa) hoitakse **mälus** (üks instants,
B05), mitte andmebaasis.

### Näidisandmed

Vt `docs/database/3_import.sql`. Näide on Mari Tamm (`id` 1, PIN `482915`, `ADMIN`, saldo
`-1250`) — sama, mis lõuendi `Main.dc.html` päises. Ta on admin, seega `role` on `ADMIN`, aga
sessioon on ikkagi ainult liikme sessioon. Tavaliikme näide: Kaspar Kask (`id` 2, PIN `111111`,
saldo `-2740`); mitteaktiivne: Kertu Mets (PIN `666666`). Arenduses `REVKASSA_PIN_SECRET=dev-pin-secret`.

## Veaolukorrad

Veakoodid on ettepanekud — lõplikud väärtused määratakse `Error` enum'is (B02).

| Olukord | Status code | Response body |
|---|---|---|
| `pin` puudub, ei ole string või ei ole täpselt 6 numbrit. Lukustuse loendurisse ei lähe (PIN-i ei kontrollita). | 400 Bad Request | `{ "message": "PIN-kood peab olema täpselt 6 numbrit.", "errorCode": 110 }` |
| CSRF token puudub või on vale | 403 Forbidden | Spring Security vastus (B04) |
| Vale PIN, lukustuseni on katseid jäänud (1.–4. viga järjest) | 403 Forbidden | `{ "message": "Vale PIN-kood. Veel 4 katset enne lukustust.", "errorCode": 111 }` (arv väheneb: 4 → 1, "Veel 1 katse") |
| PIN on õige, aga liige on mitteaktiivne (`active = false`). Loendurisse ei lähe. | 403 Forbidden | `{ "message": "Konto ei ole aktiivne. Võta ühendust adminiga.", "errorCode": 112 }` |
| Vale PIN käivitab lukustuse: 5. viga järjest (1 min) või iga vale PIN pärast eelmise lukustuse lõppu (5 min → 10 min → 30 min → 1 h → 1 päev, edasi 1 päev) | 429 Too Many Requests, päis `Retry-After: <sekundid>` | `{ "message": "Liiga palju valesid PIN-koode. Proovi uuesti hiljem.", "errorCode": 113 }` |
| Päring lukustuse ajal (ka õige PIN-iga). PIN-i ei kontrollita, lukustust ei pikendata. | 429 Too Many Requests, `Retry-After: <järelejäänud sekundid>` | `{ "message": "Liiga palju valesid PIN-koode. Proovi uuesti hiljem.", "errorCode": 113 }` |
| Ootamatu serveri viga | 500 Internal Server Error | `{ "message": "Midagi läks valesti. Proovi uuesti.", "errorCode": 500 }` |

Märkused:
- Vale PIN on ärireegli rikkumine (`ForbiddenException`, `backend/CLAUDE.md`), mitte 401.
- Vale PIN-i vastus ei ütle midagi selle kohta, kas mõni sarnane PIN on olemas.
- Iga vale PIN ja iga lukustus logitakse IP ja ajaga, **ilma PIN-ita**.
- Kliendi IP = `request.getRemoteAddr()` (rakendus ei ole proxy taga; kui väljalaskes tuleb proxy,
  vaadatakse `X-Forwarded-For` B21-s).
- **Lahknevus lõuendiga:** lukustuse ekraan näitab ka rida "Järgmine vale PIN lukustab 5 minutiks."
  `ApiError { message, errorCode }` seda infot ei kanna ja `Retry-After` annab ainult järelejäänud
  aja. Kas lisada see sõnumisse (nt "… Järgmine vale PIN lukustab 5 minutiks."), lisada eraldi
  päis või jätta rida UI-st välja, otsustatakse enne B05 implementeerimist.

## Vastuvõtu kriteeriumid

B04 — sisselogimine:
- [ ] Endpoint `POST /api/auth/pin` on olemas ja kättesaadav ilma sisselogimiseta (CSRF tokeniga).
- [ ] Õige PIN aktiivse liikmega → 200, vastus `{ memberId, displayName, role, balanceCents }` andmebaasi väärtustega, `Set-Cookie` uue sessiooni id-ga.
- [ ] Sessioon annab ligipääsu liikme endpointidele (nt `GET /api/me`) ja aegub 1 minuti tegevusetuse järel.
- [ ] Admini PIN-iga loodud sessioon on liikme sessioon: `/api/admin/**` → keelatud.
- [ ] Puuduv / vale formaadiga `pin` (`"12345"`, `"1234567"`, `"12a456"`, `123456` numbrina, puuduv väli) → 400 `ApiError`-iga.
- [ ] Vale PIN → 403 `ApiError`-iga, sessiooni ei looda.
- [ ] Mitteaktiivse liikme PIN → 403 `ApiError`-iga, sessiooni ei looda.
- [ ] PIN-i ei ole üheski logireas ega vastuses.

B05 — lukustus (fikseeritud `Clock`-iga):
- [ ] 1.–4. vale PIN → 403 õige järelejäänud katsete arvuga sõnumis.
- [ ] 5. vale PIN → 429, `Retry-After: 60`.
- [ ] Pärast lukustuse lõppu lukustab iga järgmine vale PIN kohe: 300 → 600 → 1800 → 3600 → 86400 s; edasi jääb 86400.
- [ ] Päring lukustuse ajal (ka õige PIN-iga) → 429 järelejäänud ajaga; PIN-i ei kontrollita ja lukustus ei pikene.
- [ ] Õnnestunud sisselogimine nullib loenduri ja taseme (järgmine viga on jälle "Veel 4 katset").
- [ ] Lukustus on IP kaupa: teise IP päringud ei ole mõjutatud.
- [ ] Ebaõnnestumised, lukustused ja õnnestunud sisselogimised logitakse ilma PIN-ita.

Testid:
- [ ] `@WebMvcTest` + MockMvc: valideerimine, staatuskoodid, `ApiError` keha ja `Retry-After` päis.
- [ ] `@SpringBootTest` (päris Postgres, `@Transactional`): õnnestunud sisselogimine, vale PIN, mitteaktiivne liige, lukustuse tasemed `Clock`-iga.
