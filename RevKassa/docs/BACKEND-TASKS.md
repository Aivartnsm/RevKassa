# RevKassa — backend tasks

Work top to bottom; each task is one reviewable change with its tests. Rules and API shapes
come from [PLAN.md](PLAN.md), tables from [DB-MODEL.md](DB-MODEL.md). Size: S ≈ hours,
M ≈ a day, L ≈ a few days. Tick the box when "Done when" holds and `./gradlew test` passes.

## Milestone 1 — Foundation

- [ ] **B01 · Initial schema** (M)
  `V1__init.sql` with all 8 tables, checks, FKs and indexes exactly as in DB-MODEL.md.
  Done when: the app starts with `ddl-auto=validate`; a test asserts every table exists.

- [ ] **B02 · Common plumbing** (S) — after B01
  `common` package: domain exceptions (`NotFound`, `Conflict`, `Forbidden`) mapped to
  `ProblemDetail` by a `@RestControllerAdvice`, validation errors as 400 with field list,
  a `Clock` bean (tests control time), JSON config (timestamps ISO-8601, money as integer cents).
  Done when: a `@WebMvcTest` proves each exception's status and body.

- [ ] **B03 · Member entity and PIN hashing** (M) — after B01
  `Member` entity + repository. `PinService`: HMAC-SHA256 with secret from
  `revkassa.pin-secret` (env var, app refuses to start without it), `generateUniquePin()`
  (6 digits, retries on collision), `findByPin()`. PINs never logged.
  Done when: tests cover hashing, uniqueness retry, and lookup.

- [ ] **B04 · Security and login** (L) — after B02, B03
  Spring Security with session cookie + CSRF (cookie token for the SPA).
  `POST /api/auth/pin` → member session, idle timeout **1 min**;
  `POST /api/auth/admin` (email + bcrypt password) → admin session, idle timeout **30 min**;
  `POST /api/auth/logout`. `/api/admin/**` needs an admin-login session — a PIN session of an
  admin is still only a member session. `GET /api/me` (name, role, balance).
  Done when: MockMvc tests cover both logins, wrong credentials, timeouts set per session,
  PIN session denied on `/api/admin/**`, inactive member denied.

- [ ] **B05 · PIN brute-force lockout** (S) — after B04
  Per client IP: after 5 wrong PINs lock for 1 min, doubling on each repeat; reset on success.
  In-memory is fine (single instance). Log failures and lockouts without the PIN.
  Done when: tests drive the lock, the doubling and the reset with a fixed `Clock`.

- [ ] **B06 · First admin bootstrap** (S) — after B03
  On startup, if no admin exists, create one from `revkassa.bootstrap.admin-email`,
  `-password`, `-name` (env vars) and log its generated PIN once. No credentials in migrations.
  Done when: test shows it runs once and is skipped when an admin exists.

## Milestone 2 — Products & stock

- [ ] **B07 · Products** (M) — after B04
  `Product` entity. `GET /api/products` (active only, by `sort_order`, with price and stock).
  Admin: `GET/POST/PUT /api/admin/products` (name, style, volume, price, low-stock threshold,
  sort order, active). Price > 0; optimistic locking on update (409 on stale version).
  Done when: CRUD, validation and member-vs-admin access are tested.

- [ ] **B08 · Stock ledger and deliveries** (M) — after B07
  `StockMovement` entity; `StockService.move(product, type, delta, …)` writes the movement and
  updates `product.stock_qty` in one transaction — the only way stock changes.
  `POST /api/admin/stock/deliveries` (lines: product, quantity > 0, optional unit cost).
  Done when: invariant test `stock_qty = sum(quantity_delta)` passes after deliveries.

- [ ] **B09 · Stocktake and write-offs** (S) — after B08
  `POST /api/admin/stock/stocktake` (counted quantity per product → `STOCKTAKE` movement for
  the difference, none when equal). `POST /api/admin/stock/write-offs` (quantity + note).
  Done when: differences and the no-op case are tested.

## Milestone 3 — Buying

- [ ] **B10 · Money ledger** (S) — after B03
  `AccountEntry` entity; `AccountService.post(member, type, amount, …)` writes the entry and
  updates `member.balance_cents` in one transaction — the only way a balance changes.
  Done when: invariant test `balance_cents = sum(amount_cents)` passes.

- [ ] **B11 · Purchases** (M) — after B08, B10
  `POST /api/purchases` `{lines:[{productId, quantity}]}`: one transaction writes `purchase`,
  `purchase_line` (price copied), one `PURCHASE` entry of −total, one `SALE` movement per line.
  Inactive product → 409. No balance limit; stock may go negative.
  Done when: tests check every written row, both invariants, and concurrent purchases
  of the same product/member don't lose updates.

- [ ] **B12 · Undo / reverse** (S) — after B11
  `POST /api/purchases/{id}/undo`: member on their own purchase within 10 minutes, admin
  anytime. Marks `REVERSED`, writes `PURCHASE_REVERSAL` and `SALE_REVERSAL` rows. Twice → 409.
  Done when: the 10-minute boundary (fixed `Clock`), ownership and double-undo are tested.

- [ ] **B13 · Favourites** (S) — after B07
  `MemberFavourite` entity. `GET /api/me/favourites` → ordered product ids;
  `PUT /api/me/favourites` `{productIds}` replaces the list (positions 0..n−1, no duplicates,
  unknown id → 400).
  Done when: replace, reorder and empty list are tested.

## Milestone 4 — Account & payments

- [ ] **B14 · Balance history** (S) — after B11
  `GET /api/me/entries?page=` newest first, with a readable description (e.g. "2 × Saku
  Originaal", "Payment · bank transfer").
  Done when: paging and descriptions are tested.

- [ ] **B15 · Report a payment** (S) — after B10
  `POST /api/payments` (amount > 0, `CARD`/`BANK_TRANSFER`, optional reference) → `PENDING`,
  no balance change. `GET /api/me/payments?status=PENDING` for the "waiting" list.
  Done when: the balance is untouched until confirmation.

- [ ] **B16 · Confirm / reject payments** (S) — after B15
  `GET /api/admin/payments?status=PENDING`; `POST /api/admin/payments/{id}/confirm` writes one
  `PAYMENT` entry; `/reject` with optional note. Deciding twice → 409.
  Done when: tests prove exactly one entry per confirmed payment.

- [ ] **B17 · Member admin** (M) — after B04, B10
  `GET/POST/PUT /api/admin/members` (name, role, active; email + password required for admins).
  Create and `POST /api/admin/members/{id}/pin` return a new PIN **once** in the response.
  `POST /api/admin/members/{id}/adjustments` (non-zero amount, required note).
  Done when: admin rules, PIN reset invalidating the old PIN, and adjustments are tested.

## Milestone 5 — Admin overview

- [ ] **B18 · Dashboard** (S) — after B09, B16
  `GET /api/admin/dashboard`: open tabs total and count, pending payments count and sum,
  low-stock and negative-stock products, largest open tabs (top 5).
  Done when: figures match seeded test data.

- [ ] **B19 · Sales report** (S) — after B11
  `GET /api/admin/reports/sales?from=&to=`: units and revenue per product per month,
  reversed purchases excluded.

## Milestone 6 — Release

- [ ] **B20 · Single jar** (S) — after the frontend exists
  Gradle task builds `frontend/` and copies `dist/` into the jar's static resources; unknown
  non-`/api` paths fall back to `index.html`.
  Done when: `./gradlew bootJar` + `java -jar` serves the UI and the API.

- [ ] **B21 · Run and back up** (S)
  Production profile (secrets from env, secure cookies), systemd unit, nightly `pg_dump`
  with a restore check. Document in `docs/`.
