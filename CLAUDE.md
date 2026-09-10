# SkillShare — Intelligent Worker Marketplace (DBMS project)

Spring Boot 4.1.1 (JDK 21) + PostgreSQL 16/PostGIS backend at
`backend/skillshare-backend`; React + Vite frontend at `frontend`; canonical
SQL (schema/triggers/views/functions/seed) at `db/`, mirrored into Flyway
migrations at `backend/skillshare-backend/src/main/resources/db/migration`.

MSc-level DBMS research project — the app itself is a means to demonstrate
advanced DBMS concepts (intelligent worker allocation via PL/pgSQL,
spatial queries, triggers, materialized views, query optimization
benchmarking), not just a CRUD app. See `docs/dev-status-and-next-steps.md`
for the full phase-by-phase history, known gotchas, and current status;
`docs/deployment-guide.md` for the step-by-step deployment plan; and
`Claude outputs/` for the review report.

## Status as of 2026-09-10

All 11 development phases are done and live-verified (backend, frontend,
admin panel, benchmark harness, baseline comparison). The project was just
pushed to GitHub via IntelliJ's "Share Project on GitHub" (public repo).

**In progress: deploying for free (student/demo purposes) on Neon (DB) +
Render (backend) + Vercel (frontend).** Full step-by-step is in
`docs/deployment-guide.md`. Code-side prep already done:

- `backend/skillshare-backend/Dockerfile` — multi-stage build (Render has
  no native Java runtime, only Docker, for JVM apps)
- `backend/skillshare-backend/.dockerignore`
- `backend/skillshare-backend/src/main/resources/application-prod.yml` —
  new `prod` Spring profile, reads DB connection + JWT secret + CORS
  allowed-origins + Render's `$PORT` all from environment variables (same
  convention as the existing `dev` profile's `SKILLSHARE_DB_PASSWORD`)

**Immediate next action**: these three files are new/uncommitted — commit
and push them, then continue with `docs/deployment-guide.md` Step 1
(create the Neon project) onward. Nothing else on the roadmap is
outstanding.

## Conventions

- Real secrets (DB password, JWT secret, Razorpay keys) are NEVER
  hardcoded into `application-*.yml` — always `${ENV_VAR}` placeholders,
  set via IntelliJ run-config env vars locally or the hosting platform's
  dashboard in production.
- Canonical SQL lives in `db/*.sql`; Flyway migrations under
  `backend/skillshare-backend/src/main/resources/db/migration/` must stay
  byte-identical to those (just renamed to `V1__schema.sql` etc., in
  dependency order: schema → triggers → views → functions/procedures →
  seed — not the numeric filename order).
- Do NOT reference or compare against an old "SkillShare v1" PHP/MySQL
  project in any report/deliverable — this is an independent project.
