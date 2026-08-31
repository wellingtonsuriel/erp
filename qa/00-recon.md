# Phase 1 — Reconnaissance

**QA root:** `/home/user/qa/` (created for this exercise; not part of either application repo)
**Repositories under test:**
- `/home/user/erp` — backend (branch `claude/erp-accounting-inventory-finance-q9a2di`, also on `main`)
- `/home/user/erp-frontend` — frontend (same branch/main state)

No `.env` or credentials path was supplied. Per the task's fallback instruction, throwaway QA credentials will be generated and recorded in `./qa/env-notes.md` in Phase 2.

---

## 1. System Purpose

A hybrid POS + e-commerce ERP for a multi-shop retail business (README explicitly cites Zimbabwe/ZIMRA compliance). It combines:
- in-store Point of Sale (cashier terminals),
- an online storefront/cart/checkout,
- multi-shop inventory with FIFO valuation,
- procurement (purchase orders → AP),
- sales (→ AR, returns, loyalty),
- a full double-entry General Ledger driving financial reporting (Trial Balance, P&L, Balance Sheet, Cash Flow, VAT Return),
- payroll, fixed assets, multi-currency/FX, and audit/workflow infrastructure.

This is a large, single-tenant monolith, not a set of independent services.

## 2. Architecture

```
Browser ──HTTP──> Next.js frontend (client-side rendered admin/POS pages)
                        │  axios, hardcoded BASE_URL = http://localhost:9090
                        ▼
                 Spring Boot backend (single JVM process, port 9090)
                        │  Spring Data JPA / Hibernate
                        ▼
                    MySQL 8-compatible database ("pos_system")
```

- **No microservices, no message broker, no cache layer, no separate worker process.** Scheduled jobs run in-process inside the same Spring Boot JVM (`@Scheduled`).
- **No real external network integrations.** ZIMRA fiscalisation (`ZimraService`) and file storage (`FileStorageService`) are both fully local simulations — verified by grep: zero usages of `RestTemplate`/`WebClient`/`HttpClient`/any outbound HTTP client anywhere in the backend source. File uploads write to a local `uploads/` directory (`app.storage.type=LOCAL` default).
- **WebSocket** (`/ws`, STOMP over SockJS, `SimpMessagingTemplate`) broadcasts inventory/order updates to `/topic/*`. No authentication configured on the STOMP endpoint.

## 3. Services

| Service | Tech | Notes |
|---|---|---|
| Backend | Spring Boot 3.5.3 / Java 17 (build env has Java 21, backward compatible) | One process, `hybrid-0.0.1-SNAPSHOT.jar` |
| Frontend | Next.js 14.0.4 / React 18 | Client-rendered; `next build`/`next start` |
| Database | MySQL (mysql-connector-j driver; app targets MySQL syntax via Hibernate's `MySQLDialect`) | No Flyway/Liquibase — schema is Hibernate `ddl-auto=update` |

No cache (Redis/Memcached), no queue (RabbitMQ/Kafka/SQS), no separate worker service exist in either repo.

## 4. Ports

| Port | Service | Source |
|---|---|---|
| 9090 | Backend HTTP + WebSocket (`/ws`) | `application.properties: server.port=9090` |
| 3306 | MySQL | `application.properties: spring.datasource.url=jdbc:mysql://localhost:3306/pos_system` |
| 3000 (default) | Next.js dev/prod server | Next.js default; not overridden in `next.config.mjs` |

**Frontend → backend URL is hardcoded**, not env-driven:
```
lib/api.ts:140  const BASE_URL = "http://localhost:9090"
```
This means the backend must be reachable at exactly `localhost:9090` from wherever the browser runs. It also means containerizing frontend and backend on separate Docker networks would break the app unless port-forwarded onto `localhost` on the host running the browser — a real constraint on any container topology (see §16, §20).

## 5. How Services Communicate

- Frontend → backend: REST over HTTP/JSON, axios client, Bearer JWT in `Authorization` header (attached by a request interceptor when a token exists in `localStorage`).
- Backend → database: JDBC via Spring Data JPA/Hibernate, no connection pool tuning visible beyond Spring Boot defaults (HikariCP).
- No backend → backend or backend → third-party service calls exist.

## 6. User Roles

Two distinct, **non-overlapping** authorization models coexist in this codebase:

1. **Back-office `UserAccount`** (JWT-authenticated), roles: `USER`, `ADMIN`, `CASHIER` (enum `Role`), plus fine-grained `AccountingPermission` grants (`GL_VIEW`, `GL_POST`, `AP_APPROVE`, `INVENTORY_ADJUST`, `PERIOD_CLOSE`, …) attached per-user via `UserAccountPermission` and exposed as Spring Security authorities.
2. **`Cashier`** (till/session-based), used by POS checkout and inventory transfers, identified by a `Cashier` id passed directly in request bodies — **not** a `UserAccount` and **not** JWT-authenticated. `CashierController` itself (registering/managing cashiers) *does* require `ADMIN`/`MANAGER` `UserAccount` roles.

New self-registrations (`POST /api/users/register`) always get only `Role.USER` — there is no self-service path to `ADMIN`. Granting a role (`POST /api/users/{id}/roles`) itself requires `hasRole('ADMIN')`, so **the first admin account cannot be created through the API at all** — it must be seeded directly into the database. This is documented here as a required QA seeding step, not treated as a defect (it's a reasonable bootstrap requirement for a real system), but it is a **blocker unless handled explicitly in Phase 2**.

## 7. Authentication Mechanism

- `POST /api/auth/login` (`AuthController`, no `@PreAuthorize`, correctly public) — Spring `AuthenticationManager` + `BCryptPasswordEncoder`, issues a JWT (`jjwt` 0.12.6) containing roles and authorities as separate claims.
- JWT is verified per-request by `JwtAuthenticationFilter`, populating `SecurityContextHolder` before `@PreAuthorize` checks run.
- `jwt.secret` has an insecure hardcoded fallback (`dev-only-insecure-secret-...`) unless `JWT_SECRET` env var is set — the code comments explicitly flag this as dev-only. **QA must set `JWT_SECRET` explicitly** to test the intended production posture, but the fallback also means the app "works" out of the box with a weak secret, which is itself worth noting as a risk if ever deployed without overriding it.
- Frontend stores the token in `localStorage` and attaches it via an axios request interceptor; a 401 response redirects to `/login`.
- POS/cashier flows bypass this entirely (see §6).

## 8. Authorization Model

- Method-level: `@EnableMethodSecurity` + `@PreAuthorize("hasRole('X')")` / `hasAuthority('Y')`, applied per-controller-method. This is the layer actually enforcing access control today.
- URL-level (`SecurityConfiguration`): the filter chain's whitelist array **still contains a literal `"/**"` entry** ahead of `.anyRequest().authenticated()`, which Spring Security matches first — meaning **the URL-level filter chain currently permits every request regardless of authentication**. This is called out in the code's own class comment as a known, deliberate, unresolved gap ("narrowing it is a larger, separate change that needs frontend coordination"). Practically: any endpoint that has **no** `@PreAuthorize` at all is fully open to anonymous callers. Confirmed via grep: `AuthController` (correctly, it's the login endpoint), `POSController` and `InventoryTransferController` (documented as intentionally cashier-session-authenticated, not JWT), and the WebSocket `/ws` endpoint carry no `@PreAuthorize`/auth of any kind.
- This is flagged as the **highest-priority security item to test** in Phase 3/4 — not by inspection, but by firing real unauthenticated requests at the deployed backend.

## 9. Database and Data Model

- MySQL, database name `pos_system`, no schema-namespacing beyond that.
- **77 JPA `@Entity` classes**, auto-DDL'd by Hibernate (`spring.jpa.hibernate.ddl-auto=update`) — there is **no Flyway/Liquibase**, and the one `.sql` file present (`src/main/resources/db/migration/add_total_stock_column.sql`) is a leftover manual script that is **not wired into any migration framework** and is not auto-applied; the column it describes is already part of the current entity mapping, so Hibernate creates it directly.
- Practical implication for QA: **schema comes into existence purely by booting the app against an empty `pos_system` database once.** No separate migration step is required or possible.
- Key table groups (representative, not exhaustive — 77 entities total): `user_accounts`/`user_roles`/`user_account_permissions`, `cashiers`/`cashier_sessions`/`cashier_permissions`, `shops`, `products`/`selling_prices`, `shop_inventories`/`inventory_total`/`inventory_movements` (the authoritative live-stock model — see the repo's own `INVENTORY_MANAGEMENT.md`/`INVENTORY_VALUATION.md`), `orders`/`order_lines`/`carts`, `sales`, `inventory_transfers`, `purchase_orders`/`supplier_invoices`/`supplier_payments`/`supplier_debit_notes`, `customers`/`customer_invoices`/`customer_receipts`/`customer_credit_notes`/`sales_returns`, `gl_accounts`/`gl_journal_entries`/`gl_journal_lines`/`gl_accounting_periods`/`gl_posting_rules`, `manual_journals`, `opening_balances`, `accruals`, `expenses`/`expense_categories`, `fixed_assets`/`asset_depreciations`, `employees`/`payroll_runs`/`payslips`, `currencies`/`exchange_rates`, `taxes`, `fiscal_devices`/`zimra_fiscalisations`, `stored_files`, `audit_log`, `approval_requests`, `notifications`, `idempotent_requests`, `inventory_adjustments`.
- No seed/fixture data exists anywhere in the backend repo beyond the two idempotent `ApplicationRunner`/`@PostConstruct`-driven seeders that run on every boot: the GL chart of accounts (~31 accounts) and GL posting rules. **All business data (shops, products, users, etc.) must be created via QA seeding or the API.**

## 10. Frontend Page/Route Inventory

Next.js App Router, all client components (`"use client"`), 55 routes total. Top level: `/`, `/login`, `/dashboard`, `/pos`, `/pos/setup`. Everything else is under `/admin/*`:

- **Catalog/ops**: `products`, `selling-prices`, `shops`, `shop-inventory`, `inventory`, `inventory-transfers`, `suppliers`, `customers`, `cashiers`, `currencies`, `taxes`, `users`, `orders`, `reports`, `reports/sales`
- **Accounting** (`admin/accounting/*`, 32 routes): dashboard hub, `accounts` (+`accounts/ledger`), `trial-balance`, `ap` (+`aging`,`debit-notes`,`invoices`,`payments`,`statement`), `ar` (+`aging`,`credit-notes`,`invoices`,`loyalty`,`receipts`,`sales-returns`,`statement`), `manual-journals`, `opening-balances`, `accruals`, `posting-rules`, `periods`, `reports` (+`balance-sheet`,`cash-flow`,`profit-and-loss`,`vat-return`), `reconciliation`, `inventory-reconciliation`, `fixed-assets`, `payroll`, `expenses`, `cash-bank`, `ias29`, `audit-log`, `approvals`, `notifications`

Full machine-generated list is reproducible via `find app -iname page.tsx` in `erp-frontend`.

## 11. Backend Endpoint Inventory

**56 `@RestController` classes, 414 `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping`-annotated methods.** Full detail is available at runtime via springdoc-openapi (`springdoc-openapi-starter-webmvc-ui` 2.5.0 is a declared dependency; default paths `/v3/api-docs` and `/swagger-ui.html` are explicitly present in `SecurityConfiguration`'s whitelist), which Phase 2 will use to pull a live, authoritative endpoint list rather than hand-transcribing 414 methods here. A representative module grouping (auth, POS/orders, inventory×4 controllers, procurement/AP, sales/AR, full GL/accounting stack, cash/bank, fixed assets/payroll/expenses, FX/IAS29, tax/ZIMRA, users/permissions, reporting/dashboard, audit/workflow/notifications, file storage, websocket) is documented in this session's own module summary and will be cross-checked against the live OpenAPI spec in Phase 2.

## 12. Background Jobs / Workers

All in-process (`ScheduledJobsService`, no separate worker):
- Reservation-expiry sweep: every 15 minutes (`0 0/15 * * * *`, configurable via `scheduled-jobs.reservation-expiry-cron`)
- Overdue-invoice check: daily at 06:00 (`0 0 6 * * *`)

No queue-backed or externally-triggered jobs exist. Note: the code's own comments state that **accounting period close is never triggered on a timer** — it is always an explicit API call, by design.

## 13. External Dependencies

**None that make real network calls.** Specifically checked and confirmed absent: payment gateways, SMS/email providers, cloud storage (S3 etc.), real ZIMRA tax-authority API, any outbound HTTP client library usage at all. The two things that look like external integrations are both fully local simulations:
- **ZIMRA fiscalisation** (`ZimraService`) — generates fiscal/verification codes and persists `ZimraFiscalisation`/`FiscalDevice` records locally; `zimra.auto-fiscalise=true` by default but nothing calls the real ZIMRA infrastructure.
- **File storage** (`FileStorageService`) — writes to a local `uploads/` directory by default (`app.storage.type=LOCAL`).

## 14. Dependencies That Must Be Stubbed

**None required.** Given §13, there is nothing external to stub for this system to run its full functional surface. This will be re-verified in Phase 2/4 (e.g., confirming no runtime attempt to reach a real ZIMRA host occurs when fiscalisation endpoints are exercised).

## 15. Existing Test Coverage

- **Backend**: 68 test files, 419 test methods (as of the current `main`), almost entirely Mockito-based unit tests against services/controllers directly (no `@SpringBootTest`/`MockMvc` web-layer tests found). Exactly **one** test, `HybridApplicationTests` (`@SpringBootTest`, Spring context load), requires a real MySQL connection at `localhost:3306` per `application.properties` — it cannot run without a live database.
- **Frontend**: **zero** test files of any kind (no `.test.`/`.spec.` files outside `node_modules`). No test runner is configured in `package.json` (`scripts` only has `build`/`dev`/`lint`/`start`).

## 16. Existing Docker/Deployment Support

**None in either repository.** No `Dockerfile`, no `docker-compose.yml`, no Kubernetes manifests, no CI/CD workflow files (`.github/workflows/` contains only third-party npm dependency workflows under `node_modules`, not project workflows) in either repo. All deployment tooling for this QA exercise must be authored from scratch under `./qa/`.

## 17. Known Configuration Requirements

| Variable | Required for | Default if unset | QA action |
|---|---|---|---|
| `JWT_SECRET` | Backend JWT signing | Insecure hardcoded dev string | **Must set explicitly** for a realistic QA run |
| `JWT_EXPIRATION_MS` | Token lifetime | 86400000 (24h) | Leave default, or shorten to test expiry (TC coverage item 18) |
| `spring.datasource.url/username/password` | DB connection | `jdbc:mysql://localhost:3306/pos_system`, `root`, *(blank password)* | Hardcoded in `application.properties`, **not env-overridable as written** — QA must either match this exactly (root user, no password, DB name `pos_system`, port 3306 on `localhost`) or pass `-D` system property overrides at boot |
| Frontend `BASE_URL` | Frontend→backend calls | Hardcoded `http://localhost:9090` | Backend **must** be reachable at exactly that address from the browser's perspective |
| `zimra.*`, `legacy-gl.dual-write-enabled`, `scheduled-jobs.*` | Business behavior | Sensible local defaults present | Leave default; document behavior observed |

## 18. Assumptions Made Because No Specification Exists

*(Every assumption below is explicitly labeled — none are asserted as fact.)*

- **ASSUMPTION A1**: "The repository" means both `wellingtonsuriel/erp` (backend) and `wellingtonsuriel/erp-frontend` (frontend) together, since the frontend is hardwired to call this specific backend and neither is independently a complete system. Both are treated as one system under test.
- **ASSUMPTION A2**: `./qa/` lives at `/home/user/qa/`, a sibling to both repo checkouts, rather than inside either repo — since QA artifacts describing a two-repo system don't belong inside either repo's own tree, and neither repo's README specifies otherwise.
- **ASSUMPTION A3**: Because `spring.datasource.url` is hardcoded (not `${DB_HOST:...}`-style), QA will run MySQL such that it is reachable at exactly `localhost:3306` from the backend process's perspective. If backend and DB run as separate Docker containers, this requires the DB container's port to be published to the host and the backend to run in a network namespace where `localhost:3306` resolves to that published port (e.g., backend on host network, or DB with `network_mode: host`) — this is the same constraint noted for the frontend→backend connection in §4.
- **ASSUMPTION A4**: "Deployable" for this system means: MySQL reachable, backend boots and serves HTTP on 9090, frontend serves HTML/JS on its port and successfully calls the backend. It does **not** require containerization specifically — the task's own Phase 2 language ("where applicable") and the explicit permission to "create and destroy containers" (not a mandate to use only containers) are read as: containers are the default preferred mechanism, but a working, provable, reproducible environment is the actual requirement.
- **ASSUMPTION A5**: Test credentials will be freshly generated (a throwaway admin `UserAccount`, a throwaway `Cashier`, a throwaway JWT secret) and recorded in `./qa/env-notes.md`, since no `.env` or credential source was provided.
- **ASSUMPTION A6**: "Frontend is actually served" will be verified via a real headless browser (Playwright), not just `curl`, since the frontend is entirely client-rendered — a bare HTML response from `curl` proves almost nothing about whether the app actually works.

## 19. Highest-Risk Assumptions

Ranked by how much they could invalidate later phases if wrong:

1. **A3 (network topology for `localhost:3306`/`localhost:9090`)** — if this environment cannot satisfy "backend sees DB at `localhost:3306`, browser sees frontend and frontend sees backend at `localhost:9090`" simultaneously, the "no application code changes" constraint becomes very difficult to honor, since fixing it might otherwise tempt editing `application.properties`/`lib/api.ts`. QA's answer is to run all three processes directly on the same host network namespace (see Phase 2 architecture decision, §20) rather than isolated container networks — deliberately avoiding the temptation to touch app config.
2. **A4 (containers not mandatory)** — if the grader expects a literal, working `docker-compose.qa.yml` that stands up all three tiers via `docker compose up`, and this environment cannot pull public Docker images (see §20 — **already confirmed true, not hypothetical**), that expectation cannot be met as literally written. This is disclosed prominently rather than silently worked around.
3. **UserAccount vs. Cashier dual-auth model (§6)** — assuming POS/cashier-authenticated endpoints being unauthenticated at the JWT layer is intentional (per the code's own comments) rather than a defect could cause under-reporting a real vulnerability if that assumption is wrong. Mitigated by testing both interpretations explicitly in Phase 3/4 and reporting the code's own stated rationale alongside the observed behavior, rather than silently accepting it.

## 20. Potential Blockers to Deployment/Testing

- **CONFIRMED BLOCKER, already diagnosed in this session, before Phase 2 begins**: this sandbox's outbound network policy returns **HTTP 403 (policy denial)** for `production.cloudfront.docker.com` — the CDN Docker Hub uses to serve actual image layer blobs — confirmed via the environment's own proxy diagnostic (`curl http://127.0.0.1:40957/__agentproxy/status`, `recentRelayFailures` → `"kind":"connect_rejected","host":"production.cloudfront.docker.com:443"`). Manifest/API calls to `registry-1.docker.io` itself returned `429 Too Many Requests` rather than a hard block, but the blob layer download is what actually matters and it is explicitly denied, not merely rate-limited. Per this environment's own operating instructions, a 403 policy denial must be **reported, not retried or routed around**.
  - **Practical consequence**: `docker pull` of any public image (verified with both `hello-world` and `mysql:8.0`) cannot complete in this environment today. A `docker-compose.qa.yml` referencing stock Docker Hub images will be authored as a deliverable (documenting the intended containerized topology) but **cannot be executed end-to-end in this sandbox** — this will be stated plainly in `01-deploy.md`, not glossed over.
  - **Planned mitigation** (environment-level, not application-level, per the operating rules): deploy all three tiers as **direct host processes** instead — MySQL via `apt-get install mysql-server` (confirmed reachable: `archive.ubuntu.com`/`security.ubuntu.com` package mirrors work over this same network, unaffected by the Docker registry block), backend via the Maven-built jar (`java -jar`), frontend via `next build && next start`. This is explicitly permitted by the task ("configure the QA environment", "install packages and system dependencies") and keeps the "no application source changes" constraint airtight, at the cost of not literally exercising `docker-compose.qa.yml`.
  - Docker itself (the daemon) **does** work in this environment when started (`dockerd` starts cleanly, `docker ps`/`docker run` against **locally-available** images would work) — only *pulling new public images* is blocked. This will be re-confirmed at the start of Phase 2 in case the policy is session-scoped rather than permanent.
- **The chicken-and-egg ADMIN bootstrap problem (§6)** — requires seeding the first admin `UserAccount` directly into the database (SQL insert with a BCrypt hash matching `BCryptPasswordEncoder`), not through the API. Not a blocker (a known, plannable seeding step), but flagged so it isn't mistaken for an apparent-but-false "you can't create an admin" defect during testing.
- **Hardcoded DB credentials/URL and frontend BASE_URL (§4, §17)** — no blocker by itself, but constrains the deployment topology as described in A3.
- **No frontend test tooling configured** — Playwright must be installed fresh under `./qa/` for this exercise; not a blocker, just scoped work for Phase 4.
- **`HybridApplicationTests` requires live MySQL** — will now be *unblocked* by the direct-MySQL-install mitigation above (unlike the pattern noted in this session's own prior development work, where no DB was ever available) — to be confirmed once MySQL is actually installed and running in Phase 2.

---

*End of Phase 1. Awaiting review before Phase 2 (Build and Deploy) begins.*
