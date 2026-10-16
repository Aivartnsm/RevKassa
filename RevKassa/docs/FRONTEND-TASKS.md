# RevKassa — frontend tasks

Vue 3 + Vite in `frontend/`. Screens follow the "RevKassa Mockups" canvas; API shapes come from
[PLAN.md](PLAN.md), backend work from [BACKEND-TASKS.md](BACKEND-TASKS.md) (`B..` below).
Size: S ≈ hours, M ≈ a day, L ≈ a few days.

Ground rules (see the `add-feature` skill):
- Member views in `src/views/member/` are **mobile-first** (base CSS for ~390px, `min-width`
  media queries for the tablet). Admin views in `src/views/admin/` are desktop-first and
  must still work on a phone.
- Keep it simple: Vue Router, small composables for shared state (`useSession`, `useBasket`),
  no Pinia, no UI framework. All HTTP through `src/api.js`.
- Money arrives as integer cents; format only with `formatEuro()`.
- Done when: `npm run build` and `npm test` pass, and the screen was checked at 390px wide
  (and at desktop width for admin) against a running backend.

## Milestone 1 — Foundation

- [ ] **F01 · Clean scaffold and look** (S)
  Remove the Vite demo (`HelloWorld.vue`, demo assets). Global CSS: colour tokens from the
  mockups (ground `#121A17`, surfaces `#1C2622`/`#26332E`, accent `#F2B33D`, credit/owe
  colours), Barlow + Barlow Condensed, 44px minimum touch targets, focus styles.
  Add Vitest + Vue Test Utils (`npm test`).
  Done when: an empty app builds and renders the base styles at 390px.

- [ ] **F02 · API client and helpers** (S)
  `api.js`: `fetch` wrapper with JSON, `credentials: 'same-origin'`, CSRF header from the
  cookie, ProblemDetail → thrown error with `detail` message, 401 → session expired event.
  `formatEuro(cents)` (`et-EE`, `€`, minus sign for negatives).
  Done when: unit tests cover errors, CSRF header and formatting.

- [ ] **F03 · Router, session and layouts** (M) — needs B04
  Routes: `/` PIN, `/buy`, `/buy/customise`, `/order`, `/done`, `/account`,
  `/admin/login`, `/admin/...`. `useSession` loads `GET /api/me`. Guards: member routes need a
  member session, `/admin/**` an admin session. `MemberLayout` (header with name + balance,
  bottom tab bar Buy · Account · Log out); `AdminLayout` (top nav on desktop, bottom tabs on
  phone, "Log out").
  Done when: guards redirect correctly and both layouts work at both widths.

- [ ] **F04 · PIN keypad** (M) — needs B04, B05
  Six dots, 3×4 keypad (C, 0, ⌫), auto-submit at 6 digits, wrong-PIN message, lockout
  message with countdown, keyboard digits also work. Success → `/buy`.
  Done when: tested against the backend, including the lockout.

- [ ] **F05 · Idle logout** (S) — after F03
  `useIdleLogout(ms)`: resets on pointer/key/scroll; on expiry calls logout and returns to the
  PIN screen (member, **1 min**) or admin login (admin, **30 min**). A 401 from the server
  does the same.
  Done when: unit test with fake timers; manual check that the basket is cleared.

- [ ] **F06 · Admin login** (S) — needs B04
  Email + password form, error message, note that the PIN is not enough. Success → `/admin`.

## Milestone 2 — Admin: products & stock

- [ ] **F07 · Products** (M) — needs B07
  List with price, stock, status pill (OK / Low / Count it / Hidden) and show/hide toggle;
  create/edit form (name, style, size, price in €, low-stock level, sort order).
  Desktop table, phone list → detail.

- [ ] **F08 · Record delivery** (S) — needs B08
  Desktop: one row per product with units and optional unit cost. Phone: product detail with
  −/+ and +6 / +20 / +24 shortcuts. Shows the resulting stock.

- [ ] **F09 · Stocktake and write-off** (S) — needs B09
  Counted-quantity inputs with live difference; empty rows skipped; write-off with note.

## Milestone 3 — Buying (member, mobile-first)

- [ ] **F10 · Buy screen** (M) — needs B07, B13
  "Your favourites" grid first (member's order), then "Everything else"; tap a tile to add,
  quantity badge, "Last few" hint. `useBasket` (cleared on logout). Sticky bottom bar
  "View order · n items · €x".
  Done when: works one-handed at 390px; 2 columns on phone, more on tablet.

- [ ] **F11 · Order, confirm, done, undo** (M) — needs B11, B12
  Order screen with −/+ per line, total, balance after, guest hint; "Charge €x to my balance"
  in the bottom bar. Done screen: amount, new balance, Buy more, Done (logs out), Undo
  (hidden after 10 min). Double-tap safe (button disabled while sending).

- [ ] **F12 · Customise favourites** (S) — needs B13
  Star/unstar, move up/down, "Done" saves via `PUT /api/me/favourites`.

## Milestone 4 — Account & payments

- [ ] **F13 · My account** (M) — needs B14, B15
  Balance card ("You owe" / "Your credit"), pending payments, history with "Load more".

- [ ] **F14 · Pay or top up** (S) — needs B15
  Method toggle (bank transfer / card), amount in €, optional reference; confirmation
  message; appears under "Waiting for confirmation".

- [ ] **F15 · Admin: payments** (S) — needs B16
  Pending list with Confirm / Reject (optional note); badge count in the admin nav.

- [ ] **F16 · Admin: members** (M) — needs B17
  List with balance and status; add member and reset PIN show the new PIN **once** in a
  dialog; adjust balance (amount + required reason); edit role/active (email + password when
  making an admin).

## Milestone 5 — Admin overview

- [ ] **F17 · Dashboard** (S) — needs B18
  Four figures (open tabs, to confirm, low stock, count it) linking to their screens;
  "Stock to check" and "Largest open tabs" lists.

- [ ] **F18 · Sales report** (S) — needs B19
  Month range picker and a table of units and revenue per product.

## Milestone 6 — Release

- [ ] **F19 · Production build** (S) — with B20
  Build output wired into the Gradle jar; correct base path; check deep links reload
  (`/account`, `/admin/members`) in the jar.
