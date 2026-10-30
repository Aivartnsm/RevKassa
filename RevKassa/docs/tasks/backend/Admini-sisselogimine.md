# Admini sisselogimine

**Teenus:** `POST /api/auth/admin`

**Vaste mockupis:** [RevKassa Mockups](https://claude.ai/artifact/CiWBMreAkasT8zYQP9A222) — "Admin — arvuti" (`Admin.dc.html`) ja "Admin — telefon" (`AdminMobile.dc.html`), ekraan "Admini sisselogimine" (`needsLogin`)
**BE task:** B04 · **Kes:** admin (sisselogimata kasutaja)

## Sisend

Request body (JSON), DTO `AdminLoginRequestDto`:

| Väli | Tüüp | Kohustuslik | Reegel |
|---|---|---|---|
| `email` | string | jah | mitte tühi, e-posti kuju (`@NotBlank`, `@Email`); otsing tõstutundetu (`lower(email)`) |
| `password` | string | jah | mitte tühi (`@NotBlank`) |

```json
{
  "email": "jaan@example.org",
  "password": "salajane-parool"
}
```

Päring nõuab CSRF tokenit (`X-XSRF-TOKEN` päis küpsise `XSRF-TOKEN` väärtusega). Parooli ei logita
kunagi.

## Väljund

**Response (200 OK):** sisselogitud admini andmed, DTO `MemberSessionDto` (`controller/common/dto/`,
sama mis `POST /api/auth/pin` ja `GET /api/me`). Vastusega tuleb `Set-Cookie` uue sessiooniküpsisega.

```json
{
  "memberId": 5,
  "displayName": "Jaan Saar",
  "role": "ADMIN",
  "balanceCents": 450
}
```

Sessioon:
- Admini sessioon aegub pärast **30 minutit tegevusetust** (`session.setMaxInactiveInterval(1800)`).
- Sessioonil on admini õigus: ligipääs `/api/admin/**` alla. Admin on ka liige, seega töötavad ka
  liikme endpointid (nt `GET /api/me`).
- Sisselogimine loob alati uue sessiooni; kui samas brauseris oli PIN-sessioon, asendatakse see.
- Õnnestunud sisselogimine logitakse (liikme id, IP, aeg).

## Eesmärk

Admini lehed (`/admin/...`) vajavad eraldi sisselogimist e-posti ja parooliga — "PIN-koodist siin ei
piisa" (mockup). PIN-sessioon üksi ei anna kunagi admini õigusi, nii et ühise baaritahvli juures PIN-i
näinud inimene ei pääse admini paneeli. Teenus kontrollib e-posti järgi leitud liikme rolli,
aktiivsust ja bcrypt parooli ning loob admini sessiooni.

## Seotud andmebaasi tabelid

Vt `docs/database/2_create.sql` (allolev lõik on sealt lühendatult; FK-d ja indeksid on failis
eraldi `ALTER TABLE` / `CREATE INDEX` lausetena).

### member

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

CREATE UNIQUE INDEX ux_member_email on member (lower(email)) where email is not null;
```

Teenus kasutab veerge `id`, `email`, `display_name`, `password_hash` (bcrypt), `role`,
`balance_cents`, `active`. Piirang tagab, et igal `ADMIN`-il on e-post ja parool. Tavaliikmel võib
olla e-post (arvete jaoks), aga tal pole parooli ja ta ei saa siit sisse logida. Teenus andmebaasi
ei kirjuta.

### Näidisandmed

Vt `docs/database/3_import.sql`. Näide on Jaan Saar (`id` 5, `jaan@example.org`, `ADMIN`, saldo
`450`). Teine admin on Mari Tamm (`mari@example.org`); mõlema arenduse parool on `parool123`.
Tavaliikmel Kaspar Kask (`kaspar@example.org`) on e-post, aga parooli pole → 403. Tootmises
luuakse esimene admin B06-s seadetest; `2_create.sql`-is paroole pole.

## Veaolukorrad

Veakoodid on ettepanekud — lõplikud väärtused määratakse `Error` enum'is (B02). `message` on inglise
keeles (arendajale ja logidele); UI näitab kasutajale eestikeelset teksti `errorCode` järgi.

| Olukord | Status code | Response body | UI tekst (eesti, `errorCode` järgi) |
|---|---|---|---|
| `email` või `password` puudub / tühi, `email` pole e-posti kujul | 400 Bad Request | `{ "message": "Email and password are required.", "errorCode": 120 }` (valideerimisvigade väljad B02 järgi) | Sisesta e-post ja parool. |
| CSRF token puudub või on vale | 403 Forbidden | Spring Security vastus (B04) | — |
| E-postiga liiget pole, liige pole `ADMIN` või parool on vale | 403 Forbidden | `{ "message": "Wrong email or password.", "errorCode": 114 }` | Vale e-post või parool. |
| E-post ja parool õiged, aga admin on mitteaktiivne (`active = false`) | 403 Forbidden | `{ "message": "Account is not active.", "errorCode": 112 }` | Konto ei ole aktiivne. Võta ühendust adminiga. |
| Ootamatu serveri viga | 500 Internal Server Error | `{ "message": "Unexpected server error.", "errorCode": 500 }` | Midagi läks valesti. Proovi uuesti. |

Märkused:
- Kõik kolm vale-andmete juhtumit annavad **sama** vastuse, et ei saaks teada, kas e-post on
  olemas või kas see kuulub adminile.
- Parooli kontrollitakse `PasswordEncoder`-iga (bcrypt), mitte võrdlemisega.
- Ebaõnnestunud katsed logitakse (e-post, IP, aeg — mitte parool).
- Jõurünnaku lukustust (nagu PIN-il, B05) admini sisselogimisel PLAN.md-s ei ole; bcrypt teeb
  katsed aeglaseks. Vajadusel lisatakse eraldi taskiga.

## Vastuvõtu kriteeriumid

- [ ] Endpoint `POST /api/auth/admin` on olemas ja kättesaadav ilma sisselogimiseta (CSRF tokeniga).
- [ ] Õige e-post ja parool aktiivse adminiga → 200, vastus `{ memberId, displayName, role, balanceCents }`, `Set-Cookie` uue sessiooni id-ga.
- [ ] E-posti otsing on tõstutundetu (`JAAN@example.org` töötab).
- [ ] Admini sessioon annab ligipääsu `/api/admin/**` alla ja liikme endpointidele; aegub 30 minuti tegevusetuse järel.
- [ ] PIN-sessioon ei anna `/api/admin/**` ligipääsu ka admini puhul (vt `PIN-koodiga-sisselogimine.md`).
- [ ] Puuduv / tühi / vale kujuga sisend → 400 `ApiError`-iga.
- [ ] Tundmatu e-post, `MEMBER` rolliga liige ja vale parool → sama 403 vastus, sessiooni ei looda.
- [ ] Mitteaktiivne admin → 403, sessiooni ei looda.
- [ ] Parool ega selle räsi ei ole üheski logireas ega vastuses.
- [ ] MockMvc testid katavad õnnestumise, valed andmed, mitteaktiivse admini ja sessiooni aegumise; `@SpringBootTest` päris Postgresi vastu bcrypt räsiga liikmega.
