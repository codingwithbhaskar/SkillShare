Content updated below — see Step 11 section and Twelfth gotcha for what changed 2026-09-06 (Step 11 closed: pool-accumulation methodology bug found and fixed, all 3 scales re-run clean, all 6 allocation strategies compared, findings written up as `phase-11-baseline-comparison-results.md`; JDK switched from 25 to 21). See the very end of this file for what changed 2026-09-10 (bulk seed data, GitHub push, deployment prep).

# Where we are, and the concrete next steps (as of 2026-09-10)

## What's actually done (DB layer — v3, verified against a live Postgres 16)

- `01_schema_v3.sql` — 18 base tables + `mv_worker_stats`, exclusion
  constraint for double-booking, all ENUMs.
- `04_triggers_v3.sql`, `05_functions_procedures_v3.sql` —
  `fn_find_candidates`, `fn_score_candidate`, `sp_allocate_worker`, the
  notification outbox triggers, the generic audit trigger.
- `03_views_v3.sql`, `06_seed_data_v3.sql` — 6 views, realistic seed data,
  allocation run against the seed bookings.
- All of the above live-tested; 4 real bugs found and fixed (mv fan-out,
  invalid SAVEPOINT, timezone, enum cast) — see `schema-v3-*.md`.
- ER diagram: editable `SkillShare_ERD_v3.drawio` plus a self-viewable
  rendered version (both attribute-level and entity-only variants) —
  `er-diagram-drawio-v3.md`.
- Architecture diagram (3-tier + notification outbox flow) —
  `system-architecture-diagram.md`.
- Notification system design, location/map feature design — designed,
  schema built, app-layer dispatch job now built and live-verified
  (Phase 5, below).

**This means Open Decisions #1 and #2 from `research-topic-and-springboot-
roadmap.md` §9 are effectively already resolved by what got built**: the
schema targets PostGIS (decision #1: migrate), and all scoring/allocation
math lives in PL/pgSQL, not Java (decision #2: stay in the DB). Nothing
about the app-layer work below should relitigate either — Spring Boot calls
into the existing functions/procedures, it doesn't reimplement them.

## Phase 1 — DONE and verified against real PostGIS (2026-08-26)

The original `01_schema_v3.sql` … `06_seed_data_v3.sql` files described
above turned out to have never actually been saved to disk anywhere — only
narrative descriptions of them existed in this project's docs. They were
**reconstructed from scratch** in a later session, matching every design
decision/bug-fix/constraint documented across `schema-v3-*.md`,
`advanced-dbms-extension-v2.md`, `master-entity-list-v3.md`,
`notification-system-design.md`, and `location-map-feature-design.md`.
Full detail in `phase-1-scaffolding-complete.md`. Summary:

- Spring Boot project scaffolded at `D:\Dev\SkillShare\backend\skillshare-
  backend` (Spring Boot 4.1.1, JDK 21 target / JDK 25 runtime) with all the
  right starters (webmvc, data-jpa, validation, actuator, flyway, security
  + their per-module test companions — verified correct against Spring
  Boot 4's actual modularization, no changes needed).
- `application.yml` / `application-dev.yml` (→ `localhost:5433/
  skillshare_dev`, password via `SKILLSHARE_DB_PASSWORD` env var) /
  `application-test.yml` (→ a docker-compose container on port 5434, kept
  separate from both native Postgres instances).
- `docker-compose.yml` (`postgis/postgis:16-3.5`) for Testcontainers/CI
  parity.
- The 5 SQL files converted into Flyway migrations `V1__schema.sql` …
  `V5__seed.sql` under `src/main/resources/db/migration/`, in the correct
  dependency order (schema → triggers → views → functions/procedures →
  seed — NOT the numeric filename order). Byte-identical to the canonical
  copies kept in `D:\Dev\SkillShare\db\`.
- `backend/frontend/db/docs` folder convention completed.

**Confirmed working end-to-end on Bhaskar's own machine**, not just in a
sandbox: `mvn compile` succeeds; `mvn spring-boot:run` boots the app,
Flyway applies all 5 migrations against the real `skillshare_dev` database
(PostgreSQL 16.15, port 5433, real PostGIS — not a substitute), Hibernate
validates the schema with zero mismatches, and the app starts cleanly on
port 8080. Spot-checked in pgAdmin: booking 4 correctly confirmed to Ravi
Pawar (2-skill match, urgent), booking 5 correctly confirmed to Sunil
Bhosale (closer of two equally-skilled candidates, normal) — exactly the
documented allocation behavior. **This closes the "PostGIS not yet proven
against a real instance" caveat repeated across `schema-v3-*.md`.**

## Phase 2 — DONE, verified end-to-end, and unit-tested (2026-08-26)

19 JPA `@Entity` classes (all 18 tables + `mv_worker_stats`), 19 Spring
Data repositories, 3 `@Embeddable` composite-key classes, 11 enums
mirroring the Postgres native enum types, and the `TraditionalAllocator`
baseline (skill filter → availability filter → schedule-conflict filter →
nearest-by-Haversine) as a `Strategy` bean — the "traditional system" arm
the research write-up compares the intelligent PL/pgSQL allocator against
later. Lombok added to `pom.xml` for entity boilerplate. Full detail in
`phase-2-domain-repository-layer.md`.

## Phase 3 — DONE, verified live, systemic enum bug found and fixed (2026-08-26)

Repository methods wrapping `fn_find_candidates` and a PostGIS radius
search, both exposed via `/api/spatial/...` REST endpoints. Built as
native SQL rather than Hibernate Spatial JTS mapping. A systemic enum-
casing bug (Java enum constants uppercase vs. Postgres native enum labels
lowercase) was found and fixed across the whole domain layer — see
`phase-3-spatial-candidate-retrieval.md`.

## Phase 4 — FULLY DONE AND CLOSED (2026-08-26)

`AllocationScoringService`/`AllocationService` wrap `fn_score_candidate`/
`sp_allocate_worker`. Hardened after a full code review pass: a
concurrent-request race in the pending-status check fixed with
`SELECT ... FOR UPDATE`, and a silent-null-score bug on a nonexistent
worker fixed with a new `WorkerNotFoundException`. 28/28 tests passing.

## Phase 5 — FULLY DONE AND CLOSED (2026-08-30)

Booking lifecycle (create/cancel/start/complete), review submission,
worker dashboard endpoints, notification outbox dispatch job
(`@Scheduled`, polls `vw_pending_notification_deliveries`). 47/47 tests
passing, full lifecycle live-tested including real notification dispatch.

## Phase 6 — Payment gateway: FULLY DONE AND CLOSED (2026-08-30)

Real Razorpay integration, sandbox/test mode. `createOrder` (idempotent),
`verifyCheckout` (HMAC-verified, re-fetches and only completes on
`"captured"`), `handleWebhook` (verified, idempotent). Both order creation
and a full real checkout-to-completion were live-verified against the
real Razorpay sandbox (not mocks) — 63/63 tests passing. Only the webhook
path remained unit-tested-only at the time (needed a public HTTPS URL,
which didn't exist yet — **this is now closable, see the 2026-09-10
deployment section below**, since Render gives a real public URL).

## Phase 7 — Authentication (JWT): FULLY DONE AND CLOSED (2026-08-30)

Real JWT auth — register/login, stateless filter chain, role-based access
(`/api/workers/**` → WORKER/ADMIN, `/api/admin/**` → ADMIN, everything
else → any authenticated user). Two real Spring Security bugs found and
fixed via live testing (401-vs-403 handling, and a `/error`-forward auth
gap matching `spring-projects/spring-boot#31852`). 77/77 tests passing,
full 10-step live test script passing.

## Phase 8 — Frontend: DONE (2026-08-30 → 2026-09-06)

Full role-based React + Vite + Bootstrap SPA consuming the whole REST
API — customer/worker/admin flows, booking creation and lifecycle, worker
profile self-service, reviews, payment checkout via Razorpay Checkout.js.

## Phase 9 — Admin panel: DONE AND CLOSED (2026-09-01)

Admin dashboard, user management, catalog CRUD, reports/analytics, plus a
self-service account-deactivation feature. Two Postgres/Hibernate
parameter-typing bugs found and fixed. Verified via unit tests (126/126)
and real browser click-through.

## Phase 10 — Query optimization benchmark harness: DONE (2026-09-01)

Full 5-scale sweep (1k/5k/10k/50k/100k workers) against a separate
`skillshare_benchmark` database (since retired — see below). Results in
`phase-10-benchmark-results-h3-findings.md`.

## Step 11 — Baseline comparison suite: DONE AND CLOSED (2026-09-06)

6 allocation strategies (random, nearest-worker, rating-only,
simple-weighted, traditional, intelligent) compared across 3 scales on
ASR/AMS/ATD/ART/WU metrics, after fixing a real eval-data pool-
accumulation methodology bug. Full results table and analysis in
`phase-11-baseline-comparison-results.md`. **All 11 phases of the roadmap
were done, closed, and live-verified as of this point.**

## Frontend polish — homepage, footer, DB reset (2026-09-06)

- `skillshare_benchmark` database retired (Phase 10 work complete).
- `skillshare_dev` fully wiped (`TRUNCATE ... RESTART IDENTITY CASCADE`
  across all 18 tables, script at `db/wipe_skillshare_dev.sql`) and
  reseeded from a blank slate. Fresh admin account created via the app's
  own register endpoint (real bcrypt hash): `admin@skillshare.local` /
  `Admin@12345`, `user_id = 1`. **Change that password once logged in.**
- Homepage rebuilt with scroll-reveal animations (`useInView.js`,
  `Reveal.jsx`, `CountUp.jsx`), an auto-advancing image slider of real
  trades photography (`SkillsSlider.jsx`, Unsplash-hotlinked, License-
  compliant), an honest architectural stat strip, and a 4-step "how it
  works" section — all built from existing dependencies, no new npm
  packages.
- `Footer.jsx` added site-wide via `App.jsx`'s new flex-column shell
  (renders on every route without touching individual pages).

## Bulk seed data, GitHub, and deployment prep (2026-09-10)

**1. Large synthetic seed dataset generated, validated, and delivered.**
`db/bulk_seed_large.sql` — 50 skills, 100 services, 2000 customers, 700
workers (with locations/availability/skills/services), 1587 bookings
across all 5 statuses with realistic weighted distributions, plus
payments/reviews/notifications generated automatically via the schema's
own triggers (not inserted directly) rather than fabricated. Real bcrypt
password hashes via Postgres's `pgcrypto` (`crypt(password,
gen_salt('bf'))`) — every account's password is `<FirstName>@123`, email
`<first><last><n>@gmail.com`. **Actually executed end-to-end against a
disposable test database before delivery** (not just written and hoped),
confirming zero booking overlaps and all password hashes verify
correctly. Applied to the live `skillshare_dev` database.

**2. Admin Reports "changes on repeated Apply clicks" investigated.**
Root cause concluded to be the two "Apply" clicks straddling the still-
running bulk seed script (each statement auto-commits), not a UI/backend
bug — the frontend already always sends explicit `from`/`to`. Closed off
a real but previously-dormant risk anyway: `AdminController`'s `/reports`
endpoint now rejects a missing/blank `to` instead of silently defaulting
to `OffsetDateTime.now()` (`AdminService.getReport` no longer has a
`to`-defaulting fallback either). Not yet recompiled/confirmed on
Bhaskar's machine.

**3. Project pushed to GitHub** (public repo), via IntelliJ's "Share
Project on GitHub" after local prep: root-level `.gitignore` added
(`.idea/`, `__pycache__/`, OS junk — `backend/` and `frontend/` already
had their own covering `node_modules`/`target`/`dist`/`.env`), a stray
zero-commit nested `.git` inside `backend/skillshare-backend/` removed
(leftover from Spring Initializr, was blocking the root-level `git add`),
initial commit made locally (273 files, 1.8MB, verified clean — no
`node_modules`/`target`/`.env`/secrets staged) then pushed via IntelliJ.
**Confirmed no real secrets are committed anywhere**: DB password/JWT
secret/Razorpay keys are all `${ENV_VAR}` placeholders in every
`application-*.yml`; `frontend/.env` is gitignored; the only literal
credentials in the repo are `postgres`/`postgres` for the local Docker
Compose *test* container and `password123` in throwaway PowerShell live-
test scripts — neither is a real credential.

**4. Deployment planned and prepped: Neon (DB) + Render (backend) +
Vercel (frontend), all free tiers, no credit card.** Chosen because
PostGIS is the hard constraint most free Postgres hosts fail (Neon
supports it and auto-wakes on connection, unlike Supabase's manual-
resume-after-a-week-idle pause); Vercel cannot run Spring Boot at all (no
JVM runtime, 10s function timeout on free tier, no long-running
processes — confirmed via Vercel's own docs), so Render (Docker-based,
since Render also has no native Java runtime) is the backend host.
Full step-by-step is in `docs/deployment-guide.md`. Code-side prep
delivered to `backend/skillshare-backend/`:

- `Dockerfile` — multi-stage build (JDK to compile, slim JRE to run)
- `.dockerignore`
- `src/main/resources/application-prod.yml` — new `prod` Spring profile:
  DB connection via `SKILLSHARE_DB_URL`/`_USERNAME`/`_PASSWORD` env vars,
  `server.port` reads Render's dynamic `$PORT`, CORS origins via
  `APP_CORS_ALLOWED_ORIGINS` (read by the *existing*
  `SecurityConfig.allowedOrigins` `@Value` binding — no code change
  needed there, it was already env-var-driven), JWT secret via
  `JWT_SECRET`, Razorpay keys via the same env vars as `dev`.

**Not yet committed to git as of this writing** — pending a commit/push
(via IntelliJ, or a Claude Code session with real shell access). Nothing
has been deployed yet; Steps 1-4 in `docs/deployment-guide.md` (Neon
project → Render service → Vercel project → closing the CORS loop) are
all still to do.

## The phase order

1. ~~**Project scaffolding.**~~ **DONE**
2. ~~**Domain & repository layer + baseline allocator.**~~ **DONE**
3. ~~**Spatial candidate retrieval.**~~ **DONE**
4. ~~**Intelligent scoring + allocation.**~~ **DONE, fully closed**
5. ~~**Booking lifecycle, notifications, feedback.**~~ **DONE, fully closed**
6. ~~**Payment.**~~ **DONE, fully closed** (webhook now closable post-deploy)
7. ~~**Authentication (JWT).**~~ **DONE, fully closed**
8. ~~**Frontend.**~~ **DONE**
9. ~~**Admin panel + account self-service.**~~ **DONE, fully closed**
10. ~~**Query optimization benchmark harness.**~~ **DONE, fully closed**
11. ~~**Baseline comparison suite + evaluation.**~~ **DONE, fully closed**

**All 11 phases of the roadmap are done, closed, and live-verified.**
Current focus (2026-09-10) is deployment for free student/demo hosting —
see the section immediately above and `docs/deployment-guide.md`.

## Local machine setup (Bhaskar's laptop, Windows 11)

JDK 21, Maven 3.9.16, PostgreSQL 16 + PostGIS 3.5 on port 5433 (a second,
PostGIS-less PostgreSQL 18 also exists on the default port — deliberately
not used), `skillshare_dev` database, pgAdmin 4, Git, Node.js/npm, Docker
Desktop (WSL2-backed), IntelliJ IDEA 2026.2 Community, a Razorpay test/
sandbox account. All confirmed working. `JWT_SECRET` optional locally
(falls back to a random in-memory key).

**Project folder:** `D:\Dev\SkillShare\` — `backend/`, `frontend/`, `db/`,
`docs/` subfolders, now also a git repo pushed to GitHub, plus
`CLAUDE.md` at the root for Claude Code IDE-integration context.

## Known gotchas (condensed — see git history / earlier doc versions for full write-ups)

1. **Lombok on JDK 25** — annotation-processor auto-discovery doesn't
   reliably pick it up; pin it explicitly as an `annotationProcessorPath`
   in `maven-compiler-plugin`.
2. **Java enum constants must be lowercase**, matching Postgres native
   enum labels exactly (`AuditAction` is the one uppercase exception).
3. **DB-generated columns need `EntityManager.refresh()`** after `save()`
   to read back a Postgres-side `DEFAULT now()` value.
4. **Env vars are terminal/session-scoped** unless set as real Windows
   user environment variables — a fresh terminal or a fresh IntelliJ
   Run Config won't inherit one set elsewhere. If this recurs even after
   setting it in IntelliJ's Run Config field, verify with a temporary
   hardcode test, then set it as a real OS-level env var and fully
   restart IntelliJ.
5. Razorpay's generic `4111 1111 1111 1111` is an *international* test
   card, rejected by an Indian test account — use `4100 2800 0000 1007`
   (Visa domestic), `5500 6700 0000 1002` (Mastercard), or UPI
   `success@razorpay`.
6. **Never write an inline fully-qualified enum literal in JPQL** — it
   compiles to a cast using the Java simple class name, not the Postgres
   enum type name (`ERROR: type "..." does not exist`). Always bind via
   `@Param`.
7. **`REFRESH MATERIALIZED VIEW CONCURRENTLY` cannot run inside an
   explicit `BEGIN...COMMIT` block** — drop `CONCURRENTLY` in any script
   that opens its own transaction.
8. Under `SessionCreationPolicy.STATELESS`, a custom
   `accessDeniedHandler`/`authenticationEntryPoint` calling
   `response.sendError(...)` forwards internally to `/error` — a second
   pass through the whole filter chain that lands anonymous unless
   `.requestMatchers("/error").permitAll()` is set (matches
   `spring-projects/spring-boot#31852`).
9. Browser `localStorage` is shared across tabs of the same origin —
   logging into a different account in a second tab will silently swap
   the JWT used by the first tab's outgoing requests. No fix built;
   workaround is closing other tabs during auth-sensitive testing.

## Immediate next action

Commit and push the pending deployment files (`Dockerfile`,
`.dockerignore`, `application-prod.yml`, `CLAUDE.md`,
`docs/deployment-guide.md`), then work through
`docs/deployment-guide.md` Step 1 onward: create the Neon project, deploy
the backend to Render, deploy the frontend to Vercel, close the CORS
loop, and verify end to end. Optionally close the long-open Razorpay
webhook gap (Phase 6) once Render gives a real public HTTPS URL. With the
roadmap itself complete, deployment and pulling the existing docs
together into the course's final deliverable (report/presentation) are
the only remaining work.
