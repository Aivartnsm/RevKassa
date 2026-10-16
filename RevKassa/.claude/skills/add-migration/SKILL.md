---
name: add-migration
description: Add or change RevKassa database tables/columns via a Flyway migration. Use for any schema change (new table, column, index, constraint, seed data).
---

# Adding a Flyway migration

The schema design lives in `docs/DB-MODEL.md`. Update it in the same change as the migration.

## Rules
- Files: `src/main/resources/db/migration/V<n>__<snake_case_summary>.sql`, `<n>` = next
  integer after the highest existing one. Check with `ls src/main/resources/db/migration`.
- **Never edit a migration that has been applied** (anything already committed or run
  against the user's DB) — Flyway checksums fail. Write a new one instead.
- Hibernate only validates (`ddl-auto=validate`): every entity field needs a matching
  column with a compatible type, or the app won't start.
- Conventions (match DB-MODEL.md):
  - `id bigint generated always as identity primary key`
  - money `*_cents bigint not null`, quantities `integer`, never `numeric` floats for money
  - timestamps `timestamptz not null default now()`
  - enums as `text not null check (col in ('A','B'))`
  - FKs named `fk_<table>_<column>`, indexes `ix_<table>_<columns>`, uniques `ux_<table>_<columns>`
  - ledger tables (`account_entry`, `stock_movement`) stay append-only; no `ON DELETE CASCADE` into them
- Adding a `not null` column to a table with data: add with a default, or add nullable,
  backfill, then set not null — in one migration.
- Seed/reference data goes in its own migration; test-only data does not belong in migrations.

## Verify
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test
PGPASSWORD=revkassa psql -h localhost -U revkassa -d revkassa -c '\d <table>'
```
If a migration failed halfway in local dev, fix the SQL and repair with a fresh DB only after
asking the user (`DROP SCHEMA public CASCADE` destroys their data).
