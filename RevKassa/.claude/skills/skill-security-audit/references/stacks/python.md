# Python (Django, FastAPI, Flask)

Concrete checks for the numbered items in `../checklist.md`. Set `SRC` to the code folder.

```bash
SRC=.
EXCL='--exclude-dir=.venv --exclude-dir=venv --exclude-dir=node_modules --exclude-dir=__pycache__'
```

## 1–2. Secrets and environment variables
- Django `SECRET_KEY`, Flask `SECRET_KEY`/`app.secret_key`, FastAPI JWT secrets from
  `os.environ` / `django-environ` / `pydantic-settings`, not literals:
  `grep -rnE "SECRET_KEY\s*=\s*['\"]" $EXCL $SRC`.
- `.env` in `.gitignore`.

## 3. Debug mode
```bash
grep -rnE "DEBUG\s*=\s*True|app\.run\(.*debug\s*=\s*True|FLASK_DEBUG|debug\s*=\s*True|echo\s*=\s*True" $EXCL $SRC
```
- Django `DEBUG = False` in production (shows settings and SQL on error pages otherwise).
- Flask `debug=True` enables the Werkzeug debugger: remote code execution if reachable.
- FastAPI/Starlette `debug=True` shows tracebacks.
- SQLAlchemy `echo=True` logs queries.
- `django-debug-toolbar` only in dev settings and `INTERNAL_IPS`.

## 4. Production settings
- Separate settings (`settings/prod.py`, `DJANGO_SETTINGS_MODULE`) or env-driven config.
- Django: run `python manage.py check --deploy` and report its warnings.
- `ALLOWED_HOSTS` not `['*']` in production.
- Behind a proxy: Django `SECURE_PROXY_SSL_HEADER`, `USE_X_FORWARDED_HOST` only if the
  proxy sets it; Werkzeug `ProxyFix`; uvicorn `--proxy-headers --forwarded-allow-ips`.
- Production server: gunicorn/uvicorn workers, not `runserver` or `flask run`.

## 5. Authentication
- Django: `django.contrib.auth`, `LoginRequiredMixin`/`@login_required`, DRF
  `DEFAULT_PERMISSION_CLASSES = ['rest_framework.permissions.IsAuthenticated']`
  (the DRF default is `AllowAny`).
- FastAPI: dependencies (`Depends(get_current_user)`) on routers, not forgotten per route.
- Flask: `flask-login` `@login_required`.
- JWT (`PyJWT`): `jwt.decode(token, key, algorithms=["HS256"])` with explicit algorithms.

## 6. Session expiry
- Django `SESSION_COOKIE_AGE`, `SESSION_SAVE_EVERY_REQUEST = True` for idle timeout,
  `SESSION_EXPIRE_AT_BROWSER_CLOSE`.
- Flask `PERMANENT_SESSION_LIFETIME`. Flask's default session is a signed client cookie
  and can't be revoked server-side; use `flask-session` for server-side sessions.
- JWT `exp` claim short; `djangorestframework-simplejwt` `ACCESS_TOKEN_LIFETIME`,
  refresh blacklist app enabled.

## 7. Server-side logout
- Django `logout(request)` (flushes the session). DRF token auth: delete the token.
- simplejwt: `token_blacklist` app and blacklist the refresh token on logout.
- Flask default cookie sessions: `session.clear()` only clears the cookie the client has;
  an old copy stays valid until expiry. Note this.

## 8. Password reset
- Django's built-in `PasswordResetView` is fine: `PASSWORD_RESET_TIMEOUT` (default 3 days,
  lower it to e.g. 3600). Tokens become invalid after the password changes.
- Custom: `secrets.token_urlsafe(32)`, store `hashlib.sha256` of it with an expiry.
- Django builds links from `request.get_host()`, which relies on `ALLOWED_HOSTS` being
  strict.

## 9–10. Admin routes and permissions
- Django admin on a non-default path is a small bonus; protection is `is_staff`/
  `is_superuser` and a strong password.
- DRF `permission_classes`, `IsAdminUser`; FastAPI dependency checking the role.
- Roles from the database user, not request data.

## 11. Own data only
- Django: `Model.objects.get(pk=pk)` in views where it should be
  `request.user.orders.get(pk=pk)` / `get_object_or_404(Order, pk=pk, owner=request.user)`.
  DRF `get_queryset()` filtered by the user.
- DRF `ModelSerializer` with `fields = '__all__'` leaks fields and allows mass assignment;
  use explicit `fields` and `read_only_fields`.
- FastAPI: `response_model` with a schema that excludes sensitive fields.

## 12. Validation
- Django forms / DRF serializers / pydantic models with `max_length`, constraints.
- `DATA_UPLOAD_MAX_MEMORY_SIZE`, `FILE_UPLOAD_MAX_MEMORY_SIZE`.

## 13. Sanitization
- Shell: `subprocess.run([...])` with a list, never `shell=True` or `os.system` with user
  input.
- HTML: `nh3` (or `bleach`, now deprecated) for rich text.
- `pickle.loads`, `yaml.load` (without `SafeLoader`) on user data: remote code execution.
- SSRF: validate URLs before `requests.get` on user-supplied addresses.

## 14. SQL injection
```bash
grep -rnE "\.(raw|extra|execute)\(\s*f?['\"].*(\{|%s|\+|format\()" $EXCL $SRC
grep -rnE "text\(\s*f['\"]" $EXCL $SRC
grep -rnE "execute\(\s*f['\"]|execute\([^,]*%[^,]*\)" $EXCL $SRC
```
- Safe: ORM queries, `cursor.execute(sql, params)`, SQLAlchemy `text(...)` with
  `:param` bindings.
- Unsafe: f-strings, `%` or `.format()` building SQL; Django `.extra()`, `RawSQL` with
  interpolated input.

## 15. XSS (server-side)
- Django `|safe`, `{% autoescape off %}`, `mark_safe(...)` with user content.
- Jinja2: `Environment(autoescape=True)` (Flask enables it for `.html` templates);
  `|safe`, `Markup(...)` with user content.

## 16. File upload
- Django `FileField` with `upload_to` generating names; validate with `python-magic`.
- `MEDIA_ROOT` served by the web server with no script execution; `MEDIA_URL` separate
  from `STATIC_URL`.
- Werkzeug `secure_filename()` for any name that does reach the file system.

## 17. HTTPS
- Django: `SECURE_SSL_REDIRECT = True`, `SECURE_HSTS_SECONDS`, `SECURE_PROXY_SSL_HEADER`.
- Flask/FastAPI: at the proxy, or `flask-talisman` / Starlette `HTTPSRedirectMiddleware`.

## 18. Cookies
- Django `SESSION_COOKIE_SECURE`, `CSRF_COOKIE_SECURE`, `SESSION_COOKIE_HTTPONLY`
  (default True), `SESSION_COOKIE_SAMESITE`.
- Flask `SESSION_COOKIE_SECURE`, `SESSION_COOKIE_HTTPONLY`, `SESSION_COOKIE_SAMESITE`.

## 19. CSRF
```bash
grep -rnE "csrf_exempt|CsrfViewMiddleware|CSRFProtect|WTF_CSRF_ENABLED" $EXCL $SRC
```
- Django: `CsrfViewMiddleware` in `MIDDLEWARE`; `@csrf_exempt` list short. DRF
  `SessionAuthentication` enforces CSRF; token auth doesn't need it.
- Flask: `flask-wtf` `CSRFProtect(app)`.
- FastAPI with cookie auth: no built-in protection; use a double-submit middleware
  (e.g. `starlette-csrf`).

## 20. CORS
- `django-cors-headers`: `CORS_ALLOW_ALL_ORIGINS = True` with `CORS_ALLOW_CREDENTIALS = True`
  is dangerous; use `CORS_ALLOWED_ORIGINS`.
- FastAPI `CORSMiddleware(allow_origins=["*"], allow_credentials=True)`: same problem.
- `flask-cors` `CORS(app)` without options allows all origins.

## 21. Headers
- Django: `SecurityMiddleware` (nosniff, HSTS, referrer policy), `X_FRAME_OPTIONS = 'DENY'`,
  CSP via `django-csp` (or the built-in CSP support in newer Django versions).
- Flask: `flask-talisman`. FastAPI: `secure` package or a small middleware.

## 22. Rate limiting
- `django-ratelimit`, DRF throttling (`DEFAULT_THROTTLE_RATES`), `django-axes` for login
  lockout; `slowapi` (FastAPI/Starlette); `flask-limiter`.

## 23. Dependencies
```bash
pip-audit            # or: pip-audit -r requirements.txt
pip list --outdated
# poetry: poetry show --outdated ; uv: uv pip list --outdated
```

## 24. Unused packages
```bash
deptry .   # if installed
```
- Otherwise grep each requirement's import name (it can differ from the package name,
  e.g. `Pillow` → `PIL`, `python-dotenv` → `dotenv`).

## 25. Exposed files
- `STATIC_ROOT` contains only collected static files.
- FastAPI docs: `FastAPI(docs_url=None, redoc_url=None, openapi_url=None)` in production
  or protected.
- Django admin and debug toolbar URLs in production.

## 26. Database
- `DATABASES[...]['OPTIONS'] = {'sslmode': 'require'}` in production.
- Migrations run deliberately (`migrate` in the deploy step), not `create_all()` on every
  start in production.

## 27. Password hashing
```bash
grep -rnE "hashlib\.(md5|sha1|sha256)\(|MD5PasswordHasher|UnsaltedSHA1|make_password\(.*hasher=" $EXCL $SRC
```
- Django `PASSWORD_HASHERS` first entry Argon2 or PBKDF2 (default). `passlib`/`pwdlib`
  with argon2 or bcrypt; `werkzeug.security.generate_password_hash` (scrypt/pbkdf2).
- HMAC for PINs: `hmac.new(key, pin.encode(), hashlib.sha256)`.

## 28. Password policy
- Django: `AUTH_PASSWORD_VALIDATORS` plus a custom validator class for the character
  rules (Django's built-ins don't check character classes or repeats); set
  `MinimumLengthValidator` `min_length: 12`.
- pydantic: a `field_validator` reused via a shared type. Python `re` supports the
  checklist regex as-is.

## 29. Encryption
- `cryptography` `AESGCM` with a random 12-byte nonce; key from the environment.
  Field-level: a custom model field or `django-fernet-encrypted-fields`.
