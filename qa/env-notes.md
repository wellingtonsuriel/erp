# QA Environment Notes — Generated Throwaway Credentials

**No `.env` or credential source was supplied for this exercise.** Per the task's fallback
instruction, all credentials below were freshly generated for this QA run only, are not used
anywhere else, and are safe to publish in this repo-adjacent QA artifact. None are production
secrets. Regenerate before any reuse.

## Backend runtime configuration

| Variable | Value | Notes |
|---|---|---|
| `JWT_SECRET` | `35f7de89bc0db56a3e24c3556edbf54278ae93803e67e0e907bbd7ec25586886` | Generated via `openssl rand -hex 32`. Overrides the app's insecure hardcoded dev default (see `00-recon.md` §7). |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | Left at documented default; a shortened value is used for one specific token-expiry test case in Phase 3. |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/pos_system` | Hardcoded in `application.properties`, not overridden. |
| `spring.datasource.username` | `root` | Hardcoded in `application.properties`, not overridden. |
| `spring.datasource.password` | *(empty string)* | Hardcoded in `application.properties`, not overridden. MySQL's `root` account was reconfigured (QA-database-side only) to accept TCP login with this blank password, matching what the unmodified application already expects — see `01-deploy.md` §1. **This is an inherited application default, not a QA-introduced weakening.** |
| `app.upload.dir` | `/home/user/qa/uploads` | QA-local file storage path (local-disk storage, per `00-recon.md` §13). |

## Database (QA-local MySQL 8.0.46, installed via apt)

| Item | Value |
|---|---|
| Host | `127.0.0.1` / `localhost` |
| Port | `3306` |
| Database | `pos_system` |
| Admin user | `root` (TCP, blank password — see above) |

## Application test accounts (seeded — see `seed/00-bootstrap-admin.sql`)

| Account | Username | Password | Role | Notes |
|---|---|---|---|---|
| QA Admin (`UserAccount`) | `qa_admin` | `ufsgaJ5huUfyFiQtb/Xln+oG` | `ADMIN` | Bootstrapped directly via SQL — no self-service path to ADMIN exists (`00-recon.md` §6). BCrypt hash generated with the app's own `BCryptPasswordEncoder` (see seed script for exact command). |
| QA regular user (`UserAccount`) | `qa_user` | `Nq7vLp2vJmR9wTse` | `USER` | For negative/unauthorized-role test cases. Created via `POST /api/users/register` once the backend is reachable (not via SQL — this path is self-service by design). |
| QA Cashier | `qa_cashier01` | `Kd8mXz4pQwYh` | `CASHIER`, PIN `4821` | Created via `POST /api/cashiers/register` (requires the QA Admin's JWT) once the backend is reachable. |
| QA regular user #2 (`UserAccount`) | `qa_user2` | `Nq7vLp2vJmR9wTse` (same password/hash as `qa_user`, reused deliberately) | `USER` | Bootstrapped via direct SQL insert during Phase 4, solely to get a user with **no pre-existing `Cart` row**, after `qa_user`'s cart was found to already be in a state that reproduces DEFECT-005 (see `defects.md`) on every `addToCart` call. Using a fresh account was the fastest way to keep executing the Orders/Idempotency-Key test block without touching application code. |
| QA Admin #2 (`UserAccount`) | `qa_admin2` | `ufsgaJ5huUfyFiQtb/Xln+oG` (same password/hash as `qa_admin`, reused deliberately) | `ADMIN` | Bootstrapped via direct SQL insert during Phase 4, needed because the maker-checker rule under test (TC-API-016) correctly forbids the same admin from both submitting and approving a manual journal — a second real admin account was required to reach the `approve`/`post` steps of TC-API-015 at all. |

## Other generated values

| Purpose | Value |
|---|---|
| Base currency seeded | `USD`, symbol `$`, `is_base_currency = true` (required — nothing seeds this at boot; `CurrencyService.getBaseCurrency()` throws without it, per `00-recon.md`) |
| QA shop code | `QA01` — "QA Test Shop" |

## Status of use

All accounts above have been exercised against the live backend during Phase 4 execution (DEFECT-001
was worked around per `workarounds.md` WA-001; deployment proof is in `01-deploy.md` §6). Real
login/JWT proof for each account is captured under `evidence/api/`.
