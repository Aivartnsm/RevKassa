# Node.js (Express, NestJS, Fastify, Next.js API routes)

Concrete checks for the numbered items in `../checklist.md`. Set `SRC` to the server code
folder (`src`, `server`, `api`, `app`, `pages/api`).

```bash
SRC=src
EXCL='--exclude-dir=node_modules --exclude-dir=dist --exclude-dir=build --exclude-dir=.next'
```

## 1–2. Secrets and environment variables
- `dotenv` loads `.env`; `.env` in `.gitignore`, `.env.example` committed.
- Next.js: only `NEXT_PUBLIC_*` reaches the browser. Anything secret without that prefix
  must not be read in client components.
- `grep -rnE 'process\.env\.[A-Z_]+' $EXCL $SRC | grep -iE 'console|log'`

## 3. Debug mode
- `NODE_ENV=production` in production (Express hides stack traces in the default error
  handler only then).
- `app.use(errorHandler())` from `errorhandler` package only in development.
- `DEBUG=*` not set in production.
- ORM query logging: Prisma `log: ['query']`, Sequelize `logging: console.log`, TypeORM
  `logging: true` only in development.
- `grep -rnE 'err\.stack|error\.stack' $EXCL $SRC` in responses.

## 4. Production settings
- Config per environment (`config/`, `NODE_ENV`, `@nestjs/config`).
- Express behind a proxy: `app.set('trust proxy', 1)` (exact hop count, not `true`).
- TypeORM `synchronize: false`, Sequelize no `sync({ alter/force })` in production.
- Process manager / container runs `node dist/...`, not `nodemon` or `ts-node-dev`.

## 5. Authentication
```bash
grep -rnE 'passport|express-session|cookie-session|jsonwebtoken|jose|next-auth|@auth/|lucia|better-auth|@nestjs/passport|AuthGuard' $EXCL $SRC package.json
```
- Express: auth middleware applied globally or on routers, not forgotten per route.
  NestJS: a global guard (`APP_GUARD`) with an explicit `@Public()` decorator is safer than
  per-controller guards.
- `express-session`: `session.regenerate()` on login (session fixation).
- `jsonwebtoken.verify(token, key, { algorithms: ['HS256'] })`: explicit `algorithms`.
  `jwt.decode()` does not verify.

## 6. Session expiry
- `express-session` `cookie.maxAge` plus `rolling: true` for idle timeout; a session store
  with TTL (Redis, database), not the default `MemoryStore` in production.
- JWT `expiresIn` short; refresh tokens stored server-side and revocable.

## 7. Server-side logout
- `req.session.destroy()` + `res.clearCookie('connect.sid')`; Passport `req.logout(cb)`
  together with session destroy.
- JWT: revoke the refresh token in the database.

## 8. Password reset
- `crypto.randomBytes(32).toString('base64url')`, stored as
  `crypto.createHash('sha256')` hash with an expiry.
- Base URL from config, not `req.headers.host` / `req.hostname`.

## 9–10. Admin routes and permissions
```bash
grep -rnE "router\.(get|post|put|patch|delete)|app\.(get|post|put|patch|delete)|@(Get|Post|Put|Patch|Delete)\(" $EXCL $SRC
grep -rnE 'role|isAdmin|Roles\(|RolesGuard|authorize' $EXCL $SRC
```
- `app.use('/api/admin', requireAuth, requireRole('admin'), adminRouter)`.
- Role from the database / verified session, not from `req.body.role` or an unverified
  token.

## 11. Own data only
- `findUnique({ where: { id } })` / `findById(id)` where it should include the owner
  (`where: { id, userId: req.user.id }`).
- Responses: Prisma `select`, Mongoose `.select('-passwordHash')` or DTO/serializer
  (`class-transformer` `@Exclude`). Returning the whole model leaks fields.
- Mass assignment: `Model.create(req.body)`, `Object.assign(user, req.body)`,
  `prisma.user.update({ data: req.body })`.

## 12. Validation
- zod, joi, yup, `class-validator` with NestJS `ValidationPipe({ whitelist: true, forbidNonWhitelisted: true })`.
- `express.json({ limit: '100kb' })`.

## 13. Sanitization
- Shell: `child_process.execFile`/`spawn` with an argument array, never `exec` with user
  input.
- HTML: `sanitize-html` or DOMPurify (`isomorphic-dompurify`) for rich text.
- SSRF: validate URLs before `fetch`/`axios` on user-supplied addresses.
- Prototype pollution: deep-merge of `req.body` into objects (`lodash.merge`, custom
  merge) with `__proto__`/`constructor` keys.

## 14. SQL / NoSQL injection
```bash
grep -rnE '(query|execute|raw|\$queryRawUnsafe|\$executeRawUnsafe|whereRaw|knex\.raw)\s*\(\s*`[^`]*\$\{' $EXCL $SRC
grep -rnE "(query|execute)\s*\(\s*['\"][^'\"]*['\"]\s*\+" $EXCL $SRC
```
- Prisma `$queryRaw` with a tagged template is safe; `$queryRawUnsafe` with interpolation
  is not.
- MongoDB: `express-mongo-sanitize` or validation that rejects objects where strings are
  expected (`{ "$ne": null }` login bypass). No `$where` with user input.

## 15. XSS (server-side)
- EJS `<%- %>`, Handlebars `{{{ }}}`, Pug `!{}` / `!=` with user content.
- `res.send(userInput)` without a content type renders as HTML.

## 16. File upload
- multer `limits: { fileSize }`, `fileFilter`; check magic bytes (`file-type` package).
- `storage` with generated names, destination outside `public/`.

## 17. HTTPS
- `trust proxy` set, redirect at the proxy or with middleware checking `req.secure`.
- HSTS via helmet (check 21).

## 18. Cookies
```bash
grep -rnE 'cookie\s*:|res\.cookie\(|httpOnly|secure\s*:|sameSite' $EXCL $SRC
```
- `express-session({ cookie: { httpOnly: true, secure: true, sameSite: 'lax' } })`.

## 19. CSRF
- `csurf` is deprecated. Use `csrf-csrf` (double submit), `csrf-sync`, or framework support
  (NestJS with `csrf-csrf`; Next.js Server Actions check the Origin header).
- Cookie sessions without any CSRF middleware: High.

## 20. CORS
```bash
grep -rnE "cors\(|origin\s*:|Access-Control-Allow-Origin" $EXCL $SRC
```
- `cors()` without options allows all origins. `origin: true` reflects any origin;
  together with `credentials: true` it is dangerous.

## 21. Headers
- `helmet()` sets most headers (CSP, HSTS, nosniff, frameguard, referrer policy). Check
  it is used and not with `contentSecurityPolicy: false` without a reason.
- Next.js: `headers()` in `next.config.js` or middleware.

## 22. Rate limiting
- `express-rate-limit` (+ `rate-limit-redis` for multiple instances), `@nestjs/throttler`,
  `@fastify/rate-limit`.

## 23. Dependencies
```bash
npm audit --omit=dev
npm outdated
# or: pnpm audit --prod / yarn npm audit
```

## 24. Unused packages
```bash
npx depcheck
```
- Packages only used in build config (`vite.config`, `tsconfig` plugins) show as unused
  falsely; check before removing.
- `nodemon`, `ts-node`, test and lint tools belong in `devDependencies`.

## 25. Exposed files
- `express.static('public')` contents; never `express.static('.')` or the project root.
- GraphQL: `introspection: false` and playground off in production.
- Source maps in production builds (`sourceMap`/`productionBrowserSourceMaps` in Next).

## 26. Database
- Connection string with `sslmode=require` / `ssl: { rejectUnauthorized: true }` in
  production. `rejectUnauthorized: false` disables certificate checks.

## 27. Password hashing
```bash
grep -rnE "createHash\('(md5|sha1|sha256)'\)|md5\(|sha1\(" $EXCL $SRC
grep -rnE 'bcrypt|argon2|scrypt' $EXCL $SRC package.json
```
- `argon2` or `bcrypt`/`bcryptjs` (cost ≥ 10), or `crypto.scrypt`.
- HMAC for PINs: `crypto.createHmac('sha256', secret)`.

## 28. Password policy
- One zod/joi schema or `class-validator` custom decorator reused everywhere. The
  checklist regex works in JavaScript as-is.
- bcrypt truncates at 72 bytes: `z.string().max(72)` with bcrypt.

## 29. Encryption
- `crypto.createCipheriv('aes-256-gcm', key, iv)` with a random 12-byte IV and the auth
  tag stored with the ciphertext; key from the environment.
