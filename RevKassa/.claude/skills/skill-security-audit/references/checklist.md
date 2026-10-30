# Security audit checklist

Generic checks that apply to any stack. Each check says why it matters, what to look for,
and when it is OK. Concrete commands and config keys per framework are in
`stacks/<stack>.md` under the same numbers.

Commands assume you are in the project root. Adjust paths to the project's layout.

Contents:

**A. Secrets and settings:** 1 API keys · 2 Environment variables · 3 Debug mode ·
4 Production settings

**B. Authentication and access:** 5 Authentication · 6 Session expiry · 7 Server-side
logout · 8 Password reset link · 9 Admin routes · 10 User permissions · 11 Own data only

**C. Input:** 12 Form validation · 13 Input sanitization · 14 SQL injection · 15 XSS ·
16 File upload

**D. Browser and network:** 17 HTTPS · 18 Cookies · 19 CSRF · 20 CORS · 21 Security
headers · 22 Rate limiting

**E. Dependencies and files:** 23 Dependency versions · 24 Unused packages · 25 Exposed
files

**F. Data:** 26 Database · 27 Password hashing · 28 Password policy · 29 Encryption

---

# A. Secrets and settings

## 1. API keys and secrets in code

**Look for** API keys, tokens, private keys and passwords in source code, config files and
**git history**. A key that was deleted later is still in the history.

```bash
# tracked files only
git grep -nIiE '(api[_-]?key|secret|token|passw(or)?d|private[_-]?key|client[_-]?secret)\s*[:=]' -- ':!*.lock' ':!package-lock.json' ':!yarn.lock' ':!pnpm-lock.yaml' ':!composer.lock' ':!poetry.lock'
# well-known key formats
git grep -nIE '(sk-ant-[A-Za-z0-9_-]{10,}|sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{20,}|AIza[0-9A-Za-z_-]{30,}|xox[abp]-[A-Za-z0-9-]+|sk_live_[A-Za-z0-9]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----)'
# git history, all branches
git log --all -p --no-color | grep -nE '(sk-ant-|AKIA[0-9A-Z]{16}|ghp_|github_pat_|AIza|sk_live_|-----BEGIN [A-Z ]*PRIVATE KEY)' | head -20
# gitleaks is more thorough, if installed
command -v gitleaks && gitleaks detect --no-banner --redact
```

**The frontend is public.** Everything in the browser bundle, including build-time
environment variables (`VITE_*`, `NEXT_PUBLIC_*`, `REACT_APP_*`), is visible to every
visitor. A third-party key (an AI API, a payment secret key) in the frontend is always a
leak, even if it comes from an `.env` file. Such calls must go through the backend.

**OK when:** there are no secrets in code or history, secrets come from environment
variables or a secret manager, and the frontend contains only public values.

## 2. Environment variables and leaks

**Check:**
- `.gitignore` covers `.env`, `.env.local`, `.env.*.local`, `*.pem`, `*.key`, and any
  local secrets file the project uses.
- `git ls-files | grep -iE '(^|/)\.env|\.pem$|\.key$|credentials|secret'` returns nothing.
  The exception is `.env.example`, which contains only placeholders.
- Config files read secrets from the environment. A development default is fine only if
  it really is just for development (a local database, `dev-secret`). Production values
  must not be in the repository.
- Does the app log or return its configuration? Look for environment values being
  logged, and for debug or management endpoints that print config.
- Frontend: only explicitly public variables go into the bundle. Check that the bundler
  config doesn't inject other variables (`define`, `DefinePlugin`, `env` in
  `next.config`).
- CI files (`.github/workflows/*`, `.gitlab-ci.yml`): secrets come from the CI secret
  store (`${{ secrets.X }}`), not written inline.
- Docker: no secrets in `Dockerfile` `ENV`/`ARG` or in `docker-compose.yml` committed to
  the repository (use `env_file` with an ignored file, or secrets).

**OK when:** secret files are not in git, `.env.example` documents the required variables,
and production secrets don't depend on defaults in the code.

## 3. Debug mode off

Debug mode is useful in development, but in production it leaks internal information (SQL,
stack traces, library versions) and sometimes opens extra endpoints (consoles, profilers).

**Check:**
- The framework's debug flag is off in production (see the stack file).
- SQL logging with parameters is only enabled in development. Parameters can contain
  personal data.
- Error responses don't include stack traces or exception details in production.
- Development tools (hot reload, debug toolbars, database consoles) are dev-only
  dependencies and are not reachable in production.
- The frontend in production is a production build, not the dev server.
- `console.log` with sensitive data (tokens, session data, personal data) is removed.
  A few harmless logs are not a problem.

**OK when:** debug output and development tools exist only in the development setup.

## 4. Production settings

Check whether the project has **separate production configuration** and whether it is
secure. It is very common for the development config to go to production unchanged.

**Check:**
- Are there environment profiles (dev/prod settings files, `NODE_ENV`, `APP_ENV`), and how
  is the profile selected? It should come from the environment, not be hard-coded.
- If there is no production config, note it. It must exist before the app goes to
  production. For a learning project that never goes to production, this is Low.
- The production setup must have:
  - secrets only from the environment, with no defaults (the app must not start with a
    development secret);
  - no automatic schema changes on startup;
  - debug off (check 3);
  - management and API documentation endpoints off or protected (check 25);
  - `Secure` cookies (check 18);
  - CORS only for production origins (check 20);
  - trusted proxy settings if the app runs behind a reverse proxy, so HTTPS and the client
    IP are detected correctly (checks 17 and 22);
  - log level `INFO` or `WARN`.
- Frontend: the API address comes from the build config or is relative (`/api`), not a
  hard-coded `http://localhost:…`.

**OK when:** production settings are separate and secure, and development defaults don't
reach production.

---

# B. Authentication and access

## 5. Authentication

**Check that it exists at all:**
- Is there an authentication mechanism (framework security module, auth library,
  middleware)? See the stack file for what to search for.
- Leftover framework defaults (a generated password, HTTP Basic, an example user) must not
  go to production.
- If authentication is missing, is it in the project plan? If yes, mark 🕓 and list what
  the task must include. If not, it is ⚠️, and the fix is a minimal authentication setup.
  That is a big change, so ask first which approach the user wants (cookie session or
  tokens, email or username).

**If authentication exists:**
- Every non-public endpoint requires authentication. Public routes are chosen deliberately
  and the list is short. The default for a new route is "authenticated", not "public".
- The login error doesn't reveal whether the account exists ("wrong email or password").
- Session fixation: the session id changes on login.
- Session expiry is a separate check (check 6).
- Tokens (JWT): the signing algorithm is fixed (never `none`, no algorithm taken from the
  token header), the key comes from the environment and is long enough, `exp` is short,
  and the token is verified on every request.
- Inactive or deleted users can't log in, and their existing sessions end.

**OK when:** authentication exists, everything except deliberately public routes requires
it, and the session or token is managed securely.

## 6. Login session expires

A session that never expires is as good as a permanent password: a forgotten login on a
shared computer or a stolen cookie works forever.

**Check:**
- **Idle timeout:** the session expires after inactivity. If the project has its own
  requirement (for example 1 minute of inactivity at a shared till), it must be enforced
  **on the server**, not only by a frontend timer. A frontend timer is a convenience: it
  logs out the UI, but the cookie stays valid if the server doesn't end the session.
- **Absolute lifetime:** even an active session should require logging in again after
  some time (for example 8–12 hours). In a small project this is Low if the idle timeout
  is short.
- **Different roles:** a session with higher privileges (admin) should expire faster, or
  at least not slower, than a regular user's session.
- **Tokens:** the access token has a short `exp` (minutes up to an hour) checked on every
  request. Refresh tokens also expire and can be revoked on the server.
- **"Remember me":** if it exists, its lifetime is limited, and it doesn't allow sensitive
  actions without logging in again.
- A request with an expired session returns **401**, and the frontend reacts to it
  (redirects to login) instead of silently breaking.
- Expiry and logout (check 7) also remove the session data on the server.

**Try it** (if the app can run): temporarily set a short timeout in development, or wait,
then make a request with the old cookie. The result must be 401.

**OK when:** the session has a server-enforced idle timeout (and if possible an absolute
lifetime) that matches the project's requirements.

## 7. Logout also ends the session on the server

A common mistake: the frontend deletes the token or session info from browser storage and
redirects to the home page, but the session is still alive on the server. Whoever got the
cookie or token can keep using it.

**Check:**
- Is there a logout endpoint on the server, and does it invalidate the session (or revoke
  the token) and clear the authentication context and the session cookie?
- Logout is a **POST** (with CSRF protection), not a GET. A GET can be triggered from
  another site with an image or a link.
- The frontend really calls that endpoint on logout and only then clears local state.
- Tokens: a stateless JWT can't be revoked. The solution is a short lifetime plus
  server-side revocation of refresh tokens, or a revocation list.
- Automatic logout after inactivity also happens on the server (check 6), not only via a
  frontend timer.

**Try it** (if the app can run): log in, save the cookie, log out, then repeat a request
with the same cookie:
```bash
curl -i -c jar.txt -X POST <login-url> ...   # log in
curl -i -b jar.txt <protected-url>           # 200
curl -i -b jar.txt -X POST <logout-url>      # log out (may need a CSRF token)
curl -i -b jar.txt <protected-url>           # must be 401
```

**OK when:** after logout, the old cookie or token no longer works.

## 8. Password reset link expires

Search for `reset|forgot|recover` in the backend and frontend. If there is no password
reset, mark ➖. If it is planned, mark 🕓 and list what the task must include (the list
below).

**If it exists:**
- **Expiry:** the token is valid for a short time (15–60 minutes). The expiry time is
  stored on the server (`expires_at`) and checked both when the link is opened **and**
  when the new password is saved.
- **Single use:** after successful use, the token is marked used or deleted. The same link
  can't change the password a second time.
- **A new request cancels old ones:** when the user requests a new link, previous links
  stop working.
- **Random and long:** the token comes from a cryptographically secure random generator
  (at least 32 bytes). It is not the user id, the email, a timestamp, or a shortened UUID.
- **Hashed in the database:** the token is stored as a hash (for example SHA-256). If the
  database leaks, the stored values can't be used to reset passwords.
- **Doesn't reveal accounts:** "If this email is registered, we sent a link." Same response
  and same timing for existing and non-existing emails.
- **Trustworthy link:** the link's base URL comes from config, not from the request's
  `Host` header. Otherwise an attacker can make the link point to their own domain (host
  header injection).
- **After the password change:** all existing sessions end and the user gets a
  notification.
- **Rate limiting:** requests per email and per IP are limited (check 22).
- The token doesn't end up in logs or in the `Referer` header sent to third parties (no
  external scripts on the reset page, `Referrer-Policy` set, check 21).
- The new password must meet the password policy (check 28).

**OK when:** the link expires quickly and works only once, the token is random and hashed
in the database, and reset doesn't reveal which accounts exist.

## 9. Admin routes are protected

**Check:**
- All admin routes (for example `/api/admin/**`, `/admin/*`) require the admin role
  **on the server**.
- Rule order matters: in most frameworks the first matching rule wins. A catch-all
  "permit all" before the admin rule opens everything.
- The last rule should be "authenticated" or "deny", not "permit all". Then a forgotten
  new endpoint is closed by default.
- Go through all routes or controllers. Is any admin action outside the admin prefix?
- If admin login is separate (for example a password for admins and a PIN for regular
  users), a regular user's session must not give access to admin routes.
- Frontend route guards are only a convenience, not protection. Protection must be on the
  server.

**Try it** (if the app can run): `curl -i <admin-route>` without a session must return 401
or 403, and with a regular user's session 403.

**OK when:** every admin route requires the admin role on the server and everything is
closed by default.

## 10. User permissions

Check 9 is about routes. This check is about whether the **permission model** itself is
sound.

**Check:**
- Where does the role come from? It must come from server-side data (database, session),
  not from the request, a header, a hidden form field, or an unsigned token field the
  client can change.
- Privilege escalation: can any endpoint let a user change their own role, an `isAdmin`
  flag, or another user's data? Look for update request objects with fields like `role`,
  `admin`, `active`, `status`.
- Can an admin remove the last admin's rights (then nobody can get in any more)?
- Method-level checks: if one endpoint serves several roles, does the service or handler
  check the permission (a permission annotation, policy, or a clear `if`)?
- When a role changes or a user is deactivated, does the existing session lose the rights
  (the role is re-read on the next request), or does the old role stay until the session
  ends?
- The UI hides buttons the user isn't allowed to use, but the backend also refuses those
  actions when the request is sent directly.

**OK when:** the role comes from the server, users can't raise their own rights, and every
privileged action is checked on the server.

## 11. Users can access only their own and public data

The most common mistake is IDOR: `GET /api/users/5/orders` works even when user 3 is
logged in.

**Check:**
- Endpoints with another user's id in the path or query (`{userId}`, `{orderId}`,
  `?accountId=`). Does the server check that the resource belongs to the logged-in user?
  A better pattern is to take "me" from the session (`/api/me/...`) instead of the path.
- Queries without an owner filter: "find by id" where it should be "find by id and owner".
- **Response objects:** do responses include fields the user shouldn't see
  (`passwordHash`, secrets, other users' emails, internal notes)? Database entities or
  models should never be returned directly; use explicit response objects or serializers
  with a field allowlist.
- **Mass assignment:** is a request body bound directly to an entity or model with fields
  like `role`, `balance`, `isAdmin`? Then users can set those themselves.
- Lists: does a regular user's list endpoint return everyone's data?
- Sequential ids make guessing easy. That is not a vulnerability by itself if access
  checks are correct, but it makes a missing check much worse.

**OK when:** every non-public query is filtered by the logged-in user, and responses
contain only the necessary fields.

---

# C. Input

## 12. Form validation

**Backend (this is the real protection):**
- Every request body and parameter is validated on the server: required fields, types,
  formats (email), ranges, allowed values.
- Text fields have a maximum length. Without one, someone can send a name of several
  megabytes.
- Money and quantities: negative or zero values, overflow, floating point used for money.
- Enum values: an unknown value returns 400, not 500.
- Validation error responses don't leak internals (stack trace, SQL).
- Request body size is limited.

**Frontend:** validation there is user experience, not security. Check that it exists, but
don't count it as protection.

**OK when:** every input is validated on the server and errors return a proper 400 without
internal details.

## 13. Input sanitization

Validation (check 12) decides whether input is allowed. Sanitization makes allowed input
safe where it goes next. Principle: **escape on output, for the context** the data goes
to (HTML, SQL, email header, CSV, log). "Cleaning" input on the way in is secondary and
often damages valid data (the name `O'Brien`).

**Check:**
- Text fields: trimming, removing control characters (`\u0000`–`\u001F`) from names and
  titles. Unicode normalization where uniqueness is compared (email, username); lowercase
  emails.
- **Email:** user input in the subject or headers without a `\r\n` check allows header
  injection. Use the mail library's API, not string concatenation.
- **CSV/Excel export:** a cell that starts with `=`, `+`, `-`, `@`, tab or CR runs as a
  formula in Excel. Prefix it with `'` or strip it.
- **Logs:** user input in logs without removing `\r\n` allows forged log lines.
- **File paths:** a user-supplied file name without cleaning allows `../` (check 16).
- **Shell commands:** user input passed to a shell (`exec`, `system`, `subprocess` with
  `shell=True`) allows command injection. Pass arguments as a list, never build a command
  string.
- **Rich text (HTML):** if users may enter HTML, clean it with an allowlist sanitizer
  (DOMPurify, Jsoup Safelist, bleach/nh3, HTML Purifier). Otherwise keep it as plain text
  and escape it on output.
- **URLs fetched by the server** (webhooks, link previews, image imports): validate the
  scheme and block internal addresses (SSRF).
- **Input sent to an AI model:** limit the length and don't let it override the system
  instructions (prompt injection). Treat the AI's answer as user input.

**OK when:** every input is cleaned or escaped for the place it goes to.

## 14. SQL injection

**Look for** string concatenation or formatting in queries (the stack file has concrete
patterns):
```bash
grep -rnE '"(SELECT|UPDATE|DELETE|INSERT)[^"]*" *\+' <backend-src>
grep -rnE '(SELECT|UPDATE|DELETE|INSERT|WHERE|ORDER BY).*(\$\{|%s|\{[a-z_]+\}|\+ *[a-zA-Z])' <backend-src>
```
- Correct is parameterized queries: named parameters (`:userId`), `?` placeholders, or the
  ORM's query builder with bound values.
- **`ORDER BY` and column names** can't be parameters. If the sort column comes from the
  user, it must pass an allowlist.
- `LIKE` search with `%` and `_` in user input is not injection, but can give unexpected
  results. Escape them if it matters.
- Raw SQL helpers in ORMs (`raw`, `whereRaw`, `extra`, `$queryRawUnsafe`, native queries)
  need special attention.
- The same rule applies to ORM query languages (JPQL, HQL) and NoSQL query objects built
  from user input (`{ $where: ... }`, operators like `$ne` passed straight from JSON).
- Schema and seed scripts are not dangerous at runtime, but if the app builds SQL
  dynamically, that is a high risk.

**OK when:** no query concatenates user input into a query string.

## 15. XSS protection

**Frontend:** most frameworks escape template output by default. The dangerous places are
the ones that render raw HTML (the stack file has the patterns: `v-html`,
`dangerouslySetInnerHTML`, `[innerHTML]`, `{@html}`, `innerHTML`, `document.write`,
`eval`).
- Raw HTML from user content (or from an AI response) is XSS. Is it sanitized (DOMPurify),
  or can it be replaced with normal interpolation?
- User-supplied links: allow only the `http:` and `https:` schemes (no `javascript:`).

**Backend:**
- Places that generate HTML (server templates, email templates) escape user content. Look
  for the template engine's "unescaped" syntax (see the stack file).
- JSON responses have `Content-Type: application/json`.
- The Content-Security-Policy header (check 21) is a second layer of defense.

**OK when:** there is no raw HTML rendering, or it uses sanitized content, and user content
never reaches HTML unescaped.

## 16. Secure file upload

Search for multipart or file upload handling. If there is no upload, mark ➖.

**If there is:**
- Size limit on the server.
- Allowed types checked by **content** (magic bytes), not only by the extension or the
  `Content-Type` sent by the client.
- The stored file name is generated by the server (UUID). The user's file name is kept
  only as metadata. This prevents `../` path traversal and overwriting.
- Files are stored outside the web root (or in object storage) and can't be executed.
- Downloads use `Content-Disposition: attachment` and the correct `Content-Type`, plus
  `X-Content-Type-Options: nosniff` (check 21).
- SVG and HTML files can contain scripts: block them or serve them as attachments.
- Images: consider re-encoding (removes metadata such as GPS location).
- Access: who may download the file (check 11)?
- Archives (zip): check paths when extracting (zip slip) and the size (zip bomb).

**OK when:** size and type are limited, file names are the server's, and files can't be
executed or read by other users.

---

# D. Browser and network

## 17. HTTPS

`http://localhost` is fine for local development. Check what happens in production.

- How does the app run in production? Reverse proxy (nginx, Caddy, Traefik), a platform
  (Render, Fly, Railway, Vercel, Heroku), or the app server directly with TLS? If unknown,
  mark ❔ and give recommendations.
- HTTP is redirected to HTTPS (at the proxy or in the app).
- `Strict-Transport-Security` (HSTS) is sent. Behind a proxy, the app must trust the
  forwarded headers to know the request was HTTPS.
- Hard-coded `http://` addresses in code:
  `grep -rnE "http://" <src dirs> | grep -vE 'localhost|127\.0\.0\.1|www\.w3\.org|xmlns'`.
- Mixed content: an HTTPS page doesn't load scripts, images or the API over HTTP.
- External API calls (AI, email, payments) use `https://`.
- Database and other internal connections use TLS in production when they cross a
  network (check 26).

**OK when:** in production everything goes over HTTPS and HTTP is redirected.

## 18. Secure cookies

- The session cookie is `HttpOnly` (JS can't read it), `Secure` (HTTPS only, in
  production), and `SameSite=Lax` or `Strict`.
- Custom cookies set by the app have the same flags.
- **Exception:** a CSRF token cookie that the frontend must read (for example
  `XSRF-TOKEN`) is deliberately not `HttpOnly`. That is by design, not a bug.
- Cookies don't contain sensitive data in plain text (role, user id without a signature).
- Tokens in `localStorage` are readable by any XSS. Prefer `HttpOnly` cookies. Keeping
  only UI state in `sessionStorage` (name, a balance for display) is fine.
- A "remember me" cookie is a random token stored hashed on the server, not the password or
  the user id.

**OK when:** the session cookie is `HttpOnly`, `Secure` (production) and `SameSite`, and
authentication tokens aren't readable by JS.

## 19. CSRF protection

CSRF protection is needed when the browser sends authentication automatically, that is,
with a **cookie-based session**. If the token is sent in the `Authorization` header and
not stored in a cookie, CSRF is not a problem.

**Check:**
- CSRF protection is disabled while using cookie sessions: High. This is often copied
  from tutorials.
- For an SPA: the server issues a CSRF token (usually a readable cookie) and the frontend
  sends it back in a header on POST/PUT/PATCH/DELETE. Check that the frontend really does
  this.
- State-changing actions are never GET requests (GET is not CSRF-protected).
- Exceptions (routes excluded from CSRF) are justified and the list is short. The login
  endpoint needs protection too (login CSRF).
- `SameSite=Lax` cookies help, but are not a full replacement for CSRF tokens.

**Try it:** `curl -i -X POST -b <session-cookie> <some-POST-route>` without the CSRF header
must return 403.

**OK when:** with cookie sessions, CSRF protection is on and the frontend sends the token.

## 20. CORS settings

- A wildcard origin (`*`) together with credentials is a mistake. Browsers block the
  literal combination, but "reflect any origin" or pattern `*` with credentials gets
  through and is dangerous.
- A CORS setting without parameters often allows all origins (see the stack file).
- The best option for a small project: frontend and backend on the same origin (a dev
  server proxy in development, the same domain or a reverse proxy in production). Then
  CORS isn't needed at all.
- If CORS is needed, use specific origins from config, not hard-coded in the code.

**OK when:** CORS is off (same origin) or allows only specific origins.

## 21. Security headers

Expected headers:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY` or CSP `frame-ancestors 'none'`
- `Strict-Transport-Security` (HTTPS only)
- `Content-Security-Policy` (for example `default-src 'self'; frame-ancestors 'none';
  object-src 'none'`). Dev servers with hot reload need a looser policy, so apply it in
  production or test it carefully.
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy` (for example `camera=(), microphone=(), geolocation=()`)
- `Cache-Control: no-store` on authenticated API responses.

Some frameworks add part of these by default (see the stack file). Check they haven't been
disabled. If the frontend is served separately (nginx, Netlify, Vercel), the headers must
be set there too.

**Try it:** `curl -sI <app-url>`

**OK when:** the basic headers are present and CSP exists (or is planned with a reason).

## 22. Rate limiting

**Where it is needed:** login (especially short numeric codes like PINs, where 6 digits
are only 10⁶ combinations), password reset, sending email, expensive external APIs (AI),
and public forms.

**Check:**
- Does login have an attempt counter and lockout (in the database or memory)? Is it tied
  to the account and/or the IP? Behind a proxy, the IP must come from a trusted forwarded
  header (check 4), otherwise every client has the same IP.
- Does the lockout response return 429 and say when to try again?
- Is there a library or an own implementation? For a small project, a simple counter in
  the database or in memory is often enough. Don't recommend heavy infrastructure if it
  isn't needed.
- AI and email endpoints have per-user or time-based limits.

**OK when:** brute-forceable and expensive endpoints are limited. If the project is so small
that it isn't needed, mark ➖ and explain why.

---

# E. Dependencies and files

## 23. Dependency versions and known vulnerabilities

- Run the ecosystem's audit tool (`npm audit`, `pip-audit`, `composer audit`, OWASP
  dependency-check or `gradle dependencyCheckAnalyze` if configured, `bundle audit`,
  `govulncheck`, `dotnet list package --vulnerable`). See the stack file.
- `high`/`critical` vulnerabilities in runtime dependencies are High. In dev-only
  dependencies they are usually Low (they don't reach production).
- Compare the framework version and manually pinned library versions with the latest
  stable release. A manually pinned version that overrides the framework's managed version
  is often older than needed.
- Don't add heavy scanning plugins during the audit without asking.
- If there is no network access, mark ❔ and give the user the commands to run.

**OK when:** there are no known high or critical vulnerabilities in runtime dependencies.

## 24. Unused packages

- For each declared runtime dependency, check whether it is imported or used anywhere.
  Tools: `npx depcheck`, `deptry` (Python), `composer-unused`; otherwise grep for the
  package's import name.
- Framework plugins and starters often work without imports (configured in config files).
  Check config and usage before calling them "unused".
- An unused package without vulnerabilities is Low, but it increases the attack surface.
  Exception: a package that adds endpoints or consoles (management endpoints, debug
  toolbars, database consoles) is Medium or High.
- Development tools are dev-only dependencies, not runtime ones.

**OK when:** every dependency is used and development tools don't reach production.

## 25. Exposed files

- Static and public folders (`public/`, `static/`, `resources/static/`, `wwwroot/`): is
  there anything that shouldn't be public (`.env`, backups, `.sql`, internal documents)?
- Production frontend build: source maps shouldn't be published if the source isn't
  public. Check whether build output (`dist/`, `build/`) is committed to git.
- **Management/monitoring endpoints** (Spring Actuator, debug toolbars, `/metrics`,
  `/phpinfo`, Django admin on the default path): only health is public; the rest is off
  or behind admin login.
- **API documentation** (Swagger/OpenAPI UI, GraphQL introspection/playground): fine in
  development. In production, turn it off or put it behind admin login.
- Directory listing and serving the `.git` folder (if there is an own web server).
- `git ls-files | grep -iE '\.(sql|dump|bak|log|csv|xlsx|sqlite|db)$'`: are production data
  or real personal data in the repository? Mock data is fine.

**OK when:** only the application's own files are served publicly.

---

# F. Data

## 26. Database security

SQL injection is check 14. This is the database setup itself.

- **The application's database user** is not a superuser (`postgres`, `root`, `sa`). It
  has only the rights it needs in its own schema and can't create databases or roles.
- A development database password is fine only on the local machine. In production there
  is a strong password from the environment.
- In production, the connection uses TLS (for example `sslmode=require`), and the database
  is not reachable from the public internet (only from the app server or a private
  network).
- No automatic schema changes on startup in production (ORM "create/update/sync" modes).
  Migrations run deliberately.
- Scripts that delete data (reset/drop/seed) are clearly marked as development-only.
- Database constraints (`not null`, `check`, `unique`, foreign keys) are the last line of
  defense against buggy code. Critical rules should be in the database too, not only in
  application code.
- Backups: is there a plan? Not a code question, but worth one line in the report.

**OK when:** the database user has minimal rights, the connection is encrypted in
production, and the schema doesn't change automatically on startup.

## 27. Password hashing

- Passwords are hashed with a slow, salted algorithm: Argon2id, bcrypt, scrypt, or PBKDF2
  with a high iteration count. Never MD5, SHA-1, plain SHA-256 without salt, reversible
  encryption, or plain text.
- Columns are named `password_hash`, not `password`. Seed data contains hashes, not plain
  text.
- **Short secrets (PINs, numeric codes):** hashing alone doesn't protect a 6-digit PIN,
  because 10⁶ combinations can be tried. If the code must be unique and looked up, an HMAC
  with a server-side secret (in the environment, not in the database) plus rate limiting
  (check 22) is reasonable.
- Passwords and PINs are never logged. Check request/body logging too.
- Password requirements are check 28.

**OK when:** passwords use a slow salted hash, short secrets use HMAC plus rate limiting,
and none of them are logged.

## 28. Password policy

A password must meet **all** of these rules:

| Rule | Example that fails |
|---|---|
| At least 12 characters | `Password1!` (10 characters) |
| At least one lower case letter | `PASSWORD1234!` |
| At least one upper case letter | `password1234!` |
| At least one number | `Passwordpassword!` |
| At least one special character (not a letter or digit) | `Password123456` |
| No more than 2 identical characters in a row | `Passsword123!` (`sss`), `Hello1111!abc` (`111`) |

It applies everywhere a password is created or changed: sign-up, users created by an
admin, password change and password reset (check 8). The rules don't apply to PINs or
numeric codes. Those are a separate mechanism protected by hashing (check 27) and attempt
limits (check 22).

**Check:**
- **The backend enforces the rules.** The frontend check only helps the user. Every request
  that sets a new password must validate it.
- Recommended: one reusable validator (a custom validation annotation, a validator
  function or class) used everywhere. Then the rule lives in one place, and each broken
  rule gets its own error message. A single regex also works:
  ```
  ^(?!.*(.)\1\1)(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{12,}$
  ```
  `(?!.*(.)\1\1)` forbids three identical characters in a row. The downside of a regex is
  that the user can't tell which rule failed. (Some regex engines, such as Go's RE2, don't
  support lookaheads or backreferences; implement the rules as code there.)
- **Upper limit:** bcrypt uses only the first 72 bytes, and some libraries throw an error
  for longer input. Set a maximum (for example 72 for bcrypt, or 128 with Argon2) and tell
  the user.
- Error messages name the failed rule ("Password must contain at least one number"), but
  never return or log the password itself.
- **Frontend** shows the rules before typing and marks which ones are met as the user
  types. The same rules must be in the frontend and the backend; keep them in one shared
  place if the project allows it.
- Optional extra checks: the new password differs from the old one, it doesn't contain the
  user's email or name, it isn't in a list of breached passwords.
- **Tests:** at least one failing test per rule (the table examples work) and one passing
  test (`Strong#Pass12`).
- **Existing passwords:** seed data or the database may contain passwords that don't meet
  the policy (for example a simple development password). In development mock data that
  is fine, but point it out. In production, force affected users to change their password
  at next login instead of just blocking login.

**OK when:** the backend rejects every password that breaks any rule in the table, the
frontend shows the same rules, and tests cover every rule.

## 29. Encryption of sensitive data

**What is sensitive:** national ID numbers, bank accounts (IBAN), card data, health data,
API tokens the app stores on behalf of users. Names and emails are personal data (GDPR),
but in a small project access control and TLS are usually enough for them.

Encryption in transit is check 17 and cookies are check 18. This check is data at rest.

**Check:**
- Are truly sensitive fields stored in plain text? If yes, use field-level encryption
  (AES-GCM through an ORM converter or library, or database functions like `pgcrypto`)
  with the key from the environment or a key manager. The key must not be in the code or
  in the same database.
- Does the sensitive data need to be stored at all? The safest field is the one that
  doesn't exist. Card data in particular should stay with the payment provider.
- Backups and exports are as sensitive as the database itself.
- Logs and error reports don't contain personal data or tokens.
- Only the minimum necessary data is sent to AI or other external services.

**OK when:** truly sensitive fields are encrypted or not stored at all, and the key is kept
separate from the data.
