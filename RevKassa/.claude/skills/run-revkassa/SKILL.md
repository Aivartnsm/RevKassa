---
name: run-revkassa
description: Start, build, test or check the RevKassa app locally (PostgreSQL in WSL, Spring Boot backend, Vue/Vite frontend). Use when asked to run the app, run tests, see a change working, or when the DB/Gradle won't start.
---

# Running RevKassa

Project root: the folder with `build.gradle`. Frontend: `frontend/`.

## Environment gotchas
- Windows sets `JAVA_HOME` to a Windows JDK that WSL can't run. `~/.bashrc` fixes it, but
  non-interactive shells (like Claude's Bash tool) don't load it. Prefix Gradle commands:
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew ...`
- The user also runs things from IntelliJ on Windows (Windows JDK, `gradlew.bat`). Don't
  change IntelliJ config.
- `frontend/node_modules` is platform-specific. If `npm run dev` fails with a missing native
  binding, it was installed from the other OS: delete `node_modules` and `npm install` again.

## 1. Database (PostgreSQL 16, systemd service in WSL)
```bash
systemctl is-active postgresql || sudo systemctl start postgresql
PGPASSWORD=revkassa psql -h localhost -U revkassa -d revkassa -c 'select 1'
```
DB, user and password are all `revkassa`. Windows reaches it at `localhost:5432`.
Flyway applies migrations on app start; never create tables by hand.

## 2. Backend
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test        # tests (need the DB)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew bootRun     # :8080
```
Run `bootRun` in the background and wait for `curl -s localhost:8080/actuator/health`
to return `{"status":"UP"}`. Stop it when done.

## 3. Frontend
```bash
cd frontend && npm run dev      # :5173, proxies /api and /actuator to :8080
cd frontend && npm run build    # type/compile check
```

## Email in dev
Without `spring.mail.host` the app writes invoice emails to the log instead of sending them
(look for the `Mailer` log lines). Never point dev at a real SMTP server with real member
addresses.

## Verifying a change
- Backend logic: a test, then `./gradlew test`.
- API: `bootRun` + `curl` against `localhost:8080/api/...` (session login first when secured).
- UI: both servers running, then open `http://localhost:5173`.
Report what you actually ran and its output; say so if something could not be verified.
