# RevKassa — frontend tasks

Vue 3 + Vite in `frontend/`. Screens follow the "RevKassa Mockups" canvas; API shapes come from
[PLAN.md](PLAN.md), backend work from [BACKEND-TASKS.md](BACKEND-TASKS.md) (`B..` below).
Size: S ≈ hours, M ≈ a day, L ≈ a few days.

Ground rules (see the `add-feature` skill):
- Member views in `src/views/member/` are **mobile-first** (base CSS for ~390px), with a
  tablet layout from `min-width: 1024px` for the 11" bar tablet (1920×1200 → ~1280×800 CSS px,
  landscape; see the "Member app — tablet" mockup). Admin views in `src/views/admin/` are desktop-first and
  must still work on a phone.
- Keep it simple: Vue Router, small composables for shared state (`useSession`, `useBasket`),
  no Pinia, no UI framework. All HTTP through `src/api.js`.
- Money arrives as integer cents; format only with `formatEuro()` (`27,40 €`).
- **All UI text is Estonian**, written directly in the components (`<html lang="et">`, no
  i18n library). Dates via one `formatDate()` (`16.10.2026`, `16. okt`). Mockups show the
  wording.
- Done when: `npm run build` and `npm test` pass, and the screen was checked at 390px wide,
  member screens also at 1280×800, admin screens at desktop width, against a running backend.

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
  Tablet (≥1024px): 4-column grid with the order panel always visible on the right
  (lines with −/+, total, balance after, Charge button) — F11's order step is skipped there.
  Done when: works one-handed at 390px (2 columns) and at 1280×800 (4 columns + panel).

- [ ] **F11 · Order, confirm, done, undo** (M) — needs B11, B12
  Order screen with −/+ per line, total, balance after, guest hint; "Charge €x to my balance"
  in the bottom bar. Done screen: amount, new balance, Buy more, Done (logs out), Undo
  (hidden after 10 min). Double-tap safe (button disabled while sending).

- [ ] **F12 · Customise favourites** (S) — needs B13
  Star/unstar, move up/down, "Done" saves via `PUT /api/me/favourites`.

## Milestone 4 — Account & payments

- [ ] **F13 · My account** (M) — needs B14, B15
  Balance card ("You owe" / "Your credit"), pending payments, history with "Load more".
  Tablet: balance and pay form on the left, pending and history on the right.

- [ ] **F14 · Pay or top up** (S) — needs B15
  Method toggle (bank transfer / card), amount in €, optional reference; confirmation
  message; appears under "Waiting for confirmation".

- [ ] **F15 · Admin: payments** (S) — needs B16
  Pending list with Confirm / Reject (optional note); badge count in the admin nav.

- [ ] **F16 · Admin: members** (M) — needs B17
  List with balance and status; email and fee-exempt fields; add member and reset PIN show the new PIN **once** in a
  dialog; adjust balance (amount + required reason); edit role/active (email + password when
  making an admin).

## Milestone 5 — Admin overview

- [ ] **F17 · Dashboard** (S) — needs B18
  Four figures (open tabs, to confirm, low stock, count it) linking to their screens;
  "Stock to check" and "Largest open tabs" lists.

- [ ] **F18 · Sales report** (S) — needs B19
  Month range picker and a table of units and revenue per product.

## Milestone 6 — Fees & invoices

- [ ] **F20 · Admin: membership fees** (S) — needs B22, B24
  Current fee per level and the history; "Muuda tasusid" (amount in € per level, from month,
  default next month).
  Month picker → list of charges (member, amount, Charged/Waived) with totals; Waive with a
  required note. Nav item "Fees".

- [ ] **F21 · Admin: send invoices** (M) — needs B26
  Members who owe: name, owes, email, last invoiced. Checkboxes, preselected = has email and
  not invoiced in 7 days; rows without email disabled with "add email" link to the member.
  "Preview" shows the email for one member. "Send n invoices" (confirm dialog, button disabled
  while sending) → result per member (Sent / Failed + reason, Resend). Invoice history on the
  member detail. Nav item "Invoices".
  Done when: works at desktop and phone width; tested against the logging mailer.

## Milestone 7 — Events

- [ ] **F22 · Membership levels** (S) — needs B27
  On Members: "Liikmetasemed" panel (rename, reorder, add, retire; member count per level);
  level select in member add/edit and a "Tase" column.

- [ ] **F23 · Events list and editor** (M) — needs B28
  Nav item "Üritused": cards (name, date, status, participants, fee income, budget result),
  "Uus üritus" form (name, date and time, place, description). Detail header with
  Close/Cancel (confirm dialog; cancel explains the fees are returned).

- [ ] **F24 · Fees and participants** (M) — needs B29
  Fee per level table (€ inputs, save; warning that earlier participants keep their fee).
  "Registreerimine avatud" switch. Participants list (name, level, fee, "ise" / "lisas admin",
  Remove) and "Lisa osalejaid": searchable member list
  with checkboxes, showing each member's fee before adding. Totals: count, fee income.

- [ ] **F25 · Event budget** (M) — needs B30
  Lines grouped by category (Joogid, Snäkid, Toit, Meelelahutus, Ruum, Kaunistus, Muu) with
  planned/actual inputs; add line; summary: costs planned/actual, fee income, result
  (red when negative). "AI" badge on accepted suggestions.

- [ ] **F27 · Member: events** (M) — needs B33
  Member tab "Üritused" (phone tab bar Osta · Üritused · Konto · Logi välja; tablet header):
  upcoming events with date, place, description, own fee; "Registreeru" → confirm sheet
  "Tasu X lisatakse sinu saldole" → "Oled kirjas". Signed-up events show "Oled kirjas" and
  "Loobumiseks võta ühendust adminiga" (no cancel button).

- [ ] **F28 · Events on the phone (admin)** (S) — with F23–F25
  Admin events screens work at 390px: list → detail with segments Osalejad · Tasud · Eelarve,
  sign-up switch, add/remove participants, budget lines with actual amounts, the assistant
  quick buttons. Mobile admin tab bar: Ülevaade · Maksed · Ladu · Liikmed · Tasud · Üritused.

## Milestone 8 — AI assistant (optional)

- [ ] **F26 · Assistant panel** (M) — needs B32
  Shown only when `GET /api/admin/assistant/status` says enabled. Side panel on the budget:
  quick buttons (Eelarve mustand · Kui palju osta · Retsept N inimesele), free question, answer
  text, suggested lines each with "Lisa eelarvesse". Note that only event data is sent and
  that suggestions must be checked. Loading and error states; button disabled while asking.

## Milestone 9 — Release

- [ ] **F19 · Production build** (S) — with B20
  Build output wired into the Gradle jar; correct base path; check deep links reload
  (`/account`, `/admin/members`) in the jar.
