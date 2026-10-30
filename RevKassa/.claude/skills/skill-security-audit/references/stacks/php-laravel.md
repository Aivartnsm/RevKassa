# PHP (Laravel)

Concrete checks for the numbered items in `../checklist.md`. For plain PHP or other
frameworks (Symfony), apply the same ideas.

```bash
EXCL='--exclude-dir=vendor --exclude-dir=node_modules --exclude-dir=storage'
```

## 1–2. Secrets and environment variables
- `.env` in `.gitignore`; `.env.example` committed. `APP_KEY` never committed.
- `env()` only inside `config/*.php` (it returns null when config is cached):
  `grep -rn "env(" $EXCL app routes`.

## 3. Debug mode
- `APP_DEBUG=false` in production (Ignition/error pages show env and code otherwise).
- `barryvdh/laravel-debugbar`, Telescope only in `require-dev` or protected.
- `DB::enableQueryLog()`/`DB::listen` logging only in development.

## 4. Production settings
- `APP_ENV=production`, `php artisan config:cache`, `route:cache`.
- `TrustProxies` middleware configured with the real proxy, not `*` unless behind a
  platform that requires it.
- No `migrate:fresh` / `db:seed` in the production deploy.

## 5. Authentication
- Breeze/Jetstream/Fortify/Sanctum/Passport. Routes inside `Route::middleware('auth')`
  (or `auth:sanctum`) groups.
- `Auth::login()` regenerates the session; check custom login code calls
  `$request->session()->regenerate()`.

## 6. Session expiry
- `config/session.php` `lifetime` (minutes) and `expire_on_close`; driver `database` or
  `redis` in production.
- Sanctum tokens: `expiration` in `config/sanctum.php` (default null = never).

## 7. Server-side logout
- `Auth::logout(); $request->session()->invalidate(); $request->session()->regenerateToken();`
- Sanctum: `$request->user()->currentAccessToken()->delete()`.

## 8. Password reset
- Built-in password broker: `config/auth.php` `passwords.*.expire` (default 60 minutes)
  and `throttle`. Tokens are stored hashed.
- `APP_URL` used for links; check custom notification code doesn't use `request()->getHost()`.

## 9–10. Admin routes and permissions
```bash
php artisan route:list --columns=method,uri,middleware 2>/dev/null | head -80
grep -rnE "Gate::|->can\(|@can|authorize\(|Policy" $EXCL app routes resources
```
- Admin routes in a group with `auth` + role middleware or `can:` gates.
- Policies for model actions.

## 11. Own data only
- Route model binding (`Order $order`) without a policy check = IDOR. Use
  `$this->authorize('view', $order)` or scoped queries (`$request->user()->orders()->findOrFail($id)`).
- Mass assignment: `$guarded = []` or `$fillable` including `role`/`is_admin`;
  `Model::create($request->all())`. Use `$request->validated()`.
- API Resources to control output; `$hidden` for `password`, `remember_token`.

## 12. Validation
- Form Requests or `$request->validate([...])` with `max:` on strings.

## 13. Sanitization
- Shell: `Process::run([...])` with an array, never `exec`/`shell_exec`/`system` with user
  input (or `escapeshellarg` at minimum).
- HTML: `mews/purifier` (HTML Purifier) for rich text.
- `unserialize()` on user data: object injection.

## 14. SQL injection
```bash
grep -rnE "DB::(raw|select|statement|unprepared)\(|whereRaw\(|selectRaw\(|orderByRaw\(|havingRaw\(" $EXCL app
```
- Raw methods with `"... $var ..."` or concatenation are unsafe; use bindings
  (`whereRaw('price > ?', [$price])`).
- `orderBy($request->sort)`: allowlist the column.

## 15. XSS (server-side)
- Blade `{!! $var !!}` with user content; `{{ }}` escapes.

## 16. File upload
- Validation `file|mimes:jpg,png|max:2048`; `mimes` checks content via fileinfo.
- `$file->store('uploads')` (generated name) on a non-public disk; serve via a controller
  with authorization. Never store in `public/` with the original name.

## 17. HTTPS
- `URL::forceScheme('https')` in production, `TrustProxies`, redirect at the web server.

## 18. Cookies
- `config/session.php`: `secure` (`SESSION_SECURE_COOKIE=true`), `http_only` true,
  `same_site` `lax`.

## 19. CSRF
- `VerifyCsrfToken`/`ValidateCsrfToken` middleware on the `web` group; `$except` list
  short. Sanctum SPA auth uses `/sanctum/csrf-cookie` + `XSRF-TOKEN`.

## 20. CORS
- `config/cors.php`: `allowed_origins => ['*']` with `supports_credentials => true` is
  dangerous.

## 21. Headers
- Not added by default. Middleware or `spatie/laravel-csp`; or set at nginx/Apache.

## 22. Rate limiting
- `RateLimiter::for(...)` + `throttle:` middleware; login throttling is built into
  Breeze/Fortify.

## 23. Dependencies
```bash
composer audit
composer outdated --direct
npm audit --omit=dev   # frontend assets
```

## 24. Unused packages
- `composer-unused` if installed; check `require` vs `require-dev`.

## 25. Exposed files
- Web root must be `public/`, not the project root (otherwise `.env` is downloadable).
- `phpinfo()` files, `storage/logs` reachable, Telescope/Horizon dashboards without a gate.

## 26. Database
- `DB_USERNAME` not `root`; `sslmode`/`options` for TLS in production.

## 27. Password hashing
```bash
grep -rnE "md5\(|sha1\(|hash\('sha256'" $EXCL app
```
- `Hash::make()` (bcrypt or argon2 per `config/hashing.php`). Model cast
  `'password' => 'hashed'`.
- HMAC for PINs: `hash_hmac('sha256', $pin, config('app.pin_secret'))`.

## 28. Password policy
- `Password::min(12)->mixedCase()->numbers()->symbols()` plus a custom rule for "no more
  than 2 identical characters in a row" (or `regex:/^(?!.*(.)\1\1)/`). Set it once with
  `Password::defaults(...)` and use `Password::defaults()` everywhere.
- bcrypt truncates at 72 bytes: add `max:72`.

## 29. Encryption
- Eloquent cast `'iban' => 'encrypted'` (uses `APP_KEY`, AES-256). Rotating `APP_KEY`
  requires `APP_PREVIOUS_KEYS`.
