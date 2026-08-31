# QA Environment Workarounds Log

Per explicit authorization, this file tracks every environment-level workaround applied during
this QA run so the application's real defects can be fixed upstream afterward. **No file in
either repository (`erp`, `erp-frontend`) was modified for any of these.** Every workaround
below lives entirely in QA-owned infrastructure (the local MySQL instance, the QA process
environment, or `./qa/`). Each entry names the defect, the exact workaround, and — separately —
the actual fix that belongs in the application source (for you to apply, not applied here).

---

## WA-001 — force Hibernate to quote every SQL identifier (JVM system property, no repo change)

**Blocks:** DEFECT-001 (Critical) — backend cannot complete startup at all against a fresh
MySQL 8 database (see `01-deploy.md` §3–4 for full evidence).

**First attempt (superseded, keeping the record honest):** I initially pre-created just the two
broken tables directly in the QA database with backtick-quoted DDL, matching Hibernate's failed
`CREATE TABLE` statements. The backend then booted, but crashed again seconds later on its
*first write* to `gl_journal_number_counter` — Hibernate's generated `INSERT` statement was
**also** unquoted (`insert into gl_journal_number_counter (last_value,id) values (0,1)`),
hitting the exact same reserved-word syntax error on DML, not just DDL. Pre-creating the tables
was necessary but not sufficient.

**Actual workaround applied** (backend launch command only — no file in either repo touched):
```bash
java -Dspring.jpa.properties.hibernate.globally_quoted_identifiers=true \
     -jar hybrid-0.0.1-SNAPSHOT.jar
```
This is a standard Hibernate configuration property that makes Hibernate quote **every**
identifier in **every** generated statement (DDL and DML alike), so the `last_value`/`read`
reserved-word collision never manifests anywhere. I dropped the two manually-created tables
first and let Hibernate recreate them itself with this flag on — the result is a byte-for-byte
proper schema, including a foreign key (`notifications.recipient_id → user_accounts.id`) that
my manual `CREATE TABLE` had missed. Confirmed **zero** `SQLSyntaxErrorException`/
`CommandAcceptanceException` in the full startup log afterward, and the backend has stayed up
continuously since (see `01-deploy.md` §3 for the health/endpoint proof that followed).

**Real fix required in the repo** (`erp`, not applied here) — pick one:
1. **Simplest, lowest-risk**: add one line to `hybrid/src/main/resources/application.properties`:
   ```properties
   spring.jpa.properties.hibernate.globally_quoted_identifiers=true
   ```
   This is exactly the setting used as the workaround, made permanent. No entity changes, no
   migration risk, fixes this class of bug everywhere in the codebase (not just these two
   tables) in one line.
2. **More invasive alternative**: rename the colliding fields/columns (e.g. `JournalNumberCounter.lastValue` → `counterValue`, `Notification.read` → `isRead`) and/or add explicit
   `@Column(name = "...")` overrides. Not recommended over option 1 unless there's a specific
   reason to avoid globally-quoted identifiers project-wide (e.g. a raw/native SQL query
   elsewhere that assumes unquoted identifiers and would need auditing first).
- Affected entities: `JournalNumberCounter` (`last_value`) and `Notification` (`read`).
- **Priority: highest.** This affects a from-scratch deploy against any genuinely fresh,
  standards-compliant MySQL 8 instance — not an edge case, and not something any of this
  project's 419 existing unit tests could have caught (they all mock the database).

---

---

## WA-002 — install missing npm dependencies without touching `package.json`

**Blocks:** DEFECT-002 (High) — `next build` fails outright: `Module not found: Can't resolve
'@radix-ui/react-popover'`, imported (via `components/ui/popover.tsx`) from
`app/admin/selling-prices/page.tsx`. Not a type-checking issue (the repo's
`next.config.mjs` already sets `typescript.ignoreBuildErrors: true`, which does not affect
webpack module resolution) — this is a hard, fatal build failure with zero workaround at the
application-config level.

**Root cause:** `components/ui/*.tsx` includes a full shadcn/ui component set, but
`package.json`'s `dependencies` never lists the corresponding `@radix-ui/*` (and a few other)
packages for several of those components — they're only reachable because `node_modules`
already happened to contain other packages that don't declare these as their own deps either.
Once resolution actually fails, it cascades: fixing the first reported missing module reveals
the next one in a different chunk.

**Workaround applied**: `npm install --no-save <packages>` (no `package.json` or
`package-lock.json` change — confirmed via `git status` before/after, clean). Installed:
`@radix-ui/react-popover`, `@radix-ui/react-tooltip`, `@radix-ui/react-switch`,
`@radix-ui/react-toggle-group`, `@radix-ui/react-toggle`, `react-day-picker`,
`embla-carousel-react`, `recharts`, `vaul`, `react-hook-form`, `input-otp`,
`react-resizable-panels`, `sonner`. (This list was already known from this session's earlier
`tsc --noEmit` output before this QA exercise began - it fully matched what `next build`
needed; no further missing modules surfaced after installing these.)

**Result**: `npm run build` → `✓ Compiled successfully`, all 61 routes statically generated.

**Real fix required in the repo** (`erp-frontend`, not applied here): add the 13 packages above
to `package.json`'s `dependencies` (with the same versions this environment resolved via
`npm install`, visible in this QA environment's `node_modules/*/package.json` "version" fields,
or simply re-run `npm install <pkg>` for real in the repo to let npm pick current compatible
versions and update `package-lock.json` correctly). **Priority: high** — this is not an edge
case; it is a from-clean-clone, `npm ci && npm run build` failure, i.e. this repository cannot
produce a production build as committed today.

---

## WA-003 — rebuild the jar with javac's `-parameters` flag applied directly (bypassing a Maven Compiler Plugin quirk), no repo change

**Blocks:** DEFECT-003 (Critical) — confirmed to be far larger in blast radius than first scoped. It is not
limited to `@PathVariable`-bound controller methods (~240 occurrences); it also breaks **every JPQL
`@Query` method with an unnamed/unannotated parameter**, including the one backing General Ledger
journal numbering itself:

```java
// JournalNumberCounterRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM JournalNumberCounter c WHERE c.id = :id")
Optional<JournalNumberCounter> findByIdForUpdate(Long id);   // no @Param("id")
```

Without either an explicit `@Param("id")` or compilation with `-parameters`, Spring can't bind the
JPQL `:id` placeholder to the method's sole argument, and throws
`InvalidDataAccessApiUsageException` at call time. Since `GLNumberingService.nextEntryNumber()`
calls this on **every single GL posting** (`GLPostingService.postManual`, called by Inventory
Adjustments, Manual Journals, and any other module that posts to the GL), this defect took down
**all GL-integrated write operations** in the system, not just a subset of `{id}`-style lookups.
Concretely, it is what caused TC-API-018 (Inventory Adjustment, surplus happy path) to return
HTTP 500 — see `evidence/defects/DEFECT-003-glnumbering-findbyidforupdate.log` for the full stack
trace, captured before this workaround was applied. The write was correctly rolled back in full
(confirmed no `inventory_total` row existed afterward), so this manifestation caused no partial/
corrupt state — but it made a core accounting feature completely unusable.

**First attempt (superseded, keeping the record honest):** tried rebuilding with
`mvn clean package -Dmaven.compiler.parameters=true` (a command-line property override — no
`pom.xml` change), on the theory that this is the documented, standard way to turn on `-parameters`
for `maven-compiler-plugin` without editing the POM. This did **not** work: `mvn -X` confirmed the
property resolved to `parameters = true` internally, but the plugin's actual `Command line options`
dump passed to `javac` never included `-parameters` (verified by grepping the full debug log — zero
occurrences — and by disassembling the resulting class files, which had no `MethodParameters`
attribute). This reproduced identically whether compiling in-process or with
`-Dmaven.compiler.fork=true`, and also when trying to force it via
`-Dmaven.compiler.compilerArgs=-parameters`. This appears to be a quirk/bug in how this project's
resolved Maven Compiler Plugin version merges the `parameters` boolean into the final compiler
invocation — not something fixable from the command line.

**Actual workaround applied**: compiled the **unmodified** `src/main/java` tree directly with the
system `javac`, replicating Maven's own classpath, source path, annotation-processor path (Lombok),
and target settings exactly (captured from Maven's own `-X` debug output), with `-parameters` added:
```bash
javac -parameters -g -nowarn --release 17 -encoding UTF-8 \
  -d target/classes \
  -classpath "target/classes:$(find ~/.m2/repository -name '*.jar' | tr '\n' ':')" \
  -sourcepath "src/main/java:target/generated-sources/annotations" \
  -s target/generated-sources/annotations \
  -processorpath ~/.m2/repository/org/projectlombok/lombok/1.18.30/lombok-1.18.30.jar \
  $(find src/main/java -name '*.java')
```
Verified via `javap -v` that the resulting `.class` files now carry a `MethodParameters` attribute
(confirmed on `JournalNumberCounterRepository.findByIdForUpdate`). Then repackaged into the runnable
fat jar **without letting Maven recompile and overwrite this classes directory**:
```bash
mvn package -Dmaven.compiler.skip=true -Dmaven.test.skip=true
```
`-Dmaven.compiler.skip=true` skips only the compile goal (reusing the `javac`-produced
`target/classes` as-is); `resources:resources`, `jar:jar`, and `spring-boot:repackage` still run
normally, producing a jar built from **byte-for-byte the same, untouched source files**, just
compiled with one additional javac flag. `git status` on `erp` was confirmed clean (no changes)
both before and after. Relaunched the backend on this jar (same `globally_quoted_identifiers`
launch property as WA-001) — boots clean, and both the original DEFECT-003 repro
(`GET /api/shops/count/type/RETAIL`) and TC-API-018 now return their correct results (200 and 201
respectively, with a real GL journal entry posted — see `evidence/api/TC-API-018-response.json`).

**Real fix required in the repo** (`erp`, not applied here) — pick one, both fully fix the class of
bug (not just this one method):
1. **Simplest, lowest-risk, recommended**: add `-parameters` to the compiler args in
   `hybrid/pom.xml`'s `maven-compiler-plugin` configuration explicitly, e.g.:
   ```xml
   <configuration>
     <parameters>true</parameters>
     <compilerArgs>
       <arg>-parameters</arg>
     </compilerArgs>
     ...
   </configuration>
   ```
   Adding it as an explicit `<compilerArgs><arg>-parameters</arg></compilerArgs>` entry (not just
   the `<parameters>true</parameters>` boolean) is the important part — as this workaround's first
   attempt discovered, the boolean alone silently did not translate into the flag with this
   project's resolved plugin version, whereas an explicit `-parameters` compiler arg is
   unambiguous.
2. **More targeted, more invasive**: annotate every affected parameter individually — every
   `@Query`/`@Modifying` repository method parameter with `@Param("...")` (starting with
   `JournalNumberCounterRepository.findByIdForUpdate`), and every `@PathVariable`/`@RequestParam`
   controller parameter with its explicit name, e.g. `@PathVariable("id") Long id`. Fixes the same
   defects but must be done at every one of the ~240+ call sites and is easy to miss one; option 1
   fixes all current and future occurrences project-wide in one place.
- **Priority: highest**, tied with DEFECT-001. This is not an edge case: it silently breaks the
  system's core double-entry GL posting path (the highest-value feature verified in this QA run) on
  a totally standard Maven/JDK toolchain, and none of the project's 419 existing unit tests caught
  it (unit tests don't exercise the compiled JAR's method-parameter metadata the way a live JPQL
  named-parameter binding at runtime does).

---

*(Further entries appended below as additional workarounds are needed during this run.)*
