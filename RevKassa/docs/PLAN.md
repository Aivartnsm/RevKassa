# RevKassa — project plan

A small self-service bar till for one organization: members take a beer, record it
themselves, and it goes on their tab. Admins keep the stock and prices right and
confirm when members pay off their tab. The monthly membership fee goes on the same
balance, and admins can email payment requests to members who owe money. Admins also
organize events: participants, a participation fee per membership level, and the event
budget, with an optional AI assistant for budgeting and recipes.

The app is in **Estonian** (UI and emails). Code, API fields, database tables and these
docs are in English.

DB model: [DB-MODEL.md](DB-MODEL.md). Tasks: [backend](BACKEND-TASKS.md), [frontend](FRONTEND-TASKS.md). Mockups: the "RevKassa Mockups" design canvas.

## Scope (v1)

In:
- Member login with a **PIN only** (no name or email to pick); roles `MEMBER` and `ADMIN`
  (an admin is also a member). Admin pages additionally need email + password.
- Self-service buying: pick beers, confirm, the total is charged to the member's balance.
- Personal buy screen: each member stars favourite drinks and orders them; favourites are
  shown first, everything else below.
- Member balance: positive = prepaid credit, negative = open tab. There is **no tab limit**:
  admins follow up on large tabs from the dashboard.
- Paying by card or bank transfer — to pay off the tab or to **top up in advance**. The app does **not** process payments: the
  member reports a payment, an admin confirms it against the bank statement / card terminal.
- Stock per product, kept as a ledger: deliveries, sales, stocktake corrections, write-offs.
- Admin: products and prices, deliveries, stocktake, pending payments, members, balances.
- Guests have no accounts: a member buys for their guests on their own account and is
  responsible for paying it. One price list for everyone.
- **Monthly membership fee** by membership level (e.g. Noorliige pays less), changed by an
  admin (history kept), charged to every member's balance automatically each month. Admins see who was charged
  per month and can waive a charge.
- **Invoice emails**: from the admin panel, send a payment request by email to members with
  a negative balance. Every email sent is recorded.
- **Membership levels**: every member has a level (5 to start with, e.g. Täisliige,
  Noorliige, Vilistlane, Toetajaliige, Auliige). Admins rename, add, reorder and retire
  levels; the number isn't fixed.
- **Event manager** (admin): create an event, set a participation fee per level, open it for
  sign-up; members **sign up themselves** in the app or an admin adds them (the fee goes on
  their balance); only an admin can remove a participant; and plan the event budget by category
  (drinks, snacks, food, entertainment, venue, decoration, other) with planned and actual
  amounts against the fee income.
- **AI assistant (optional)**: in the event budget, an admin can ask an LLM for help: a
  budget draft, quantities to buy, recipes scaled to the head count. Suggestions are only
  added to the budget when the admin accepts them. Off unless an API key is configured.

Out (for now): online payments, keg/draught volume tracking, multiple locations,
VAT invoices / bookkeeping export, other email notifications (receipts, reminders sent
automatically), member self-registration (as members of the organization), members
cancelling their own event sign-up, event ticketing/payments, AI anywhere except the event assistant.

## Rules

Login
- Members log in by typing their PIN on a keypad. The PIN alone identifies the member, so
  PINs are **unique**, exactly **6 digits**, and chosen/reset by an admin (members can change
  their own later). PINs are stored as an HMAC-SHA256 with a server-side secret, so the
  login can look the member up by it without storing the PIN itself.
- Brute-force protection: after 5 wrong PINs from the same device/IP, the keypad locks for
  1 minute (doubling on repeat). Successful logins and lockouts are logged.
- A PIN session ends on "Log out" / "Done, log me out" or after **1 minute idle**, on every
  device (shared tablet and personal phones alike). Enforced on the server (session timeout)
  and mirrored in the UI, which returns to the PIN keypad.
- Admin pages need a second login with email + password (bcrypt), session idle timeout
  30 minutes. A PIN session alone never grants admin access.

Money
- All amounts are integer **euro cents** (`long` in Java, `bigint` in SQL). Never `double`.
- Balance changes only through an `account_entry` row, written in the same transaction as
  the cached `member.balance_cents` update.
- The unit price is copied onto each purchase line, so later price changes don't rewrite history.

Purchases
- A member may undo their own purchase within 10 minutes; an admin may reverse any purchase.
- A reversal never deletes rows: it marks the purchase `REVERSED` and writes compensating
  `account_entry` and `stock_movement` rows.

Payments
- Members can top up before buying; a positive balance is credit and purchases draw it down.
- Member reports amount, method (`CARD` / `BANK_TRANSFER`) and an optional reference →
  `PENDING`. Only `CONFIRMED` payments change the balance. Admins can also record a payment
  directly as confirmed.

Stock
- `product.stock_qty` is a cache of the sum of its `stock_movement.quantity_delta`.
- Sales are not blocked by stock reaching 0 — the fridge is the truth. Negative stock is
  shown to admins as "needs stocktake".
- Stocktake: admin enters the counted quantity; the app writes a `STOCKTAKE` movement for
  the difference.

Membership fee
- The fee depends on the member's **level**: one amount per level, stored with the month it
  applies from (`membership_fee`: level + month + amount). An admin changes the fees by saving
  new amounts for one or more levels from a month onwards; old months keep their old amounts.
  A level's fee of €0 means that level isn't charged (e.g. Auliige). A new level is free until
  a fee is set for it.
- The fee goes onto the member's balance like a purchase. "Paid" therefore means the balance
  is not negative; unpaid fees show up as an open tab and are covered by invoice emails.
- On the 1st of each month the app charges every member who is **active, not fee-exempt** and
  joined before that month the fee of **their level on that day** (level and amount are copied
  onto the charge). A level change takes effect from the next charge. The job also runs daily and on startup and
  only fills in missing charges, so a server that was down on the 1st catches up. A unique
  `(member, month)` row makes double charging impossible.
- Admins can mark a member fee-exempt (e.g. honorary members) and **waive** a single charge
  with a note, which writes a compensating entry. A waived charge cannot be waived again.
- Members see "Membership fee · October 2026" in their balance history.

Invoice emails
- Members get an optional `email` (required for admins as before). Members without an email
  are listed but can't be selected.
- The admin "Invoices" screen lists members with a negative balance, with their email and
  when they were last invoiced. The admin ticks members (default: all with an email who were
  not invoiced in the last 7 days), previews one email, and sends.
- An invoice is a payment request, not a VAT invoice, and **does not change the balance**.
  It has a number (`2026-0001`, per year), the amount owed when sent, the balance entries
  since the previous invoice (at most the last 60 days), a due date (14 days) and payment
  instructions: organization name, IBAN, and "Arve 2026-0001" as the payment description
  (*selgitus*; not an Estonian *viitenumber*).
- Emails are in **Estonian**, with Estonian formats (`27,40 €`, `30.10.2026`, "Liikmemaks ·
  oktoober 2026"). The member still reports the payment in the app, and an admin confirms
  it as usual.
- Plain-text email over SMTP. Each email is sent and recorded on its own, so one bad
  address doesn't stop the others; failed ones show the error and can be resent.
- Settings from configuration, not the database: `revkassa.invoice.org-name`, `.iban`,
  `.from`, `.reply-to`, `.due-days`, plus the standard `spring.mail.*` SMTP settings (secrets
  from environment variables). Without `spring.mail.host` (dev) emails are written to the
  log instead of sent.

Language
- All UI text is Estonian, written directly in the Vue components (no i18n library; one
  language). Money `27,40 €`, dates `16.10.2026`, `et-EE` formatting everywhere.
- API error messages (`ProblemDetail.detail`) are Estonian because the UI shows them.
  Enum values, field names, logs and code stay English.

Membership levels
- `membership_level` rows: name, order, active. Seeded with 5 levels in the first migration;
  admins manage them on the Members screen. A level in use can be retired (hidden from new
  choices) but not deleted. Every member has exactly one level; new members get the first one.
- Levels set both the monthly membership fee and the event participation fees.

Events
- An event has a name, start time, place, description and a status `PLANNED` → `CLOSED`
  (after it happened; no more changes) or `CANCELLED`.
- Participation fee: one amount per level for that event (€0 allowed). Adding a participant
  copies their current level and that level's fee onto the participant row and charges it to
  their balance (`EVENT_FEE` entry). Later fee changes only apply to participants added
  afterwards; the screen warns about that.
- Removing a participant before the event is closed writes an `EVENT_FEE_REVERSAL` entry.
  Cancelling an event reverses every remaining participant's fee. Nothing is deleted.
- Sign-up: an event has a **sign-up open** switch. While the event is `PLANNED` and sign-up is
  open, members see it under "Üritused" in the app with their own fee and can sign up with one
  confirmation ("Tasu 15,00 € lisatakse sinu saldole"). Signing up works like being added:
  level and fee copied, `EVENT_FEE` entry. There is no balance limit, as elsewhere.
- Members **cannot cancel** their sign-up: the app says to contact an admin. Only admins remove
  participants (fee reversed). Admins can still add members after sign-up has closed.
- The participant list shows who signed up themselves and who was added by an admin.
  Members see "Ürituse tasu · <event>" in their history. Guests of a member are covered by
  the budget, not as participants.

Event budget
- Budget lines: category, description, planned amount, actual amount (filled in after
  buying), optional note, and whether it came from the AI assistant.
- The budget view shows planned and actual totals per category, the fee income (sum of
  participants' fees) and the result (income − costs). It is a planning sheet: budget lines
  never touch balances or stock.

AI assistant (optional)
- Calls the Anthropic Messages API from the backend (Anthropic Java SDK), model from
  configuration (`revkassa.ai.model`, default `claude-sonnet-5-5`). Enabled only when
  `ANTHROPIC_API_KEY` is set; otherwise the UI hides the assistant.
- Admin-only, on one event at a time. Quick actions: "Budget draft", "How much to buy",
  "Recipe for N people", plus a free question. Answers are in Estonian.
- The request contains only event data: name, date, number of participants per level, the
  current budget lines and the admin's question. **Never member names, emails or balances.**
- Answers come back as text plus suggested budget lines (structured JSON). Each suggestion
  has "Lisa eelarvesse"; nothing is added automatically. Accepted lines are marked as AI.
- Limits: 20 requests per admin per day, max output size capped, timeouts and errors shown as
  "Abiline ei vastanud" without breaking the page. Prompts and answers are not stored; the
  log records only who asked, when and token counts.

## Architecture

- Backend: Spring Boot 4 (Java 21), Web MVC REST API under `/api`, Spring Data JPA,
  Flyway migrations, Bean Validation, Spring Security (session cookie + CSRF).
  Errors as RFC 9457 `ProblemDetail`.
- Package by feature under `com.example.RevKassa`: `member`, `product`, `stock`,
  `purchase`, `payment`, `account`, `fee`, `invoice`, `level`, `event`, `assistant`,
  `security`, `common`.
- Email: `spring-boot-starter-mail`; the monthly fee job uses `@Scheduled` (single instance,
  no extra scheduler library).
- AI: Anthropic Java SDK (`com.anthropic:anthropic-java`) behind an `EventAssistant`
  interface, with a disabled implementation when no key is set and a fake one in tests.
- Database: PostgreSQL 16 in WSL (`localhost:5432`, db/user/password `revkassa`).
- Frontend: Vue 3 + Vite in `frontend/`, Vue Router, plain `fetch` wrapper. In dev Vite proxies
  `/api` to `:8080`. For release the built app is copied into Spring's static resources so
  the whole thing ships as one jar.
- **Member views are mobile-first**: designed and styled for a ~390px phone screen first,
  then scaled up with `min-width` media queries. Phone: single column, bottom tab bar
  (Buy · Account · Log out), the order total in a sticky bottom bar, touch targets ≥ 44px.
- **Shared bar tablet**: 11" 1920×1200 in landscape, which browsers show as about
  **1280×800 CSS px** (1.5× scaling). From `min-width: 1024px` the member views switch to a
  tablet layout: header with name, balance and Buy · Account · Log out; buy screen with a
  4-column drink grid and the order always visible in a right-hand panel (no separate order
  step); account in two columns; larger keypad and touch targets (≥ 52px).
- Admin views are desktop-first but must still be usable on a phone.

## API sketch

| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/auth/pin` | all | `{pin}` → member session (rate-limited) |
| `POST /api/auth/admin` | admin | `{email, password}` → admin session |
| `POST /api/auth/logout` | all | end session |
| `GET /api/me` | member | profile and balance |
| `GET /api/products` | member | active products with price and stock |
| `POST /api/purchases` | member | `{lines:[{productId, quantity}]}` → purchase |
| `POST /api/purchases/{id}/undo` | member (own, ≤10 min) / admin | reverse a purchase |
| `GET /api/me/entries` | member | balance history |
| `GET /api/me/favourites` | member | favourite product ids, in order |
| `PUT /api/me/favourites` | member | `{productIds:[...]}` — replaces the ordered list |
| `POST /api/payments` | member | report a payment |
| `GET /api/me/payments?status=PENDING` | member | own payments waiting for confirmation |
| `GET /api/events` | member | upcoming events open for sign-up, with own fee and whether signed up |
| `POST /api/events/{id}/signup` | member | sign up; charges the own-level fee (no cancel endpoint) |
| `GET/POST/PUT /api/admin/products` | admin | manage products and prices |
| `POST /api/admin/stock/deliveries` | admin | record a delivery (lines of product, qty, unit cost) |
| `POST /api/admin/stock/stocktake` | admin | counted quantities → corrections |
| `POST /api/admin/stock/write-offs` | admin | broken / expired |
| `GET /api/admin/payments?status=PENDING` | admin | payments to confirm |
| `POST /api/admin/payments/{id}/confirm`, `/reject` | admin | decide a payment |
| `GET/POST/PUT /api/admin/members` | admin | members and roles |
| `POST /api/admin/members/{id}/pin` | admin | set or reset a member's PIN |
| `POST /api/admin/members/{id}/adjustments` | admin | manual balance correction with note |
| `GET /api/admin/fees` | admin | current fee per level and the history |
| `POST /api/admin/fees` | admin | `{validFrom: "2026-11", fees:[{levelId, amountCents}]}` — new fees from a month |
| `GET /api/admin/fees/charges?month=2026-10` | admin | who was charged that month, waived ones |
| `POST /api/admin/fees/charges/{id}/waive` | admin | `{note}` → compensating entry |
| `GET /api/admin/invoices/candidates` | admin | members with a negative balance, email, last invoiced |
| `GET /api/admin/invoices/preview?memberId=` | admin | subject and body as it would be sent |
| `POST /api/admin/invoices` | admin | `{memberIds:[...]}` → sends, result per member |
| `GET /api/admin/invoices?memberId=` | admin | sent invoices, newest first |
| `POST /api/admin/invoices/{id}/resend` | admin | retry a failed email |
| `GET/POST/PUT /api/admin/levels` | admin | membership levels (name, order, active) |
| `GET/POST /api/admin/events` | admin | events list / create |
| `GET/PUT /api/admin/events/{id}` | admin | event detail with fees, participants, budget totals / edit (incl. `signupOpen`) |
| `POST /api/admin/events/{id}/close`, `/cancel` | admin | close, or cancel and reverse fees |
| `PUT /api/admin/events/{id}/fees` | admin | `{fees:[{levelId, amountCents}]}` |
| `POST /api/admin/events/{id}/participants` | admin | `{memberIds:[...]}` → charges fees |
| `DELETE /api/admin/events/{id}/participants/{memberId}` | admin | remove, reverses the fee |
| `GET/POST/PUT/DELETE /api/admin/events/{id}/budget-lines` | admin | budget lines |
| `GET /api/admin/assistant/status` | admin | `{enabled}` |
| `POST /api/admin/events/{id}/assistant` | admin | `{kind: BUDGET\|QUANTITIES\|RECIPE\|QUESTION, question}` → `{answer, suggestedLines}` |
| `GET /api/admin/dashboard` | admin | overview figures |
| `GET /api/admin/reports/sales?from=&to=` | admin | units and revenue per product per month |

## Screens

Member: PIN keypad · Buy (favourites first, then the rest) · Customise buy screen ·
Order · Purchase done (with Undo) · Events (upcoming, sign up) · My account
(balance, history, report payment). Phone tab bar: Osta · Üritused · Konto · Logi välja.
Admin: Admin login (email + password) · Dashboard (low/negative stock, pending payments, top debtors) · Products & stock ·
Record delivery · Stocktake · Payments to confirm · Members (incl. email, fee-exempt) ·
Membership fees (amount per level, monthly charges, waive) · Invoices (pick members, preview, send) ·
Membership levels (on Members) · Events (list, detail with participants, fees per level,
budget, AI assistant panel).

## Milestones

1. **Foundation** — V1 migration (full schema), security + login, seeded admin, Vue router
   and layout, API client, error handling.
2. **Products & stock** — admin product CRUD, deliveries, stock ledger, stocktake, write-offs.
3. **Buying** — buy screen with personal favourites, purchase service (balance + stock in one
   transaction), undo.
4. **Account & payments** — member history, report payment, admin confirm/reject, adjustments.
5. **Admin overview** — dashboard, members management, simple reports (sales per product/month).
6. **Fees & invoices** — membership fee and monthly charge job, waivers, email sending,
   invoice screen.
7. **Events** — membership levels, events with per-level fees, participants, budget.
8. **AI assistant** (optional) — event budgeting/recipe assistant behind a feature switch.
9. **Release** — bundle frontend into the jar, run as a service, DB backups, SMTP and AI keys.

## Open questions

None right now.
