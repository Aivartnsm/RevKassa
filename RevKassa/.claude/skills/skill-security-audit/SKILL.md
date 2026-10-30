---
name: skill-security-audit
description: Review an entire project (backend, frontend, database, configuration, dependencies, git history) for security problems, report each finding with severity and location, and fix the code only after the user approves specific fixes. Covers 29 checks - API keys and secrets, environment variables, debug mode, production settings, authentication, session expiry, server-side logout, password reset link expiry, admin routes, user permissions, access to own data only (IDOR), form validation, input sanitization, SQL injection, XSS, file uploads, HTTPS, secure cookies, CSRF, CORS, security headers, rate limiting, dependency versions, unused packages, exposed files, database security, password hashing, password policy and encryption of sensitive data. Works with any stack (Spring Boot, Node/Express/NestJS, Django/FastAPI/Flask, Laravel, Vue/React/Angular/Svelte and others). Use it whenever the user says "make the project secure", "teeme projekti turvaliseks", "security audit", "turvaaudit", "security review", "is my app secure", "check for vulnerabilities", "harden the app", or wants to check security before publishing or deploying, even if they only mention one of the areas above.
---

# Security audit

Goal: the user gets a clear picture of where the project is vulnerable and what is already
fine, and decides which problems to fix. The audit itself is **read-only**. Change code
only after the user has approved specific fixes.

Talk to the user in the language they use. For every finding, briefly explain **what could
happen** in plain words. Many users are still learning, and "CSRF is missing" tells them
nothing, while "another website can confirm a payment in your name" does.

## 1. Get to know the project

Checks depend on the stack, so first find out what you are looking at:

- **Backend:** build and dependency files (`build.gradle`, `pom.xml`, `package.json`,
  `requirements.txt`, `pyproject.toml`, `composer.json`, `Gemfile`, `go.mod`, `*.csproj`),
  framework and security library versions, configuration files, the security setup and
  the route or controller definitions.
- **Frontend:** `package.json`, the bundler config (`vite.config.*`, `webpack.config.*`,
  `next.config.*`), `index.html`, `.env*` files and the HTTP client setup.
- **Database:** schema scripts, migrations and the ORM configuration.
- **Project rules and plans:** `CLAUDE.md`, `README.md`, `docs/` (plan, task list,
  architecture). These tell you what is **planned** but not built yet, for example "login
  comes in task B04". A planned-but-missing feature is a different finding from a
  forgotten one, and the report must show the difference.
- **Git:** `git ls-files`, `.gitignore`, and whether the repository is public
  (`git remote -v`).
- **Deployment:** Dockerfile, CI workflows, platform config (`fly.toml`, `render.yaml`,
  `vercel.json`, nginx config). These tell you how HTTPS, headers and secrets are handled
  in production. If nothing shows how the app is deployed, say so.

Then read the matching stack files from `references/stacks/`. Read only the ones the
project uses:

| Project uses | Read |
|---|---|
| Spring Boot / Java / Kotlin | `references/stacks/spring-boot.md` |
| Node.js (Express, NestJS, Fastify, Next.js API routes) | `references/stacks/node.md` |
| Python (Django, FastAPI, Flask) | `references/stacks/python.md` |
| PHP (Laravel) | `references/stacks/php-laravel.md` |
| Browser frontend (Vue, React, Angular, Svelte, plain JS) | `references/stacks/frontend-spa.md` |

For any other stack (Rails, ASP.NET, Go, Phoenix and so on), apply the generic checks from
`references/checklist.md` and translate them to that framework's equivalents. Say in the
report that no stack file existed, so the user knows the check was less specific.

Judge findings by the project's real situation. A local development password in a config
file of a learning project is a low risk. The same password in production config is
critical. Rate the real danger, not the theoretical maximum, or the real problems get lost
in the noise.

## 2. Run all 29 checks

`references/checklist.md` describes each check: why it matters, what to look for, and
when it counts as OK. Read it before you start. The stack files add concrete commands and
config keys for each check, using the same numbers.

The checks are in six groups, so the report is easy to read:

**A. Secrets and settings**
1. API keys and secrets in code
2. Environment variables and leaks
3. Debug mode off
4. Production settings

**B. Authentication and access**
5. Authentication
6. Login session expires
7. Logout also ends the session on the server
8. Password reset link expires
9. Admin routes are protected
10. User permissions
11. Users can access only their own and public data

**C. Input**
12. Form validation
13. Input sanitization
14. SQL injection
15. XSS protection
16. Secure file upload

**D. Browser and network**
17. HTTPS
18. Secure cookies
19. CSRF protection
20. CORS settings
21. Security headers
22. Rate limiting

**E. Dependencies and files**
23. Dependency versions and known vulnerabilities
24. Unused packages
25. Exposed files

**F. Data**
26. Database security
27. Password hashing
28. Password policy (at least 12 characters, upper and lower case letter, number, special
    character, no more than 2 identical characters in a row)
29. Encryption of sensitive data

Some checks depend on each other. A cookie-based session means CSRF protection is needed.
Behind a reverse proxy, HTTPS detection and rate limiting depend on trusting forwarded
headers. When one finding affects another check, refer to it in the report instead of
writing the same thing twice.

Check for real: read files and run read-only commands. Don't draw conclusions from file
names. If something can't be checked (for example `npm audit` needs network access and
there is none), say so in the report. Never skip a check silently.

**Never print secrets.** If you find a key, password or token, show only its location and
the first few characters (`sk-ab…`). Otherwise the secret ends up in the conversation and
logs, and the leak gets worse.

## 3. Report

Present the report in the conversation using this format:

```markdown
# Security audit: <project name>

**Summary:** <2–3 sentences: overall state and the most important thing to do right away>

### A. Secrets and settings
| # | Check | Status | Severity | Finding |
|---|-------|--------|----------|---------|
| 1 | API keys | ✅ OK | – | No secrets in code or git history |
| 3 | Debug mode | ⚠️ Issue | Low | SQL logging enabled in the main config |
| … |

### B. Authentication and access
| # | Check | Status | Severity | Finding |
|---|-------|--------|----------|---------|
| 9 | Admin routes | ⚠️ Issue | High | `/api/admin/**` is open to everyone |
| … |

<…the other groups the same way; all 29 checks must appear in the tables…>

## Issues

### [High] 9. Admin routes are not protected
**Where:** `src/main/java/.../SecurityConfig.java:24`
**What could happen:** <one or two sentences in plain words>
**Fix:** <what exactly you would change, which files>
**Impact:** <could the change break something, does it need tests>
```

Statuses: ✅ OK · ⚠️ Issue · 🕓 Planned (in the project plan, not built yet) ·
➖ Not applicable (for example no passwords or no file uploads) · ❔ Could not check.

Severities:

- **Critical:** can be exploited right now with serious consequences. Examples: a secret
  in a public repository, an admin route with no authentication, passwords stored in
  plain text.
- **High:** exploitable, but needs a precondition. Examples: IDOR, missing CSRF protection
  with a cookie session.
- **Medium:** a missing layer of defense. Examples: security headers, rate limiting, a
  CORS policy that is too broad.
- **Low:** hygiene. Examples: an unused package, a slightly outdated dependency with no
  known vulnerabilities.

Sort the issues by severity, most severe first.

## 4. Ask for approval

After the report, ask what to fix. Use the `AskUserQuestion` tool with `multiSelect`.
Make each option one issue or a logical group (for example "Security headers + CORS").
Mark the recommended options with `(Recommended)`. If there are more than four issues,
group them by severity or topic.

Some fixes are not code changes. Don't do these yourself; give the user instructions:

- **Rotating a leaked secret:** the key must be regenerated with the provider. Removing it
  from the code doesn't help, because it is already in the git history.
- **Rewriting git history** (`git filter-repo`, BFG): this is irreversible and affects
  everyone who has cloned the repository. Explain it and let the user decide.
- **Major version upgrades** (for example a new framework major version): that is
  separate work, not part of the audit.
- **Planned features** (🕓): if a whole task is planned (for example login), don't build
  it during the audit. Point to the task and list what it must include from a security
  point of view.

If authentication is missing and **not** planned, adding it is a big change. Ask
separately which approach the user wants (cookie session or tokens, what the login
identifier is), because CSRF, cookies and logout depend on that choice.

## 5. Fix

Fix only the checks the user selected:

- If you are on the main branch, ask whether to create a new branch before changing
  anything.
- Follow the project's conventions (`CLAUDE.md`, the existing code). A security fix should
  look like the rest of the code: the same package layout, naming and comment density.
- Make the smallest change that solves the problem. Don't refactor anything else at the
  same time.
- Keep local development working. When you move a secret into an environment variable,
  keep a development default where that is safe (for example
  `${DB_PASSWORD:dev-password}` in Spring, `os.getenv("DB_PASSWORD", "dev-password")`
  only in a dev settings file) or add a `.env.example`. Otherwise nobody can start the app
  the next morning and the fix gets thrown away. Production config must not have such a
  default.
- If a fix needs a secret you don't have (for example a production key), add the
  placeholder and instructions. Never make up a value.

## 6. Verify and report back

After fixing, run what the project has:

- the backend tests (`./gradlew test`, `./mvnw test`, `npm test`, `pytest`,
  `php artisan test` and so on);
- the frontend build (`npm run build` or equivalent);
- when you changed security settings, try them for real if the app can run: an admin
  route without login must return 401 or 403 (`curl -i`), headers can be checked with
  `curl -I`, and an old session cookie after logout must return 401.

Finish with a short summary:

- what you fixed (files);
- what you ran and what the result was (show the output of any failing test);
- what is left and why (not selected, planned, or needs the user to act).

If the project has a task list or issue tracker in the repository (for example
`docs/TASKS.md`, `BACKLOG.md`), offer to add the remaining issues there as tasks, so they
aren't forgotten.
