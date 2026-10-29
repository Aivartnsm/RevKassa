# Sisselogitud liikme profiil ja saldo

**Teenus:** `GET /api/me`

**Vaste mockupis:** [RevKassa Mockups](https://claude.ai/artifact/CiWBMreAkasT8zYQP9A222) — "Liikme rakendus — telefon" (`Main.dc.html`) ja "Liikme rakendus — tahvel" (`Tablet.dc.html`): päis nime ja saldoga kõigil sisselogitud ekraanidel (`isApp`, nt "Mari Tamm · Võlg 12,50 €") ja ekraan "Minu konto" (`isAccount`) saldo plokk ("Võlgned" / "Sinu ettemaks")
**BE task:** B04 · **Kes:** liige (PIN- või admini sessioon)

## Sisend

Teenusel puuduvad sisendid. Liige tuleb sessioonist; teise liikme andmeid selle teenusega küsida ei
saa.

## Väljund

**Response (200 OK):** sisselogitud liikme profiil ja **värske** saldo andmebaasist. DTO
`MemberSessionDto` (`controller/common/dto/`, sama mis `POST /api/auth/pin` ja `POST /api/auth/admin`
vastusel).

```json
{
  "memberId": 1,
  "displayName": "Mari Tamm",
  "role": "ADMIN",
  "balanceCents": -1250
}
```

- `displayName` — `member.display_name`.
- `role` — `MEMBER` või `ADMIN` (liikme roll, mitte sessiooni tüüp: admin PIN-sessiooniga näeb
  samuti `ADMIN`).
- `balanceCents` — `member.balance_cents` eurosentides; positiivne = ettemaks, negatiivne = võlg.
  UI vormindab: "Võlg 12,50 €" / "Ettemaks 4,50 €" (päis), "Võlgned" / "Sinu ettemaks" (konto).
- Iga päring loeb liikme andmebaasist uuesti (saldo muutub ostude, maksete ja tasudega), seda ei
  võeta sessioonist.

## Eesmärk

Pärast sisselogimist näitab liikme rakendus igal ekraanil päises liikme nime ja saldot ning "Minu
konto" ekraanil suurelt saldot. Pärast ostu, makse teatamist või lehe värskendamist küsib UI selle
teenuse kaudu ajakohase saldo. Teenus on ka lihtne viis kontrollida, kas sessioon veel kehtib
(aegunud sessioon → 401 → UI naaseb PIN-i klahvistikule).

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
```

Teenus loeb veerge `id`, `display_name`, `role`, `balance_cents`, `active`. `balance_cents` on
`account_entry` ridade summa vahemälu (B10); teenus `account_entry` tabelit ise ei loe. Saldo
ajalugu on eraldi teenus `GET /api/me/entries` (B14). Teenus andmebaasi ei kirjuta.

### Näidisandmed

Vt `docs/database/3_import.sql`. Näide on Mari Tamm (`id` 1, `ADMIN`, saldo `-1250`) — sama, mis
lõuendi `Main.dc.html` päises. Tavaliige: Kaspar Kask (`id` 2, saldo `-2740`); ettemaksuga: Jaan Saar
(`id` 5, saldo `450`); mitteaktiivne: Kertu Mets (`id` 6).

## Veaolukorrad

Veakoodid on ettepanekud — lõplikud väärtused määratakse `Error` enum'is (B02).

| Olukord | Status code | Response body |
|---|---|---|
| Sessioon puudub või on aegunud | 401 Unauthorized | `{ "message": "Sessioon on aegunud. Logi uuesti sisse.", "errorCode": 115 }` |
| Liige on sessiooni ajal mitteaktiivseks muudetud | 403 Forbidden | `{ "message": "Konto ei ole aktiivne. Võta ühendust adminiga.", "errorCode": 112 }` |
| Ootamatu serveri viga | 500 Internal Server Error | `{ "message": "Midagi läks valesti. Proovi uuesti.", "errorCode": 500 }` |

Märkused:
- 401 vastuse `ApiError` keha tuleb Spring Security `AuthenticationEntryPoint`-ist (B04), mitte
  vaikimisi HTML/tühjast vastusest — sama kehtib kõigile kaitstud endpointidele.
- Mitteaktiivse liikme juhul sessioon invalideeritakse.

## Vastuvõtu kriteeriumid

- [ ] Endpoint `GET /api/me` on olemas ja nõuab sisselogimist.
- [ ] PIN-sessiooniga → 200, vastus `{ memberId, displayName, role, balanceCents }` andmebaasi väärtustega.
- [ ] Admini sessiooniga → 200 sama kujuga, `role: "ADMIN"`.
- [ ] Saldo on värske: kui `balance_cents` andmebaasis muutub, näitab järgmine päring uut väärtust.
- [ ] Negatiivne, null ja positiivne saldo tagastatakse õigesti (sentides, märgiga).
- [ ] Ilma sessioonita / aegunud sessiooniga (1 min PIN, 30 min admin) → 401 `ApiError`-iga.
- [ ] Mitteaktiivseks muudetud liige → 403, sessioon invalideeritakse.
- [ ] Päring pikendab sessiooni (tegevusetuse taimer nullitakse).
- [ ] Vastuses pole `pin_hmac`-i, `password_hash`-i ega e-posti.
- [ ] MockMvc testid katavad õnnestumise mõlema sessioonitüübiga, 401 ja 403; `@SpringBootTest` kontrollib väärtusi päris Postgresist.
