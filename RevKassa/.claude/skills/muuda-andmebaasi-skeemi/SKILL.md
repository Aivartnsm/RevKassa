---
name: muuda-andmebaasi-skeemi
description: Lisa või muuda RevKassa andmebaasi tabeleid/veerge/indekseid/piiranguid/algandmeid docs/database/ SQL skriptide kaudu. Kasuta iga skeemimuudatuse puhul (uus tabel, veerg, indeks, piirang, algandmed) või kui kasutaja mainib "muuda skeemi", "lisa veerg", "2_create.sql" vms.
---

# Andmebaasi skeemi muutmine

Skeemi kavand on failis `docs/DB-MODEL.md`, skeem ise failides `docs/database/`:
`1_drop.sql` (kustutab skeemi), `2_create.sql` (kogu praegune skeem + 5 liikmetaset),
`3_import.sql` (arenduse näidisandmed). Flyway't pole; skripte käivitatakse käsitsi.

## Iga muudatus puudutab kõiki neid kohti

1. **`docs/DB-MODEL.md`** — tabeli kirjeldus, indeksid, piirangud, invariandid.
2. **`docs/database/2_create.sql`** — muuda nii, et see looks alati kogu **praeguse** skeemi nullist.
3. **`docs/database/3_import.sql`** — kohanda näidisandmed (nt uus `not null` veerg vajab väärtust).
4. **`docs/database/1_drop.sql`** — uus tabel lisa kustutamisse (sõltuvuste järjekorras).
5. **Muudatusskript tootmise jaoks** `docs/database/muudatused/<AAAA-KK-PP>_<lühikirjeldus>.sql` —
   `ALTER`/`CREATE` laused, mis viivad olemasoleva andmebaasi eelmisest skeemist uude **andmeid
   kaotamata**. Tootmises ei tohi kunagi `1_drop.sql`-i käivitada.
6. **Entiteet** `persistence/<entiteet>/` — Hibernate valideerib (`ddl-auto=validate`), seega iga
   entiteedi väli vajab sobiva tüübiga veergu, muidu rakendus ei käivitu.

## Konventsioonid (vastavalt DB-MODEL.md-le)
- `id bigint generated always as identity primary key`
- raha `*_cents bigint not null`, kogused `integer`, rahale mitte kunagi `numeric`/ujukoma
- ajatemplid `timestamptz not null default now()`
- enumid `text not null check (col in ('A','B'))`
- FK nimed `fk_<tabel>_<veerg>`, indeksid `ix_<tabel>_<veerud>`, unikaalsus `ux_<tabel>_<veerud>`
- pearaamatud (`account_entry`, `stock_movement`) on ainult lisatavad; nendesse ei tohi minna `ON DELETE CASCADE`

`not null` veeru lisamine andmetega tabelisse (muudatusskriptis): lisa vaikeväärtusega või
lisa nullable, täida andmed, siis sea `not null` — ühes skriptis.

## Kontrolli
```bash
# arenduse andmebaas uuesti nullist (kustutab kõik arenduse andmed — küsi kasutajalt enne luba)
for f in 1_drop 2_create 3_import; do PGPASSWORD=revkassa psql -h localhost -U revkassa -d revkassa -v ON_ERROR_STOP=1 -f docs/database/$f.sql; done
PGPASSWORD=revkassa psql -h localhost -U revkassa -d revkassa -c '\d <tabel>'
cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test
```
Muudatusskripti kontrollimiseks: loo andmebaas **eelmise** `2_create.sql` + `3_import.sql`-iga
(`git show HEAD:docs/database/2_create.sql`), käivita muudatusskript ja võrdle tulemust (`\d`)
uue `2_create.sql`-iga.
