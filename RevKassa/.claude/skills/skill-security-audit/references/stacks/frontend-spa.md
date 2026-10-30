# Browser frontend (Vue, React, Angular, Svelte, plain JS)

Concrete checks for the numbered items in `../checklist.md` that concern the browser
code. Set `FE` to the frontend source folder.

```bash
FE=frontend/src   # or src
```

Remember throughout: **the frontend is not a security boundary.** Everything here can be
read and changed by the user. Route guards, hidden buttons and form validation are user
experience. The real checks must be on the server.

## 1–2. Secrets and build-time variables
- Public prefixes that reach the bundle: Vite `VITE_*`, Next.js `NEXT_PUBLIC_*`, Create
  React App `REACT_APP_*`, Nuxt `NUXT_PUBLIC_*` / `runtimeConfig.public`, SvelteKit
  `PUBLIC_*` / `$env/static/public`, Angular `environment.ts` (all of it).
- `grep -rnE 'VITE_|NEXT_PUBLIC_|REACT_APP_|NUXT_PUBLIC_|PUBLIC_' $FE .env* 2>/dev/null`
  and check none of the values are secrets.
- Bundler `define` / `DefinePlugin` injecting `process.env` wholesale.
- Check the built output too if present: `grep -rnE 'sk-|AKIA|AIza' dist/ build/ .next/ 2>/dev/null | head`.

## 3. Debug mode
- Production build (`npm run build`) served, not the dev server.
- Vue `app.config.devtools`/`performance`, Redux DevTools enabled in production,
  Angular `enableProdMode()` (or built with production config).
- `grep -rnE 'console\.(log|debug)\(' $FE` for logs that print tokens or personal data.

## 4. Production settings
- API base URL from build config or relative (`/api`), not hard-coded
  `http://localhost:8080`: `grep -rnE "https?://localhost" $FE`.

## 6–7. Session expiry and logout
- An idle timer is fine, but it must call the server logout, and the app must handle 401
  from the API (interceptor that redirects to login).
- Logout calls the server endpoint (POST) before clearing local state:
  `grep -rn "logout" $FE`.
- Tokens in `localStorage`: readable by any XSS (check 18). `sessionStorage` for UI state
  only is fine.

## 9. Admin routes
- Router guards (`router.beforeEach`, React Router loaders, Angular `canActivate`) exist
  for UX, but every admin API call must be protected on the server. Admin code is still in
  the bundle; don't put secrets or hidden logic there.

## 12. Validation
- Present for user experience; same rules as the backend (especially the password policy,
  check 28).

## 15. XSS
```bash
grep -rnE 'v-html|dangerouslySetInnerHTML|\[innerHTML\]|bypassSecurityTrust|\{@html|innerHTML|outerHTML|insertAdjacentHTML|document\.write|eval\(|new Function\(' $FE
grep -rnE ':href="[^"]*\+|href=\{[^}]*\+|\[href\]=' $FE
```
- Vue `v-html`, React `dangerouslySetInnerHTML`, Angular `[innerHTML]` (sanitized by
  Angular unless `bypassSecurityTrust*` is used), Svelte `{@html}`: only with trusted or
  DOMPurify-sanitized content. Markdown renderers (`marked`, `markdown-it` with
  `html: true`) output raw HTML too.
- User-supplied URLs in `href`/`src`: allow only `http:`/`https:`.

## 18. Cookies and storage
```bash
grep -rnE 'document\.cookie|localStorage|sessionStorage' $FE
```
- Auth tokens in `localStorage` or readable cookies: prefer `HttpOnly` cookies set by
  the server.

## 19. CSRF
- axios reads the `XSRF-TOKEN` cookie and sends `X-XSRF-TOKEN` automatically for
  same-origin requests (`xsrfCookieName`/`xsrfHeaderName`). From axios 1.6, cross-origin
  requests need `withXSRFToken: true`. `fetch` sends nothing automatically: check the
  wrapper adds the header on POST/PUT/PATCH/DELETE.
- Angular `HttpClient` has built-in XSRF support (`withXsrfConfiguration`).

## 20. CORS / same origin
- Dev server proxy (`server.proxy` in `vite.config.*`, `devServer.proxy` in webpack,
  `rewrites` in Next) keeps frontend and API on the same origin, so CORS isn't needed.

## 21. Headers
- If the frontend is served by nginx, Netlify (`_headers`, `netlify.toml`), Vercel
  (`vercel.json` `headers`) or a CDN, the security headers (CSP, frame-ancestors,
  nosniff, referrer policy) must be set there.
- CSP with Vite dev mode needs `'unsafe-inline'`/websocket allowances; apply the strict
  policy to the production build only.

## 23–24. Dependencies
```bash
npm audit --omit=dev
npm outdated
npx depcheck
```
- Build tools, linters and test libraries belong in `devDependencies`.

## 25. Exposed files
- `public/` contents are copied to the build as-is: no `.env`, backups or internal docs.
- Source maps: Vite `build.sourcemap`, webpack `devtool` in production, Next
  `productionBrowserSourceMaps`. Fine if the code is public anyway; otherwise off or
  uploaded privately to the error tracker only.
- Build output (`dist/`, `build/`) committed to git: `git ls-files | grep -E '^(frontend/)?(dist|build)/' | head`.

## 28. Password policy
- Show the rules before typing and tick them off as they are met. Reuse the same rule
  definitions as the backend where possible. The checklist regex works in JavaScript.

## 29. Sensitive data
- Don't keep personal data in `localStorage` longer than needed; clear it on logout.
- Third-party scripts (analytics, chat widgets) on pages with sensitive data or reset
  tokens can read them.
