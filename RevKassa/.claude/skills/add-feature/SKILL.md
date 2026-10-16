---
name: add-feature
description: Build a RevKassa feature end to end — JPA entity, repository, service, REST controller, tests and the Vue screen. Use when implementing anything from docs/PLAN.md (products, stock, purchases, payments, members, admin screens).
---

# Building a feature slice

Read `docs/PLAN.md` (rules, API sketch, milestones) and `docs/DB-MODEL.md` first. Check the
"RevKassa Mockups" canvas for the screen's intended layout when building UI.

## Backend layout (package by feature)
`src/main/java/com/example/RevKassa/<feature>/`
- `<Thing>.java` — JPA entity. Lombok `@Getter`/`@Setter` + protected no-arg constructor;
  no `@Data` on entities. `@Version` where the table has `version`. Enums `EnumType.STRING`.
- `<Thing>Repository.java` — Spring Data interface.
- `<Thing>Service.java` — business rules, `@Transactional` on writes. Controllers never
  touch repositories directly.
- `<Thing>Controller.java` — `@RestController`, paths from the API sketch (`/api/...`,
  admin under `/api/admin/...`). Request/response DTOs are Java `record`s with Bean
  Validation (`@NotNull`, `@Positive`, ...) and `@Valid`. Never return entities.
- Errors: throw domain exceptions; a `@RestControllerAdvice` in `common` maps them to
  `ProblemDetail` (400 validation, 404 not found, 409 business-rule conflict such as
  "undo window has passed").

## Non-negotiable domain rules
- Money is `long` cents end to end; format as euros only in the UI.
- Balance changes: write an `AccountEntry` **and** update `member.balanceCents` in the same
  transaction. Stock changes: write a `StockMovement` **and** update `product.stockQty`.
  Put each pair behind one service method (`AccountService.post(...)`,
  `StockService.move(...)`) and never bypass it.
- Ledger rows are never updated or deleted; corrections are new rows.
- Copy `unit_price_cents` onto purchase lines. One price list; guests buy on
  the hosting member's account (no guest accounts), and there is no tab limit.
- Auth: members log in with a unique 6-digit PIN only (stored as `pin_hmac`, never plain or
  logged); PIN login is rate-limited; PIN sessions expire after 1 minute idle on every device. `/api/admin/**` requires the separate email + password
  admin login — a PIN session never reaches admin endpoints.

## Tests
- Service logic: `@SpringBootTest` against the real Postgres (see `run-revkassa`), each test
  `@Transactional` so it rolls back. Assert the ledger invariants from DB-MODEL.md.
- Controller: `@WebMvcTest` with `MockMvc` for validation and status codes.

## Frontend (`frontend/src`)
- `views/member/<Screen>View.vue` and `views/admin/<Screen>View.vue`, routed in `router.js`
  (admin routes under `/admin`); shared bits in `components/`.
- API calls through one `api.js` wrapper (`fetch`, JSON, CSRF header, throws on non-2xx
  with the ProblemDetail `detail` message). No direct `fetch` in components.
- Member views (`views/member/`) are **mobile-first**: write base CSS for a ~390px phone,
  add `@media (min-width: 768px)` rules for the tablet; never the other way round. Single
  column, bottom tab bar, sticky bottom action bar, buttons ≥ 44px, `inputmode` on numeric
  inputs, no hover-only affordances. Check at 390px wide first.
- Admin views (`views/admin/`) are desktop-first but must not break at phone width (tables
  scroll in an `overflow-x: auto` box, toolbars wrap).
- Money via one `formatEuro(cents)` helper (`et-EE` locale, `€`).

## Done means
`./gradlew test` and `npm run build` pass, `docs/` updated if the API or model changed, and
the feature was exercised for real (see `run-revkassa`) — report what you ran.
Schema changes: follow `add-migration`.
