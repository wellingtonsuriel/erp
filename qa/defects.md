# Defect Register

All defects below were found by executing real requests/UI actions against the real,
unmodified application code (per `test-script.md`), with evidence saved under `evidence/`.
None of these were "fixed" in either repository — per the standing instruction for this
engagement, the application itself was never modified. Where a QA-environment-level
workaround exists to keep testing moving, it is documented separately in `workarounds.md`,
never in the repos.

Severity scale: **Critical** (blocks a core feature or is a serious security hole) /
**High** (a real defect with meaningful business impact) / **Medium** (a real defect,
narrower impact) / **Low** (minor/cosmetic).

---

## DEFECT-001 — Backend cannot boot against a fresh MySQL 8 database (Critical)

- **Layer:** Backend / Hibernate schema generation
- **Module:** `GLSeedRunner`, `JournalNumberCounter`, `Notification`
- **Environment:** Fresh MySQL 8.0.46, `ddl-auto=update`, no other config changes
- **Repro:** Start the application against any brand-new, standards-compliant MySQL 8
  database.
- **Expected:** Application starts successfully.
- **Actual:** Hibernate emits unquoted DDL/DML for two MySQL 8 reserved-word
  columns — `last_value` (`gl_journal_number_counter`) and `read` (`notifications`) —
  causing `SQLSyntaxErrorException`. `GLSeedRunner` (an unconditional, uncaught
  `ApplicationRunner`) then crashes the entire JVM on every boot.
- **Evidence:** `evidence/defects/DEFECT-001-full-startup-log.log`,
  `evidence/defects/DEFECT-001-schema-creation-failure.log`,
  `evidence/defects/tables-after-failed-boot.txt`
- **QA workaround applied:** WA-001 (see `workarounds.md`) — launched with
  `-Dspring.jpa.properties.hibernate.globally_quoted_identifiers=true`.
- **Real fix for the repo:** add
  `spring.jpa.properties.hibernate.globally_quoted_identifiers=true` to
  `hybrid/src/main/resources/application.properties` (one line, no entity changes,
  fixes this class of bug everywhere).
- **Reproducibility:** 100%, every cold boot against a fresh database.

---

## DEFECT-003 — Missing `-parameters` javac flag breaks every named-parameter binding (Critical)

- **Layer:** Backend / build configuration
- **Module:** Project-wide — confirmed on `@PathVariable` controller methods (~240
  occurrences across the codebase) AND on `@Query`-annotated repository methods with
  unnamed parameters (e.g. `JournalNumberCounterRepository.findByIdForUpdate`)
- **Repro (path variable):** `GET /api/shops/count/type/RETAIL` with any valid token.
- **Repro (named query):** `POST /api/inventory-adjustments` (or any other GL-posting
  write — manual journals, etc.) with a valid, complete payload.
- **Expected:** Normal successful response.
- **Actual:** `IllegalArgumentException: Name for argument of type [ShopType] not
  specified...` (path variable case) or `InvalidDataAccessApiUsageException: For
  queries with named parameters you need to provide names for method parameters...`
  (named query case), both HTTP 500. The named-query manifestation breaks
  `GLNumberingService.nextEntryNumber()`, which every GL-posting write path calls —
  so **this one root cause took down all inventory adjustments and all manual journal
  submit/approve/post actions**, and independently pre-empts `@PreAuthorize` checks on
  every affected `@PathVariable` endpoint (the crash happens during Spring MVC argument
  resolution, before method-level security AOP runs), which also blocked live
  verification of a security fix on the Zimra fiscalise endpoint until worked around.
- **Root cause:** `hybrid/pom.xml`'s `maven-compiler-plugin` configuration does not pass
  `-parameters` to `javac`, and none of the affected methods use explicit
  `@PathVariable("name")` / `@Param("name")` annotations as a fallback.
- **Evidence:** `evidence/defects/DEFECT-003-pathvariable-enum-full.log`,
  `evidence/defects/DEFECT-003-glnumbering-findbyidforupdate.log`,
  `evidence/api/TC-API-DEFECT003-1.json`, `evidence/api/TC-API-DEFECT003-2.json`
- **QA workaround applied:** WA-003 (see `workarounds.md`) — rebuilt the (unmodified)
  jar with `-parameters` applied via a direct `javac` invocation, after discovering
  that `-Dmaven.compiler.parameters=true` on the Maven command line silently did not
  translate into the actual compiler invocation with this project's resolved plugin
  version.
- **Real fix for the repo:** add an explicit
  `<compilerArgs><arg>-parameters</arg></compilerArgs>` entry to the
  `maven-compiler-plugin` configuration in `hybrid/pom.xml` (the plain
  `<parameters>true</parameters>` boolean was proven insufficient in this project's
  resolved plugin version — see `workarounds.md` WA-003 for the exact reasoning).
- **Reproducibility:** 100%, every affected endpoint, every request, until rebuilt.

---

## DEFECT-008 — Online order creation is completely broken: circular JPA reference crashes the transaction (Critical)

- **Layer:** Backend
- **Module:** `OrderService.createOrderFromCart`, `Order`/`OrderLine` entities
- **Repro:** `POST /api/orders` with a valid cart, valid shipping address/payment
  method, and a real selling price configured for the product.
- **Expected:** HTTP 201, a real `Order` row created, stock reserved, GL posted.
- **Actual:** HTTP 400 with an **empty body**, and — despite backend log lines that look
  like success ("Reserved 1 units...", "Created accounting entries for order N...",
  "GL: posted entry #N...") — the entire operation is silently rolled back: no `Order`
  row, no reservation, no GL entry ever actually persists. Reproduced with 100%
  consistency; **no online order can ever be created via this endpoint as currently
  written.**
- **Root cause:** `OrderService.createOrderFromCart` (line ~140) passes the raw `Order`
  JPA entity directly to `messagingTemplate.convertAndSend("/topic/orders",
  savedOrder)`. `Order.orderLines` and `OrderLine.order` form a bidirectional
  reference with no `@JsonIgnore`/`@JsonManagedReference`/`@JsonBackReference` and no
  DTO boundary, so Jackson (via the STOMP message converter) recurses until it hits
  `StreamWriteConstraints.maxNestingDepth` (1000) and throws
  `MessageConversionException`. Because this happens *inside* the method's own
  `@Transactional` boundary, the whole transaction rolls back. The controller's
  `catch (RuntimeException e)` then swallows the exception with zero logging and
  returns a bare 400, so nothing in the server logs points at the real cause without
  raising the Spring transaction log level (a QA-only diagnostic step, not a repo
  change — see `workarounds.md`).
- **Evidence:** `evidence/api/TC-API-025a-response.json`,
  `evidence/defects/DEFECT-008-order-circular-reference.log`
- **QA workaround:** none applicable — this is a genuine application logic defect, not
  a build/config/environment issue. There is no JVM flag, Maven flag, or Jackson
  property that fixes a bidirectional entity cycle without either annotating the
  entities or introducing a response DTO — both are source changes, out of scope for
  this engagement. TC-API-025 is recorded as **FAIL**; TC-API-026/027, which depend on
  a successful order existing, are recorded as **BLOCKED** downstream of this failure.
- **Real fix for the repo:** either (a) return/broadcast a DTO instead of the raw
  `Order` entity (preferred — also fixes the same class of bug for any other endpoint
  that might one day serialize `Order` directly), or (b) add
  `@JsonIgnore`/`@JsonBackReference` to `OrderLine.order` (quick, narrower fix, but
  leaves the entity itself unsafe to serialize from any other future call site).
- **Reproducibility:** 100%, every attempt.

---

## DEFECT-006 — Cart/CartItem circular JPA reference corrupts every non-trivial Cart API response (Critical)

- **Layer:** Backend
- **Module:** `Cart`/`CartItem` entities, `CartController`
- **Repro:** `POST /api/cart/items` or `GET /api/cart` for any user whose cart contains
  at least one item.
- **Expected:** A normal, finite JSON response describing the cart and its items.
- **Actual:** The response is a ~83KB, 1000-levels-deep nested JSON document (verified:
  1000 open braces / 1000 close braces, the BCrypt password hash of the cart's owner
  repeated 333 times) that gets truncated mid-stream once Jackson's own safety cap
  (`StreamWriteConstraints.maxNestingDepth = 1000`) trips, throwing
  `HttpMessageNotWritableException` **after** the HTTP status/headers were already
  committed to the client — so `POST /api/cart/items` returns a "successful" 201 (and
  `GET /api/cart` a "successful" 200) with a body that is neither valid JSON nor
  usable by any real client (a browser's `fetch`/axios call would throw on
  `.json()`).
- **Root cause:** `Cart.cartItems` (`@OneToMany`) and `CartItem.cart` (`@ManyToOne`,
  no `fetch = LAZY`, so EAGER by default) form a fully-initialized bidirectional
  reference with no cycle-breaking annotation and no DTO boundary — the same defect
  family as DEFECT-008, but for Cart instead of Order.
- **Security note:** because `Cart.user` is serialized in full at every recursion
  level, the cart owner's **BCrypt password hash** is embedded hundreds of times in a
  single API response body.
- **Evidence:** `evidence/defects/DEFECT-006-cart-circular-serialization-response.txt`,
  `evidence/defects/DEFECT-006-backend-log.txt`
- **Real fix for the repo:** same remedy family as DEFECT-008 — a response DTO for
  Cart/CartItem (preferred, also stops leaking the password hash), or
  `@JsonIgnore`/`@JsonBackReference` on `CartItem.cart`.
- **Reproducibility:** 100%, any cart with ≥1 item.

---

## DEFECT-005 — `CartService.getOrCreateCart` can create a duplicate `Cart` row for a user with an empty cart (High)

- **Layer:** Backend
- **Module:** `CartRepository.findByUserIdWithItems`, `CartService.getOrCreateCart`
- **Repro:** A user with an existing, empty `Cart` (0 `CartItem` rows — e.g. one whose
  cart was previously cleared, or one that was created some other way with no items
  yet) calls `POST /api/cart/items`.
- **Expected:** The existing cart is found and the item is added to it.
- **Actual:** `SQL Error 1062: Duplicate entry '<user_id>' for key
  'carts.UK...'` — `CartService.getOrCreateCart` incorrectly concludes no cart exists
  and tries to insert a second one.
- **Root cause:** `CartRepository.findByUserIdWithItems` uses `JOIN FETCH
  c.cartItems` (an **inner** join). For a cart with zero items, the inner join
  returns no rows at all, so `getOrCreateCart`'s `.orElseGet(() -> createCart(user))`
  fires even though a `Cart` row already exists, and the unique constraint on
  `carts.user_id` rejects the resulting duplicate insert.
- **Evidence:** captured live in `logs/backend.log` at 12:23:07 during this session
  (`Duplicate entry '2' for key 'carts.UK64t7ox312pqal3p7fg9o503c2'`); reproduced
  against `qa_user` (id=2), whose cart (id=1) had 0 items at the time.
- **QA workaround:** none needed to fix the app — simply used a second, fresh test
  account (`qa_user2`) that had no pre-existing empty cart, to keep the Orders test
  block moving (see `workarounds.md` and `env-notes.md`).
- **Real fix for the repo:** change the query to `LEFT JOIN FETCH c.cartItems`.
- **Reproducibility:** 100%, for any user whose existing cart currently has 0 items.

---

## DEFECT-009 — Notifications endpoints trust a client-supplied `userId`: cross-user IDOR (Critical)

- **Layer:** Backend / access control
- **Module:** `NotificationController` (`GET /api/notifications`,
  `GET /api/notifications/unread`, `GET /api/notifications/unread-count`,
  `POST /api/notifications/{id}/read`)
- **Repro:** Log in as any authenticated `USER`-role account (e.g. `qa_user2`, id=3);
  call `GET /api/notifications?userId=1` (a different user's id, e.g. `qa_admin`).
- **Expected:** HTTP 403, or the server ignores the `userId` param and uses the JWT's
  own identity.
- **Actual:** HTTP 200 with the target user's full private notification content.
  Proven live end-to-end: a notification was seeded for `qa_admin` (id=1) with the
  message "Confidential: admin-only test message for DEFECT-009 proof"; `qa_user2`
  (id=3, `USER` role) successfully **read** it via `?userId=1`, then successfully
  **marked it as read** via `POST /api/notifications/1/read` — both actions on a
  notification belonging to a completely different account, with no ownership check
  at any point.
- **Root cause:** The controller is annotated only `@PreAuthorize("isAuthenticated()")`
  at the class level (any logged-in user, any role) and every method takes `userId`
  as a plain client-supplied `@RequestParam`/uses it with no comparison against the
  authenticated principal.
- **Evidence:** `evidence/api/DEFECT-009-idor-notifications-response.json`
- **Real fix for the repo:** derive the acting user from the authenticated principal
  (`@AuthenticationPrincipal`) server-side instead of trusting the `userId`
  parameter, the same pattern already correctly used elsewhere in this codebase for
  manual journal actor resolution (see TC-API-017).
- **Reproducibility:** 100%, any two accounts.
- **Note:** the Notification feature currently has no caller anywhere in the codebase
  that actually creates a `Notification` row — it appears to be wired but unused in
  production code paths today, which somewhat limits real-world exposure until
  something starts creating notifications, but the access-control gap is real and
  should be fixed regardless.

---

## DEFECT-011 — Web login is completely broken: admin login form calls the wrong backend endpoint (Critical)

- **Layer:** Frontend
- **Module:** `erp-frontend/lib/auth-context.tsx`, `login()` (used by `/login`'s admin
  form)
- **Repro:** Open `/login` in a real browser, enter any correct `UserAccount`
  username/password (verified against the real, working `POST /api/auth/login`
  endpoint), submit.
- **Expected:** Redirects to `/dashboard` with a valid session.
- **Actual:** Always fails with "Invalid credentials" / HTTP 401, regardless of how
  correct the credentials are. **No admin or user account can ever log into the web
  application through this form.**
- **Root cause:** `login()` sends the request to `POST
  http://localhost:9090/api/cashiers/authenticate` (the **Cashier** PIN/employeeId
  authentication endpoint) instead of `POST /api/auth/login` (the real `UserAccount`
  endpoint, confirmed correct and working via 40+ passing direct API tests this
  session). No `Cashier` record exists with a `UserAccount`'s username, so the
  backend correctly rejects the request — the backend is not at fault.
- **Bounding check:** manually placing a token obtained directly from the real
  `POST /api/auth/login` into `localStorage` and loading `/dashboard` renders a fully
  correct, fully functional dashboard with accurate live data — proving the rest of
  the authenticated frontend (dashboard, inventory, manual journals, at minimum) is
  correctly built and wired; the defect is narrowly confined to this one function.
- **Evidence:** `evidence/defects/DEFECT-011-login-wrong-endpoint.log`,
  `evidence/ui/TC-UI-002-after-login.png`,
  `evidence/ui/manual-token-dashboard.png` (working dashboard once the broken login
  is bypassed)
- **Real fix for the repo:** change `login()` in `lib/auth-context.tsx` to call
  `POST /api/auth/login` and map its real response shape (`{token, username, roles,
  permissions}`) instead of the Cashier-shaped response it currently expects.
- **Reproducibility:** 100%, every login attempt, every account.
- **Impact:** this is the single highest-impact defect found in the entire QA run —
  as shipped, **the web application is unusable by anyone**, even though both the
  backend API and the rest of the frontend are largely correct.

---

## DEFECT-002 — Frontend production build fails outright (High)

- **Layer:** Frontend / build
- **Module:** `package.json` dependencies vs. `components/ui/*.tsx` imports
- **Repro:** `npm ci && npm run build` from a clean clone.
- **Expected:** Successful production build.
- **Actual:** `Module not found: Can't resolve '@radix-ui/react-popover'` (and, once
  that's resolved, a cascade of further missing modules).
- **Root cause:** `package.json` is missing ~13 packages
  (`@radix-ui/react-popover`, `@radix-ui/react-tooltip`, `@radix-ui/react-switch`,
  `@radix-ui/react-toggle-group`, `@radix-ui/react-toggle`, `react-day-picker`,
  `embla-carousel-react`, `recharts`, `vaul`, `react-hook-form`, `input-otp`,
  `react-resizable-panels`, `sonner`) that the shadcn/ui component set under
  `components/ui/*.tsx` actually imports.
- **Evidence:** `01-deploy.md` §4 (original failure), `workarounds.md` WA-002
- **QA workaround applied:** WA-002 — `npm install --no-save <13 packages>`.
- **Real fix for the repo:** add the 13 packages to `package.json`'s `dependencies`.
- **Reproducibility:** 100%, any clean install.

---

## DEFECT-004 — Missing-required-field validation surfaces as 500 instead of 400 (Medium)

- **Layer:** Backend
- **Module:** `ProductController.createProduct` / `ProductService.createProduct`
- **Repro:** `POST /api/products` with the `name` field omitted.
- **Expected:** HTTP 400 with a clear validation message.
- **Actual:** HTTP 500 — `ProductService.createProduct` throws a plain
  `IllegalArgumentException`, and `ProductController` has no `try/catch` for it
  (unlike, for comparison, `InventoryAdjustmentController`, which correctly catches
  the equivalent exception and returns 400 with a real message — see TC-API-020/021).
- **Evidence:** `evidence/api/TC-API-032-response.json`
- **Real fix for the repo:** add the same `catch (IllegalArgumentException e) ->
  ResponseEntity.badRequest()` pattern already used in `InventoryAdjustmentController`.
- **Reproducibility:** 100%.
- **Note:** not exhaustively checked across every other controller given time
  constraints — this may be a systemic pattern gap worth an audit, not just a
  single-controller fix.

---

## DEFECT-010 — Creating a new base currency silently demotes the existing one with no confirmation or audit trail (High)

- **Layer:** Backend / business rule
- **Module:** `CurrencyController` / currency creation
- **Repro:** With `USD` already seeded as the base currency, `POST /api/currencies`
  as ADMIN with `{"code":"EUR", ..., "baseCurrency": true}`.
- **Expected:** Either rejected (409/400, "a base currency already exists"), or some
  explicit, deliberate re-designation flow.
- **Actual:** HTTP 201 — the new currency is created, and the **existing** base
  currency is silently flipped to non-base in the same call
  (`currencies.is_base_currency`: USD `1`→`0`, EUR `0`→`1`), with no confirmation, no
  warning, and no visible audit trail of the change.
- **Why this matters:** every previously-posted GL amount's reporting-currency
  meaning is implicitly anchored to whichever currency is currently flagged as base;
  a single unprotected "create currency" call can retroactively change that anchor
  for the whole system.
- **Evidence:** `evidence/api/TC-API-034-response.json`
- **Real fix for the repo:** require an explicit, separate "designate base currency"
  action (with its own authorization/confirmation and an audit log entry) rather than
  letting a plain currency-create request silently reassign it as a side effect.
- **Reproducibility:** 100%.

---

## Summary table

| ID | Severity | Area | One-line summary |
|---|---|---|---|
| DEFECT-001 | Critical | Backend boot | Fresh MySQL 8 + reserved-word columns crash startup on every cold boot |
| DEFECT-003 | Critical | Backend / build | Missing `-parameters` flag breaks path variables AND named JPQL queries, incl. all GL posting |
| DEFECT-008 | Critical | Backend / Orders | Circular Order/OrderLine reference makes online order creation 100% non-functional |
| DEFECT-006 | Critical | Backend / Cart | Circular Cart/CartItem reference corrupts every non-empty cart response + leaks password hash |
| DEFECT-011 | Critical | Frontend / Login | Admin login form calls the wrong backend endpoint — nobody can log into the web app |
| DEFECT-009 | Critical | Backend / AuthZ | Notifications endpoints let any authenticated user read/modify any other user's notifications (IDOR) |
| DEFECT-002 | High | Frontend / build | `next build` fails from a clean clone — 13 missing dependencies |
| DEFECT-005 | High | Backend / Cart | `getOrCreateCart` can create a duplicate Cart row for a user with an empty cart |
| DEFECT-010 | High | Backend / Currency | Creating a currency can silently reassign which one is "base" with no confirmation |
| DEFECT-004 | Medium | Backend / Products | Missing required field surfaces as 500 instead of 400 |
