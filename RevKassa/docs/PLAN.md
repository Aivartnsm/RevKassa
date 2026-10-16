# RevKassa — project plan

A small self-service bar till for one organization: members take a beer, record it
themselves, and it goes on their tab. Admins keep the stock and prices right and
confirm when members pay off their tab.

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

Out (for now): online payments, keg/draught volume tracking, multiple locations,
VAT/invoicing, email notifications, member self-registration.

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

## Architecture

- Backend: Spring Boot 4 (Java 21), Web MVC REST API under `/api`, Spring Data JPA,
  Flyway migrations, Bean Validation, Spring Security (session cookie + CSRF).
  Errors as RFC 9457 `ProblemDetail`.
- Package by feature under `com.example.RevKassa`: `member`, `product`, `stock`,
  `purchase`, `payment`, `account`, `security`, `common`.
- Database: PostgreSQL 16 in WSL (`localhost:5432`, db/user/password `revkassa`).
- Frontend: Vue 3 + Vite in `frontend/`, Vue Router, plain `fetch` wrapper. In dev Vite proxies
  `/api` to `:8080`. For release the built app is copied into Spring's static resources so
  the whole thing ships as one jar.
- **Member views are mobile-first**: designed and styled for a ~390px phone screen first,
  then scaled up with `min-width` media queries for the shared tablet. Single column, bottom
  tab bar (Buy · Account · Log out), the order total in a sticky bottom bar, touch targets
  ≥ 44px. Admin views are desktop-first but must still be usable on a phone.

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
| `GET/POST/PUT /api/admin/products` | admin | manage products and prices |
| `POST /api/admin/stock/deliveries` | admin | record a delivery (lines of product, qty, unit cost) |
| `POST /api/admin/stock/stocktake` | admin | counted quantities → corrections |
| `POST /api/admin/stock/write-offs` | admin | broken / expired |
| `GET /api/admin/payments?status=PENDING` | admin | payments to confirm |
| `POST /api/admin/payments/{id}/confirm`, `/reject` | admin | decide a payment |
| `GET/POST/PUT /api/admin/members` | admin | members and roles |
| `POST /api/admin/members/{id}/pin` | admin | set or reset a member's PIN |
| `POST /api/admin/members/{id}/adjustments` | admin | manual balance correction with note |
| `GET /api/admin/dashboard` | admin | overview figures |
| `GET /api/admin/reports/sales?from=&to=` | admin | units and revenue per product per month |

## Screens

Member: PIN keypad · Buy (favourites first, then the rest) · Customise buy screen ·
Order · Purchase done (with Undo) · My account
(balance, history, report payment).
Admin: Admin login (email + password) · Dashboard (low/negative stock, pending payments, top debtors) · Products & stock ·
Record delivery · Stocktake · Payments to confirm · Members.

## Milestones

1. **Foundation** — V1 migration (full schema), security + login, seeded admin, Vue router
   and layout, API client, error handling.
2. **Products & stock** — admin product CRUD, deliveries, stock ledger, stocktake, write-offs.
3. **Buying** — buy screen with personal favourites, purchase service (balance + stock in one
   transaction), undo.
4. **Account & payments** — member history, report payment, admin confirm/reject, adjustments.
5. **Admin overview** — dashboard, members management, simple reports (sales per product/month).
6. **Release** — bundle frontend into the jar, run as a service, DB backups.

## Open questions

None right now.
