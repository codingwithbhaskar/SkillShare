# db/

Canonical, hand-authored SQL for SkillShare's PostgreSQL schema (v3),
kept here as the human-readable source of truth. The Spring Boot backend
consumes byte-identical copies of these same 5 files as Flyway migrations
under `backend/skillshare-backend/src/main/resources/db/migration/`,
renamed to Flyway's required `V<n>__description.sql` convention:

| This file                       | Flyway migration                  |
|----------------------------------|------------------------------------|
| `01_schema_v3.sql`               | `V1__schema.sql`                   |
| `04_triggers_v3.sql`             | `V2__triggers.sql`                 |
| `03_views_v3.sql`                | `V3__views.sql`                    |
| `05_functions_procedures_v3.sql` | `V4__functions_procedures.sql`     |
| `06_seed_data_v3.sql`            | `V5__seed.sql`                     |

Run order matters and differs from the numeric filename prefixes above —
it's schema -> triggers -> views -> functions/procedures -> seed, because
the views don't depend on triggers/functions but the seed script's
`CALL sp_allocate_worker(...)` does. That's exactly the order the Flyway
`V1`..`V5` numbering encodes.

If you ever need to modify the schema, edit the file here first, then copy
it into the Flyway `db/migration` folder under a **new** `V<n+1>__...sql`
file (never edit an already-applied `V<n>` file in place — that's what
breaks Flyway's checksum validation on teammates' machines).

Verified: all 5 files were run end-to-end against a live PostgreSQL 16
instance (schema -> triggers -> views -> functions -> seed, zero errors),
including the two real `CALL sp_allocate_worker(...)` allocation runs in
the seed data, which reproduced the expected qualitative behavior — the
higher-skill-match candidate winning despite being pricier on an urgent
booking, and the closer of two equally-skilled candidates winning on a
normal booking. PostGIS-specific calls (`ST_DWithin`, `ST_Distance`,
`geography(Point,4326)`) were swapped for the `earthdistance`/`cube`
extensions for that test run only, since this sandbox cannot install
PostGIS (network egress blocked) — same workaround already used and
documented elsewhere in this project. The files as shipped here use real
PostGIS syntax throughout; run once against the actual PostGIS-enabled
`skillshare_dev` database (already confirmed working on this machine, per
`dev-status-and-next-steps.md`) to fully confirm the geodetic distance math
before trusting it in production.
