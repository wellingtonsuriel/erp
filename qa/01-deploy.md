# Phase 2 — Build and Deploy

## Status: **DEPLOYED — all three tiers up and proven, via authorized QA-environment workarounds.**

The backend initially could not stay running against a fresh database at all (DEFECT-001,
Critical) and the frontend could not produce a production build at all (DEFECT-002, High).
Both are genuine application defects, documented in full below and in `defects.md`/
`workarounds.md`, and **neither repository was modified to work around them** — per explicit
authorization, QA-environment-level workarounds (a JVM system property at backend launch, and
`npm install --no-save` for the frontend) were applied instead. See `workarounds.md` for the
exact commands, why each was necessary, and the specific real fix each needs in the repos.

---

## 0. Deployment architecture decision (see Phase 1 §20)

Docker Hub image pulls are policy-blocked in this sandbox (confirmed: `production.cloudfront.docker.com:443` → 403 from the egress proxy; independently re-confirmed with `docker pull mysql:8.0` and `docker pull hello-world`, both failing). **All three tiers are deployed as direct host processes instead.** A `docker-compose.qa.yml` is still provided as a documented, intended topology (see §6) but was not and cannot be executed in this sandbox.

## 1. Exact commands used, in order

```bash
# 1. Docker daemon check (for completeness/re-verification — see §0)
dockerd &                      # starts; confirms daemon itself works
docker pull mysql:8.0          # FAILS - 403 via CDN, see evidence/docker-pull-failures.txt

# 2. MySQL 8.0 via apt (official Ubuntu archive - not blocked)
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y mysql-server
service mysql start

# 3. Configure root for TCP access matching the app's hardcoded credentials
#    (application.properties: username=root, password=<blank>, url=jdbc:mysql://localhost:3306/pos_system)
mysql -u root <<'SQL'
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '';
CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED WITH mysql_native_password BY '';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
FLUSH PRIVILEGES;
CREATE DATABASE IF NOT EXISTS pos_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SQL

# 4. Build the backend
cd erp/hybrid && ./mvnw -q clean package -DskipTests
# → BUILD SUCCESS, target/hybrid-0.0.1-SNAPSHOT.jar (68,520,434 bytes)

# 5. Boot the backend with a throwaway JWT secret (see env-notes.md)
JWT_SECRET="<generated, see env-notes.md>" JWT_EXPIRATION_MS=86400000 \
  java -jar erp/hybrid/target/hybrid-0.0.1-SNAPSHOT.jar --app.upload.dir=/home/user/qa/uploads
```

## 2. Database — proven independently of the application

| Check | Result | Evidence |
|---|---|---|
| MySQL process running | ✅ `service mysql status` → running, `mysqladmin ping` → `mysqld is alive` | `evidence/db-connectivity-proof.txt` |
| Real TCP connection (matching the app's exact JDBC path) | ✅ `mysql -h 127.0.0.1 -P 3306 -u root --password=''` connects | same |
| Real query succeeds | ✅ `SELECT VERSION()` → `8.0.46-0ubuntu0.24.04.3` | same |
| `pos_system` database exists | ✅ `SHOW DATABASES` lists it | same |
| Schema created by the app | ⚠️ **Partially** — 79 tables exist (of ~80 expected: 77 entities + `user_roles`/element-collection tables + others), but **2 tables failed to be created** — see §4 | `evidence/defects/tables-after-failed-boot.txt` |

Database tier is genuinely up and reachable. The schema gap is an **application** defect (Hibernate DDL generation), not a database-tier problem — proven by reproducing the identical failure directly against MySQL with no application involved at all (§4).

## 3. Backend — build succeeded, boot does not stay up

- ✅ `mvn package` succeeds cleanly (tests skipped for deploy speed only — the full suite was already run and reported in this session's development work, 419/419 passing at the unit level; re-running the full suite is Phase 4 scope).
- ✅ Spring context **does** fully initialize: Hibernate connects, Tomcat binds port 9090, JWT/security beans wire up, WebSocket broker starts, and the log even reaches `Started HybridApplication in 24.013 seconds`.
- ❌ **Immediately after that line**, an `ApplicationRunner` (`GLSeedRunner`, `@Order(100)`) that Spring Boot always invokes right after startup throws an uncaught exception, which — per standard Spring Boot `ApplicationRunner` semantics — aborts `SpringApplication.run()` and **terminates the entire process**. The port is unbound again within ~1 second of "Started". By the time any HTTP client (including this QA session's own `curl`) could reach it, the process is already gone.
- **This reproduces on every single boot attempt against a fresh `pos_system` database** — not a one-off flake. Confirmed: process exits (`ps -p <pid>` → not found), port 9090 not listening, log ends with `Application run failed` and a graceful-shutdown sequence.

## 4. Root cause (fully isolated and independently confirmed, not just inferred from logs)

Two Hibernate-generated `CREATE TABLE` statements are **syntactically invalid MySQL** because they use unquoted column names that collide with MySQL 8.0 reserved words:

```
create table gl_journal_number_counter (id bigint not null, last_value bigint not null, primary key (id)) engine=InnoDB
  → ERROR 1064: syntax error near 'last_value bigint not null, ...'

create table notifications (..., read bit not null, ...) engine=InnoDB
  → ERROR 1064: syntax error near 'read bit not null, ...'
```

**Independently reproduced with no application involved**, directly against the same MySQL instance:
```sql
mysql> CREATE TABLE t (id INT, last_value INT);
ERROR 1064 (42000): You have an error in your SQL syntax ... near 'last_value INT)'
mysql> CREATE TABLE t (id INT, `last_value` INT);   -- backtick-quoted
Query OK   -- succeeds
mysql> CREATE TABLE t (id INT, `read` BIT);          -- backtick-quoted
Query OK   -- succeeds
```
`LAST_VALUE` (a window function, reserved since MySQL 8.0) and `READ` (reserved for `LOCK TABLES ... READ`) are both real MySQL 8.0 reserved words. Hibernate is not quoting these identifiers, so their `CREATE TABLE` statements fail outright — the tables are simply never created.

**Why this crashes the whole app, not just those two features**: `GLSeedRunner.run()` unconditionally calls `journalNumberCounterRepository.findById(1L)` on every boot, with no try/catch. Since `gl_journal_number_counter` doesn't exist, this throws `SQLGrammarException` → `ApplicationRunner.run()` propagates it → Spring Boot's `callRunners()` treats this as a fatal startup failure → `SpringApplication.run()` throws → the JVM process exits.

**Net effect: this backend cannot successfully boot against a genuinely fresh MySQL 8.0 database, at all, under any request path — not "GL features are broken," but "the process will not stay running."** This is consistent with (and explains) something this session's own development history already shows: every prior QA/test run this session found *no live MySQL available* — this defect has, as far as I can tell, never actually been observed before, because the app has never previously been booted against a real, freshly-created MySQL 8.0 schema. Code review and 419 passing unit tests (all of which mock the database) could not have caught this class of defect.

Full evidence:
- `evidence/defects/DEFECT-001-full-startup-log.log` — complete backend log, this exact boot
- `evidence/defects/DEFECT-001-schema-creation-failure.log` — excerpted failing DDL statements
- `evidence/defects/tables-after-failed-boot.txt` — `SHOW TABLES` output (79 tables; `gl_journal_number_counter` and `notifications` absent)
- `evidence/db-connectivity-proof.txt` — direct DB proof, and the isolated reserved-word repro shown above

This is filed formally as **DEFECT-001 (Critical)** in `defects.md` (Phase 5), but is being surfaced now, per the task's own instruction for an undeployable system: *"If the system cannot be deployed after reasonable environment-level troubleshooting, STOP... Report precisely: what failed; command used; error observed; evidence; likely root cause; what is required to continue."*

## 5. Resolution — Option B authorized and applied

You chose Option B: apply QA-environment-level workarounds (not touching either repo) so the
rest of the system gets real coverage, with everything logged for you to fix upstream. Full
detail, exact commands, and the recommended real fix for each defect are in `workarounds.md`.
Summary:

- **DEFECT-001** (backend can't stay up) → **WA-001**: launch the backend with
  `-Dspring.jpa.properties.hibernate.globally_quoted_identifiers=true` (a JVM system property,
  zero file changes in `erp`). This makes Hibernate quote every SQL identifier, so the
  `last_value`/`read` reserved-word collision never manifests in either DDL or DML. Confirmed:
  zero SQL errors on a full fresh boot, backend stays up indefinitely.
- **DEFECT-002** (frontend can't build) → **WA-002**: `npm install --no-save <13 packages>` in
  the frontend checkout (confirmed via `git status`: `package.json`/`package-lock.json`
  untouched). `next build` then completes cleanly, 61/61 routes generated.

## 6. Post-workaround verification (real evidence, not re-asserted from §2–4)

| Check | Result | Evidence |
|---|---|---|
| Backend stays running after "Started" | ✅ confirmed alive 4s, then continuously, after `Started HybridApplication` | `logs/backend.log` — zero `SQLSyntaxErrorException` in the full log |
| `notifications` FK correctly created this time | ✅ `notifications.recipient_id → user_accounts.id` (missing from my first manual attempt — Hibernate's own DDL is authoritative once quoting works) | `SHOW CREATE TABLE notifications` output, captured during this run |
| Admin bootstrap seed runs cleanly | ✅ `qa_admin` (ADMIN role), base currency `USD` inserted, idempotency-guarded | `seed/00-bootstrap-admin.sql` output |
| Real endpoint responds with real DB data | ✅ `GET /api/currencies` → `200`, returns the exact seeded `USD` row | inline curl output, this session |
| Login works with the seeded test account | ✅ `POST /api/auth/login` → `200`, valid JWT with `"roles":["ADMIN"]` | inline curl output |
| DB connectivity proven through an app operation | ✅ (same as above — the currency data round-tripped through the app, not a direct DB read) | same |
| Authenticated request to a protected endpoint | ✅ `GET /api/shops` with `Authorization: Bearer <token>` → `200` | inline curl output |
| **Observation, not yet a filed defect** — `GET /api/shops` also returns `200` **without** any token | Consistent with this session's own prior, documented decision to leave read-only catalog endpoints open while the broader `SecurityConfiguration` `/**` gap remains (00-recon.md §8) — carried into Phase 3 as an explicit test case rather than asserted here either way | inline curl output |
| **Observation** — the seeded `qa_admin` (role `ADMIN` only) gets `403` on `GET /api/users/me` (`hasRole('USER')`) | Expected given the seeded data (no role hierarchy — ADMIN does not imply USER); not itself a defect, but shapes the Phase 3 role-matrix test cases | inline curl output |
| Frontend built | ✅ `next build` → `✓ Compiled successfully`, 61/61 static routes | `npm run build` output, this session |
| Frontend served | ✅ `npm start` → `Ready`, `GET /` → `200` | inline curl output |
| Browser can open the application | ✅ real headless Chromium (Playwright), `/login` loads, title "POS System" | `evidence/phase2-_login.png` |
| Zero uncaught console errors on initial load | ✅ on `/login` (the real, unauthenticated entry point) — zero console errors, zero page errors | `scripts/phase2-frontend-check.mjs` output |
| (For contrast, not a Phase 2 pass/fail) `/dashboard` visited with no session | ⚠️ 403s logged to console fetching dashboard data (no token) — carried into Phase 3 as a real test case (does it redirect to `/login` as intended, or fail silently?) | `evidence/phase2-_dashboard.png`, console log |

## 7. Deliverables produced

- `./qa/docker-compose.qa.yml` + `./qa/Dockerfile.backend` + `./qa/Dockerfile.frontend` — document the intended three-tier containerized topology for a network where Docker Hub is reachable. **Not executed in this sandbox** (§0) — includes comments explaining exactly why, and the same two known limitations (reserved-word tables, hardcoded `localhost` URLs) called out for that context too.
- `./qa/seed/00-bootstrap-admin.sql` — direct-SQL seed for the first `UserAccount` (ADMIN) and base `Currency`. **Run successfully** against the live backend (§6).
- `./qa/env-notes.md` — generated throwaway credentials, now with real accounts created.
- `./qa/workarounds.md` — the authoritative record of every environment-level workaround, why it was needed, and the exact recommended fix for the repos.

## 7. Known warnings observed (non-blocking, noted for completeness)

- `spring.jpa.open-in-view is enabled by default` — Spring Boot informational warning, not an error.
- `HHH90000025: MySQLDialect does not need to be specified explicitly` — Hibernate deprecation notice for an explicit config line in `application.properties`; cosmetic.
- `Global AuthenticationManager configured with an AuthenticationProvider bean. UserDetailsService beans will not be used...` — Spring Security informational warning; consistent with this app's custom `AuthenticationProvider` bean and not itself an error.

---

*End of Phase 2. All three tiers deployed and proven. Proceeding to Phase 3 (test plan).*
