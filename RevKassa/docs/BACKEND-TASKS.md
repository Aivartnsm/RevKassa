# RevKassa — backend tasks

Work top to bottom; each task is one reviewable change with its tests. Rules and API shapes
come from [PLAN.md](PLAN.md), tables from [DB-MODEL.md](DB-MODEL.md). Size: S ≈ hours,
M ≈ a day, L ≈ a few days. Task numbers are ids, not order. Tick the box when "Done when" holds and `./gradlew test` passes.

## Milestone 1 — Foundation

- [ ] **B01 · Initial schema** (M)
  `V1__init.sql` with all 16 tables (incl. the 5 seeded membership levels), checks, FKs and indexes exactly as in DB-MODEL.md.
  Done when: the app starts with `ddl-auto=validate`; a test asserts every table exists.

- [ ] **B02 · Common plumbing** (S) — after B01
  `common` package: domain exceptions (`NotFound`, `Conflict`, `Forbidden`) mapped to
  `ProblemDetail` by a `@RestControllerAdvice`, validation errors as 400 with field list,
  a `Clock` bean (tests control time), JSON config (timestamps ISO-8601, money as integer cents).
  Error `detail` texts are **Estonian** (the UI shows them), kept in one `Messages` class.
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
  `GET/POST/PUT /api/admin/members` (name, email, role, active, fee-exempt; email + password
  required for admins, email optional for members).
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

## Milestone 6 — Fees & invoices

- [ ] **B22 · Membership fee per level** (S) — after B04, B27
  `MembershipFee` entity (level, month, amount). `GET /api/admin/fees` → current fee per level
  + history. `POST /api/admin/fees` `{validFrom: "YYYY-MM", fees:[{levelId, amountCents ≥ 0}]}`:
  the month must be a future one, or the current month if it hasn't been charged yet; a level
  with a row for that month already → 409. `feeFor(level, month)` = latest `valid_from` ≤ month,
  else 0.
  Done when: history lookup per level, a level without fees, the month rules and €0 are tested.

- [ ] **B23 · Monthly charge job** (M) — after B10, B17, B22 (level fees)
  `MembershipChargeService.chargeMonth(month)`: for every active, non-exempt member created
  before the month and not yet charged, takes `feeFor(member.level, month)` (0 → skip),
  writes a `membership_charge` (with level) + one `MEMBERSHIP_FEE`
  entry via `AccountService.post` (`created_by` null), one transaction per member.
  `@Scheduled` daily at 06:00 (Europe/Tallinn) and once on startup for the current month.
  Unique `(member_id, month)` guards against a parallel run. History description:
  "Membership fee · October 2026".
  Done when: fixed-`Clock` tests cover the 1st, catching up later in the month, running twice
  (no double charge), different levels charged different amounts, exempt/inactive/new members
  skipped, fee 0 writes nothing, invariants hold.

- [ ] **B24 · Charges list and waive** (S) — after B23
  `GET /api/admin/fees/charges?month=YYYY-MM` (member, amount, status, totals).
  `POST /api/admin/fees/charges/{id}/waive` `{note}` (required) → `WAIVED` +
  `MEMBERSHIP_FEE_WAIVER` entry of +amount. Twice → 409.
  Done when: waive, double waive and the invariant are tested.

- [ ] **B25 · Email sending** (S) — after B02
  Add `spring-boot-starter-mail`. `Mailer` interface: SMTP implementation when
  `spring.mail.host` is set, otherwise a logging one (dev). `@ConfigurationProperties`
  `revkassa.invoice.*` (org-name, iban, from, reply-to, due-days = 14), validated on startup
  when SMTP is on.
  Done when: tests show the logging mailer is used without a host and the properties bind.

- [ ] **B26 · Invoices** (M) — after B14, B17, B25
  `Invoice` entity. `GET /api/admin/invoices/candidates`: members with balance < 0, email,
  last invoice date, `selectable` (has email). `GET /api/admin/invoices/preview?memberId=`.
  `POST /api/admin/invoices` `{memberIds}`: per member, own transaction: next number
  `YYYY-NNNN`, amount = −balance, due date, plain-text **Estonian** body (owed, entries since
  the previous invoice or last 60 days, IBAN, description "Arve <number>"; `et-EE` money and
  dates, Estonian month names), send, store `SENT` or `FAILED` + error;
  returns a result per member. Balance ≥ 0 or no email → skipped with a reason.
  `GET /api/admin/invoices?memberId=`, `POST /api/admin/invoices/{id}/resend` (`FAILED` only,
  same number). Balances never change.
  Done when: tests with a fake `Mailer` cover numbering across a year change, a failing
  address not stopping the rest, skip reasons, resend, and the email body.

## Milestone 7 — Events

- [ ] **B27 · Membership levels** (S) — after B17
  `MembershipLevel` entity. `GET/POST/PUT /api/admin/levels` (name unique ignoring case,
  sort order, active). Retiring keeps members on it; delete only when unused (409 otherwise).
  `member.level_id` in member admin; new members get the first active level.
  Done when: unique name, retire vs delete, and default level are tested.

- [ ] **B28 · Events** (M) — after B27
  `Event` + `EventLevelFee` entities. `GET/POST /api/admin/events`, `GET/PUT
  /api/admin/events/{id}` (detail with fees per level, participant count, budget totals),
  `PUT /api/admin/events/{id}/fees`. `CLOSED`/`CANCELLED` events are read-only (409).
  Done when: CRUD, fee table, status rules and optimistic locking are tested.

- [ ] **B29 · Participants and event fees** (M) — after B10, B28
  `POST /api/admin/events/{id}/participants {memberIds}`: per member copy level + fee, write
  `EVENT_FEE` entry via `AccountService.post` (none when 0); already active → skipped.
  `DELETE …/participants/{memberId}` → `REMOVED` + `EVENT_FEE_REVERSAL`. `POST …/cancel`
  reverses all; `POST …/close` locks. History description "Ürituse tasu · <event>".
  Done when: add/remove/re-add, fee 0, cancel, the participant invariant and the balance
  invariant are tested.

- [ ] **B30 · Event budget** (S) — after B28
  `EventBudgetLine` entity, CRUD under `/api/admin/events/{id}/budget-lines`; totals per
  category (planned, actual), fee income and result in the event detail.
  Done when: totals with missing actuals and the read-only states are tested.

- [ ] **B33 · Member sign-up** (S) — after B29
  `event.signup_open` (admin edit). `GET /api/events`: `PLANNED` events with sign-up open,
  plus events the member is in; each with own fee (by level), participant count, `signedUp`.
  `POST /api/events/{id}/signup`: only `PLANNED` + open (else 409), not already active (409);
  same service as the admin add, `added_by` = the member. No member endpoint to cancel.
  Done when: open/closed/cancelled, double sign-up, fee by level and that a member can't
  remove themselves (no route; admin DELETE still works) are tested.

## Milestone 8 — AI assistant (optional)

- [ ] **B31 · Assistant client** (M) — after B30
  Add `com.anthropic:anthropic-java`. `EventAssistant` interface; `AnthropicEventAssistant`
  when `ANTHROPIC_API_KEY` is set (model `revkassa.ai.model`, default `claude-sonnet-5-5`,
  max tokens and timeout from config), else `DisabledEventAssistant`.
  `GET /api/admin/assistant/status`.
  Done when: the right bean is chosen with/without a key; no key → status `{enabled: false}`.

- [ ] **B32 · Event assistant endpoint** (M) — after B31
  `POST /api/admin/events/{id}/assistant {kind, question}`. Prompt in Estonian built only
  from event name, date, participants per level, budget lines and the question — **no
  member names, emails or balances**. Asks for JSON `{answer, suggestedLines:[{category,
  description, plannedCents}]}`; invalid JSON → answer text only. 20 requests per admin per
  day (in memory), log who/when/tokens, never the prompt. Accepting a suggestion uses the
  normal budget-line POST with `source: AI`.
  Done when: tests with a fake assistant check the prompt contains no personal data, the
  JSON parsing fallback, the daily limit, and the disabled state.

## Milestone 9 — Release

- [ ] **B20 · Single jar** (S) — after the frontend exists
  Gradle task builds `frontend/` and copies `dist/` into the jar's static resources; unknown
  non-`/api` paths fall back to `index.html`.
  Done when: `./gradlew bootJar` + `java -jar` serves the UI and the API.

- [ ] **B21 · Run and back up** (S)
  Production profile (secrets from env incl. SMTP password and `ANTHROPIC_API_KEY`, secure cookies), systemd unit, nightly `pg_dump`
  with a restore check. Document in `docs/`.
