# SkillShare — Intelligent Worker Marketplace

An on-demand home-services marketplace (electricians, plumbers, cleaners,
and more) where **worker allocation is computed inside the database**,
not the application layer — a Spring Boot + PostGIS project built to
demonstrate advanced DBMS concepts through a real, working product rather
than isolated SQL exercises.

**Live demo:** https://skill-share-lime.vercel.app
**API health:** https://skillshare-backend-wdhv.onrender.com/actuator/health

> Free-tier hosting: the backend may take a few seconds to respond on the
> very first request if it's been idle.

---

## What makes this a DBMS project, not just a CRUD app

| Concept | Where it lives |
|---|---|
| **Intelligent allocation** — candidates ranked on distance, rating, skill fit, price, experience and current workload | PL/pgSQL functions (`fn_find_candidates`, `fn_score_candidate`) and a procedure (`sp_allocate_worker`), not Java |
| **Spatial search** | PostGIS `geography(Point, 4326)` columns, radius queries via `ST_DWithin`/`ST_Distance` |
| **Concurrency-safe booking** | An `EXCLUDE` constraint (`btree_gist`) makes double-booking a worker for overlapping slots impossible at the database level, not just checked in application code |
| **Notification outbox pattern** | Triggers write to an outbox table on every status change; a scheduled job polls and dispatches — notifications can never be silently forgotten by a new code path |
| **Materialized view** | `mv_worker_stats` (ratings/booking counts), refreshed concurrently, backing the worker dashboard and booking cards |
| **Generic audit trail** | A single trigger function logs INSERT/UPDATE/DELETE across tracked tables to JSONB before/after snapshots |
| **Query optimization** | A benchmark harness swept 1k–100k synthetic workers with/without indexes, comparing `EXPLAIN ANALYZE` plans |
| **Evaluation methodology** | 6 allocation strategies (random, nearest-worker, rating-only, simple-weighted, traditional, intelligent) compared head-to-head on real metrics — see `docs/dev-status-and-next-steps.md` |

Full schema/design rationale, ER diagram, and phase-by-phase build history
are in `docs/dev-status-and-next-steps.md`.

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4.1.1 (JDK 21) — Web MVC, Data JPA, Security, Flyway, Validation, Actuator |
| Database | PostgreSQL 16/18 + PostGIS — hosted on [Neon](https://neon.tech) |
| Frontend | React 18 + Vite + Bootstrap 5 |
| Maps | Leaflet + OpenStreetMap tiles, OpenRouteService (routing), LocationIQ (geocoding) |
| Payments | Razorpay (Checkout.js + server-side order/verify/webhook) |
| Email | Brevo Transactional Email API (password reset) |
| Auth | Stateless JWT (HS512), BCrypt password hashing |
| Hosting | [Render](https://render.com) (backend, Docker) + [Vercel](https://vercel.com) (frontend, static) + Neon (database) |

---

## Features

**Customers** — browse services, book a worker, get matched automatically
by the allocation engine, track the job on a live map, pay via Razorpay,
leave a rating and review.

**Workers** — self-service profile (bio, rate, location, skills,
availability, services offered), a dashboard of assigned jobs, stats and
reviews.

**Admin** — user management (suspend/reactivate), full catalog CRUD
(services/skills with category tagging), revenue/booking reports, and a
research-only endpoint that reruns the 6-strategy allocation comparison
on demand.

**Everyone** — self-service profile editing, password change, and a
"forgot password" flow with real email delivery.

---

## Project structure

```
SkillShare/
├── backend/skillshare-backend/   Spring Boot API
│   └── src/main/resources/db/migration/   Flyway migrations (V1–V8)
├── frontend/                     React + Vite SPA
├── db/                           Canonical SQL — schema, triggers, views,
│                                  functions/procedures, seed data
│                                  (Flyway migrations are byte-identical
│                                  copies, just reordered/renamed)
├── docs/                         Deployment guide, full dev history
└── benchmark/                    Query-optimization benchmark harness
                                   and results (Python + raw SQL)
```

---

## Running it locally

### Prerequisites
JDK 21, Maven, Node.js, PostgreSQL 16+ with the PostGIS extension
available, Docker (optional, for the Testcontainers-backed test profile).

### Database
```bash
createdb skillshare_dev
# Flyway applies db/01_schema_v3.sql … 06_seed_data_v3.sql (as
# V1__schema.sql … V5__seed.sql) automatically on first backend boot —
# no manual schema step needed.
```

### Backend
```bash
cd backend/skillshare-backend
# Set SKILLSHARE_DB_PASSWORD as an env var first (see application-dev.yml)
./mvnw spring-boot:run
```
Runs on `http://localhost:8080`, profile `dev` by default.

### Frontend
```bash
cd frontend
npm install
cp .env.example .env   # defaults to http://localhost:8080/api
npm run dev
```
Runs on `http://localhost:5173`.

### Tests
```bash
cd backend/skillshare-backend
./mvnw test   # 149 tests — unit tests + a Testcontainers context-load test
```

---

## Deployment

The live demo runs on entirely free tiers: **Neon** (Postgres/PostGIS),
**Render** (backend, Docker), **Vercel** (frontend). Full step-by-step
instructions, environment variable reference, and known free-tier
limitations are in [`docs/deployment-guide.md`](docs/deployment-guide.md).

---

## Documentation

- [`docs/dev-status-and-next-steps.md`](docs/dev-status-and-next-steps.md) — complete phase-by-phase build history, design decisions, bugs found and fixed, and the baseline-comparison evaluation results
- [`docs/deployment-guide.md`](docs/deployment-guide.md) — deploying your own copy for free
- [`db/`](db) — canonical SQL, readable independently of the Java code

---

## Conventions

- Real secrets (DB password, JWT secret, Razorpay/Brevo/routing keys) are
  never hardcoded — always `${ENV_VAR}` placeholders.
- Canonical SQL lives in `db/*.sql`; Flyway migrations mirror it
  byte-for-byte, just reordered into dependency order.
- All scoring/allocation logic lives in the database (PL/pgSQL) — the
  Spring Boot layer calls into it, it doesn't reimplement it.

---

*An independent MSc-level database systems project.*
