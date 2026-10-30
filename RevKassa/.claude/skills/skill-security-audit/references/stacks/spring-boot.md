# Spring Boot (Java / Kotlin)

Concrete checks for the numbered items in `../checklist.md`. Paths assume a backend in
`backend/` or the project root; adjust them. Config lives in
`src/main/resources/application*.properties|yml`.

```bash
CFG=$(find . -path ./node_modules -prune -o -path '*/src/main/resources/application*' -print)
SRC=$(find . -path ./node_modules -prune -o -type d -path '*/src/main' -print | head -1)
```

## 1–2. Secrets and environment variables
- Secrets as `${ENV_VAR:dev-default}` in config. Production profile without defaults:
  `${ENV_VAR}`.
- Local secrets file (`application-local.properties`) is in `.gitignore`.
- Look for logged config: `grep -rnE '@Value|System\.getenv' $SRC | grep -i log`.

## 3. Debug mode
```bash
grep -nE '^(debug|trace)\s*=\s*true|logging\.level\.[a-zA-Z.]*\s*[=:]\s*(DEBUG|TRACE)|show-sql\s*[=:]\s*true|format_sql|include-stacktrace|include-exception|include-message|h2\.console\.enabled' $CFG
grep -nE 'devtools' build.gradle* pom.xml 2>/dev/null
```
- `debug=true`, `trace=true` only in dev.
- `spring.jpa.show-sql=true`, `logging.level.org.hibernate.SQL=DEBUG` and
  `org.hibernate.orm.jdbc.bind=TRACE` log queries with parameters; dev profile only.
- `server.error.include-stacktrace=never`, `include-exception=false`.
- `spring-boot-devtools` must be `developmentOnly` (Gradle) or `optional` + `runtime`
  (Maven), never `implementation`.
- `spring.h2.console.enabled` is not `true` in production.

## 4. Production settings
- Profiles: `application-prod.properties`, selected with `SPRING_PROFILES_ACTIVE`.
  `spring.profiles.active=prod` hard-coded in the main file is a smell.
- `spring.jpa.hibernate.ddl-auto=validate` or `none` (never `create`, `create-drop`,
  `update`). Flyway/Liquibase migrations instead.
- `server.forward-headers-strategy=framework` behind a reverse proxy.
- `management.endpoints.web.exposure.include=health`.
- `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false` in prod.

## 5. Authentication
```bash
grep -nE 'spring-boot-starter-security|oauth2|jjwt|nimbus|java-jwt' build.gradle* pom.xml 2>/dev/null
grep -rnE 'SecurityFilterChain|@EnableWebSecurity|httpBasic|formLogin|oauth2Login|oauth2ResourceServer' $SRC
```
- Starter present but no own `SecurityFilterChain`: Spring defaults apply ("Using
  generated security password" in the log, HTTP Basic). Must not go to production.
- Starter missing: all routes are open.
- Session fixation: Spring changes the session id on login by default. Check that
  `sessionFixation().none()` isn't configured.
- Last rule `anyRequest().authenticated()` (or `denyAll()`), not `permitAll()`.

## 6. Session expiry
```bash
grep -rnE 'session\.timeout|setMaxInactiveInterval|maximumSessions|tokenValiditySeconds|expiration|expiresAt' $SRC $CFG
```
- `server.servlet.session.timeout` (default 30m). A custom idle rule (for example 1 min)
  must be enforced server-side: a filter that checks last activity, or
  `setMaxInactiveInterval` on the session.
- JWT: `exp` claim checked by the decoder (`JwtDecoder` validates it by default; check that
  custom parsing does too).
- `rememberMe().tokenValiditySeconds(...)` limited.

## 7. Server-side logout
```bash
grep -rnE 'logout|invalidate\(|clearContext' $SRC
```
- `logout(l -> l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID"))`,
  or an own endpoint calling `session.invalidate()` and `SecurityContextHolder.clearContext()`.
- With CSRF enabled, Spring's logout only accepts POST by default.

## 8. Password reset
- Token from `SecureRandom` (`new byte[32]`, Base64url), stored as SHA-256 in the
  database with `expires_at` and `used_at`.
- Base URL from config (`app.base-url`), not `request.getServerName()` / `Host`.
- After reset: invalidate sessions (Spring Session `FindByIndexNameSessionRepository`, or
  a `password_changed_at` timestamp compared on each request).

## 9–10. Admin routes and permissions
```bash
grep -rnE 'requestMatchers|authorizeHttpRequests|hasRole|hasAuthority|@PreAuthorize|@Secured|@RolesAllowed|@EnableMethodSecurity' $SRC
grep -rnE '@(Request|Get|Post|Put|Patch|Delete)Mapping' $SRC
```
- `.requestMatchers("/api/admin/**").hasRole("ADMIN")` before broader rules.
- Method security: `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")`.
- Roles loaded from the database in `UserDetailsService`, not taken from request data.

## 11. Own data only
- Path variables with ids: `grep -rnE '@PathVariable' $SRC`. Check the service filters by
  the current user (`findByIdAndOwnerId`), or use `/api/me/...` with the user from
  `SecurityContextHolder` / `@AuthenticationPrincipal`.
- Controllers return DTOs, never `@Entity` classes:
  `grep -rnE 'public (ResponseEntity<)?[A-Z][a-zA-Z]*(Entity)?>? [a-z]' $SRC/java/**/controller` and compare return types with entity classes.
- `@RequestBody` bound to an entity = mass assignment.

## 12. Validation
```bash
grep -rn "@RequestBody" $SRC | grep -v "@Valid"
grep -rnE '@(NotNull|NotBlank|Size|Pattern|Email|Min|Max|Positive)' $SRC | wc -l
```
- `jakarta.validation` annotations on request DTOs + `@Valid` in controllers.
- `MethodArgumentNotValidException` handled in `@RestControllerAdvice` without leaking
  internals.
- Body size: `server.tomcat.max-swallow-size`, `spring.servlet.multipart.*`.

## 13. Sanitization
- Email: `MimeMessageHelper.setSubject/setTo`, not header strings.
- Rich text: Jsoup `Jsoup.clean(html, Safelist.basic())` or OWASP Java HTML Sanitizer.
- Shell: `ProcessBuilder` with an argument list, never `Runtime.exec(String)` with user
  input.

## 14. SQL injection
```bash
grep -rnE '(createQuery|createNativeQuery|jdbcTemplate\.(query|queryForObject|queryForList|update|execute)|@Query)\s*\(' $SRC | grep -E '\+ *[a-zA-Z"]'
grep -rnE 'String\.format\(.*(SELECT|UPDATE|DELETE|INSERT|WHERE|ORDER BY)' $SRC
grep -rnE '"(SELECT|UPDATE|DELETE|INSERT)[^"]*" *\+' $SRC
```
- Spring Data derived methods and `@Query` with `:param` are safe.
- Dynamic sorting: Spring Data `Sort` validates the property name; raw column names need an
  allowlist.

## 15. XSS (server-side)
- Thymeleaf `th:utext`, FreeMarker `?no_esc`, Mustache `{{{ }}}` with user content.

## 16. File upload
```bash
grep -rnE 'MultipartFile|@RequestPart' $SRC
grep -nE 'multipart' $CFG
```
- `spring.servlet.multipart.max-file-size`, `max-request-size`.
- Content type by magic bytes (Apache Tika). Store outside `resources/static`.

## 17. HTTPS
- Behind a proxy: `server.forward-headers-strategy=framework`.
- Redirect: `http.redirectToHttps(Customizer.withDefaults())` (Spring Security 6.1+), or
  at the proxy.
- `server.ssl.*` if Spring terminates TLS itself.

## 18. Cookies
```bash
grep -nE 'session\.cookie' $CFG
grep -rnE 'ResponseCookie|new Cookie\(|setHttpOnly|setSecure|sameSite' $SRC
```
- `server.servlet.session.cookie.http-only=true`, `.secure=true` (prod), `.same-site=lax`.

## 19. CSRF
```bash
grep -rnE 'csrf|Csrf' $SRC
```
- `csrf().disable()` / `csrf(AbstractHttpConfigurer::disable)` with a cookie session: High.
- SPA: in Spring Security 7, `csrf(csrf -> csrf.spa())`. In 6.x:
  `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfTokenRequestAttributeHandler`
  (or the documented `SpaCsrfTokenRequestHandler`) + a filter that loads the deferred
  token.
- `ignoringRequestMatchers(...)` list is short and justified.

## 20. CORS
```bash
grep -rnE '@CrossOrigin|allowedOrigins|allowedOriginPatterns|CorsConfiguration|addCorsMappings' $SRC
```
- `@CrossOrigin` without arguments allows all origins.
- `allowedOriginPatterns("*")` + `allowCredentials(true)` is dangerous.

## 21. Headers
- Spring Security adds `X-Content-Type-Options`, `X-Frame-Options: DENY`,
  `Cache-Control: no-store`, and HSTS on HTTPS by default. Check for
  `headers().disable()` / `frameOptions().disable()`.
- Add: `headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; frame-ancestors 'none'; object-src 'none'")).referrerPolicy(r -> r.policy(STRICT_ORIGIN_WHEN_CROSS_ORIGIN)).permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=()")))`.
  The exact method names vary slightly between Spring Security versions; check the
  version in use.

## 22. Rate limiting
- Bucket4j, Resilience4j `RateLimiter`, or a simple attempt counter in the database.
- Client IP behind a proxy: `request.getRemoteAddr()` is correct only with
  `forward-headers-strategy` set.

## 23. Dependencies
```bash
./gradlew dependencies --configuration runtimeClasspath | grep -E '^\+---|^\\---' | head -40
./mvnw dependency:tree -Dscope=runtime | head -60
# if configured:
./gradlew dependencyCheckAnalyze   # OWASP plugin
```
- The Spring Boot BOM manages most versions. Manually pinned versions that override the
  BOM are often older than needed.

## 24. Unused packages
- For each `implementation`/`<dependency>`: `grep -rn "import <package.prefix>" $SRC`.
  Starters (`spring-boot-starter-actuator`, `-validation`) work without imports; check
  config and annotations first.

## 25. Exposed files
- `src/main/resources/static/` and `public/` contents.
- Actuator: `management.endpoints.web.exposure.include` must not be `*` (exposes `env`,
  `heapdump`, `configprops`).
- springdoc / Swagger UI in production.

## 26. Database
- JDBC URL in prod with `sslmode=require` (PostgreSQL) or `useSSL=true` (MySQL).
- `ddl-auto` as in check 4.

## 27. Password hashing
```bash
grep -rnE 'MessageDigest|DigestUtils|md5|sha1|NoOpPasswordEncoder|\{noop\}|StandardPasswordEncoder' $SRC
grep -rniE 'log\.(info|debug|warn|error)\(.*(password|pin|secret|token)' $SRC
grep -rnE 'CommonsRequestLoggingFilter|setIncludePayload' $SRC
```
- `BCryptPasswordEncoder`, `Argon2PasswordEncoder`, or
  `PasswordEncoderFactories.createDelegatingPasswordEncoder()`.
- HMAC for PINs: `javax.crypto.Mac.getInstance("HmacSHA256")` with the key from config.

## 28. Password policy
```bash
grep -rnE 'password' $SRC --include=*Dto.java --include=*Request*.java -il
grep -rnE '@Pattern|@Size|ConstraintValidator' $SRC
```
- A custom `@StrongPassword` annotation + `ConstraintValidator<StrongPassword, String>`
  that reports each failed rule via `context.buildConstraintViolationWithTemplate(...)`.
- Java supports the regex from the checklist as-is (escape backslashes in the annotation:
  `@Pattern(regexp = "^(?!.*(.)\\1\\1)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,}$")`).
- Spring Security 6.3+ `BCryptPasswordEncoder` throws for input over 72 bytes: add
  `@Size(max = 72)`.

## 29. Encryption
- JPA `AttributeConverter<String, String>` with AES-GCM (`Cipher.getInstance("AES/GCM/NoPadding")`,
  random 12-byte IV stored with the ciphertext), key from the environment.
- Or PostgreSQL `pgcrypto` (`pgp_sym_encrypt`) with the key passed as a parameter, never
  stored in the database.
