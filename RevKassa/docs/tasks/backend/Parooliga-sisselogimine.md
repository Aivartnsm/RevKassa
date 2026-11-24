# Parooliga sisselogimine

**Teenus:** `POST /api/auth/login`

**Vaste mockupis:** [RevKassa Mockups](https://claude.ai/artifact/CiWBMreAkasT8zYQP9A222) — "Admin — arvuti" (`Admin.dc.html`) ja "Admin — telefon" (`AdminMobile.dc.html`), ekraan "Admini sisselogimine" (`needsLogin`). **Lahknevus lõuendiga:** lõuendil on see ainult admini ekraan; nüüd on see kõigi liikmete sisselogimine väljaspool usaldatud seadet (liikme telefonis ja arvutis avalehel `/`, vt F04/F06). Liikme telefoni joonisel parooli vormi veel pole.
**BE task:** B04 (sisselogimine ja sessioon), B05 (lukustus IP kaupa) · **Kes:** kõik (sisselogimata kasutaja), igas seadmes

## Sisend

Request body (JSON), DTO `LoginRequestDto`:

| Väli | Tüüp | Kohustuslik | Reegel |
|---|---|---|---|
| `email` | string | jah | mitte tühi, e-posti kuju (`@NotBlank`, `@Email`); otsing tõstutundetu (`lower(email)`) |
| `password` | string | jah | mitte tühi (`@NotBlank`) |

```json
{
  "email": "kaspar@example.org",
  "password": "parool123"
}
```

Päring nõuab CSRF tokenit (`X-XSRF-TOKEN` päis küpsise `XSRF-TOKEN` väärtusega). Parooli ei logita
kunagi.

## Väljund

**Response (200 OK):** sisselogitud liikme andmed, DTO `MemberSessionDto` (`controller/common/dto/`,
sama mis `POST /api/auth/pin` ja `GET /api/me`). Vastusega tuleb uus `XSRF-TOKEN` küpsis (enne sisselogimist teada olnud CSRF token ei kehti edasi) ja `Set-Cookie` uue sessiooniküpsisega.

```json
{
  "memberId": 2,
  "displayName": "Kaspar Kask",
  "role": "MEMBER",
  "balanceCents": -2740,
  "loginMethod": "PASSWORD"
}
```

Sessioon:
- Aegub pärast **30 minutit tegevusetust** (`session.setMaxInactiveInterval(1800)`).
- Kõigil on liikme õigused (ostmine, konto, maksed, üritused) — ka isiklikus telefonis.
- `ADMIN` rolliga liige saab lisaks admini õiguse: ligipääs `/api/admin/**` alla. Admini õigus
  tuleb **ainult** parooliga sisselogimisest, mitte kunagi PIN-ist.
- Sisselogimine loob alati uue sessiooni; kui samas brauseris oli PIN-sessioon, asendatakse see.
- Töötab ka usaldatud seadmes (admin tahvlis), seadmeküpsist see ei muuda.
- Õnnestunud sisselogimine nullib selle IP parooli lukustuse loenduri (B05) ja logitakse
  (liikme id, IP, aeg).

## Eesmärk

PIN-iga saab sisse logida ainult admini registreeritud seadmes (baaritahvel). Igal pool mujal —
liikme isiklik telefon, kodune arvuti, admini sülearvuti — logitakse sisse e-posti ja parooliga.
Nii ei piisa kellegi PIN-i teadmisest, et tema arvele väljastpoolt baari osta. Liikme konto loob
admin; parooli määrab liige ise e-kirjaga saadud ühekordse lingi kaudu (B36). Teenus kontrollib
e-posti järgi leitud liikme aktiivsust ja bcrypt parooli ning loob sessiooni, `ADMIN`-ile ka admini
õigusega.

## Seotud andmebaasi tabelid

Vt `docs/database/2_create.sql` (allolev lõik on sealt lühendatult; FK-d ja indeksid on failis
eraldi `ALTER TABLE` / `CREATE INDEX` lausetena).

### member

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

CREATE UNIQUE INDEX ux_member_email on member (lower(email));
```

Teenus kasutab veerge `id`, `email`, `display_name`, `password_hash` (bcrypt), `role`,
`balance_cents`, `active`. `password_hash` on `null`, kuni liige pole kutselingi kaudu parooli
määranud — siis saab ta sisse ainult PIN-iga tahvlis. Teenus andmebaasi ei kirjuta.

Lukustuse loendur (IP kaupa, eraldi PIN-i omast) hoitakse **mälus** (B05).

### Näidisandmed

Vt `docs/database/3_import.sql`. Arenduse parool on `parool123`: Mari Tamm (`mari@example.org`,
`ADMIN`), Jaan Saar (`jaan@example.org`, `ADMIN`, saldo `450`) ja Kaspar Kask
(`kaspar@example.org`, `MEMBER`). Liis Lepp ja Rasmus Pärn pole parooli määranud → 403. Kertu Mets
on mitteaktiivne. Tootmises luuakse esimene admin B06-s seadetest; `2_create.sql`-is paroole pole.

## Veaolukorrad

Veakoodid on ettepanekud — lõplikud väärtused määratakse `Error` enum'is (B02). `message` on inglise
keeles (arendajale ja logidele); UI näitab kasutajale eestikeelset teksti `errorCode` järgi.

| Olukord | Status code | Response body | UI tekst (eesti, `errorCode` järgi) |
|---|---|---|---|
| `email` või `password` puudub / tühi, `email` pole e-posti kujul | 400 Bad Request | `{ "message": "Validate fields", "errorCode": 103, "fieldErrors": {...} }` (B02 üldine valideerimisviga) | Sisesta e-post ja parool. |
| CSRF token puudub või on vale | 403 Forbidden | `{ "message": "Access denied.", "errorCode": 118 }` (`SecurityErrorHandler`) | Midagi läks valesti. Värskenda lehte. |
| E-postiga liiget pole, parool on määramata või vale; lukustuseni on katseid jäänud | 403 Forbidden | `{ "message": "Wrong email or password.", "errorCode": 114, "remainingAttempts": 4 }` | Vale e-post või parool. (Kui parool on määramata, kasuta "Unustasid parooli?".) |
| E-post ja parool õiged, aga liige on mitteaktiivne (`active = false`) | 403 Forbidden | `{ "message": "Account is not active.", "errorCode": 112 }` | Konto ei ole aktiivne. Võta ühendust adminiga. |
| Liiga palju valesid katseid sellelt IP-lt (B05 tasemed) või päring lukustuse ajal | 429 Too Many Requests, `Retry-After: <sekundid>` | `{ "message": "Too many failed logins. Try again later.", "errorCode": 117, "nextLockoutSeconds": 300 }` | Liiga palju ebaõnnestunud katseid. Proovi uuesti hiljem. |
| Ootamatu serveri viga | 500 Internal Server Error | `{ "message": "Unexpected server error.", "errorCode": 500 }` | Midagi läks valesti. Proovi uuesti. |

Märkused:
- Tundmatu e-post, määramata parool ja vale parool annavad **sama** vastuse, et ei saaks teada,
  kas e-post on olemas. Ka tundmatu e-posti puhul tee üks bcrypt kontroll (võltsräsiga), et
  vastuse aeg seda ei reedaks.
- Parooli kontrollitakse `PasswordEncoder`-iga (bcrypt), mitte võrdlemisega.
- Ebaõnnestunud katsed logitakse (e-post, IP, aeg — mitte parool).
- Kliendi IP = `request.getRemoteAddr()` (rakendus ei ole proxy taga; kui väljalaskes tuleb proxy,
  vaadatakse `X-Forwarded-For` B21-s).

## Vastuvõtu kriteeriumid

- [x] Endpoint `POST /api/auth/login` on olemas ja kättesaadav ilma sisselogimiseta (CSRF tokeniga), igast seadmest.
- [x] Õige e-post ja parool aktiivse liikmega → 200, vastus `{ memberId, displayName, role, balanceCents, loginMethod: "PASSWORD" }`, `Set-Cookie` uue sessiooni id-ga.
- [x] E-posti otsing on tõstutundetu (`KASPAR@example.org` töötab).
- [ ] `MEMBER`-i sessioon annab ligipääsu liikme endpointidele (k.a `POST /api/purchases`), aga `/api/admin/**` → 403.
- [x] `ADMIN`-i sessioon annab ligipääsu `/api/admin/**` alla ja liikme endpointidele.
- [x] Sessioon aegub 30 minuti tegevusetuse järel.
- [x] Puuduv / tühi / vale kujuga sisend → 400 `ApiError`-iga.
- [x] Tundmatu e-post, määramata parooliga liige ja vale parool → sama 403 vastus, sessiooni ei looda.
- [x] Mitteaktiivne liige → 403, sessiooni ei looda.
- [ ] 5. vale katse samalt IP-lt → 429 (B05); teise IP katsed ei ole mõjutatud; PIN-i loendur ei muutu.
- [x] Parool ega selle räsi ei ole üheski logireas ega vastuses.
- [ ] MockMvc testid katavad õnnestumise (liige ja admin), valed andmed, määramata parooli, mitteaktiivse liikme, lukustuse ja sessiooni aegumise; `@SpringBootTest` päris Postgresi vastu bcrypt räsiga liikmega.
