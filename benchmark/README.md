# Query optimization benchmark harness

Roadmap item "Step 10" (see `research-topic-and-springboot-roadmap.md` §7 /
`dev-status-and-next-steps.md`): produce the "Normal vs Indexed" candidate-
search timing comparison across worker-count scale points, for hypothesis
H3 ("spatial + partial indexing reduces candidate-search time vs. full
scan").

Runs against a **separate** `skillshare_benchmark` database — `skillshare_dev`
(your real dev/demo data) is never touched.

## Why you run the SQL, not me

This session can read/write files in this folder and drive your browser,
but the sandboxed Linux VM it uses for shell commands cannot reach your
Windows Postgres instance over the network (confirmed: no `psql`, no route
to `localhost:5433`). So the SQL has to run on your machine, via `psql`. I
designed the harness so that's the *only* manual step — everything else
(schema stand-up, data generation at 5 scales, running the queries with and
without indexes, building the comparison table) is scripted.

## What's in here

```
benchmark/
  sql/
    00_reference_seed.sql            skills/services/allocation_criteria only
    01_generate_synthetic_data.sql   wipes + regenerates at -v scale=N
    02_query_candidate_search.sql    Query A (mirrors fn_find_candidates)
    03_query_nearby_workers.sql      Query B (mirrors the /spatial/nearby endpoint)
    04_drop_benchmark_indexes.sql    for the "without index" arm
    05_recreate_benchmark_indexes.sql
  run_benchmark.ps1                  orchestrates all of the above
  parse_results.py                   builds the comparison table afterward
  results/                           EXPLAIN ANALYZE JSON, one file per run
```

## How to run it

1. **Open PowerShell** and make sure `psql` is on PATH:
   ```powershell
   psql --version
   ```
   If that fails, add your Postgres `bin` folder to PATH (typically
   `C:\Program Files\PostgreSQL\16\bin`), or edit `$Psql` at the top of
   `run_benchmark.ps1`.

2. **Set the DB password** the same way you do for the backend:
   ```powershell
   $env:SKILLSHARE_DB_PASSWORD = "<your postgres password>"
   ```

3. **Smoke-test with one small scale first** (finishes in seconds, catches
   any setup problem before committing to the full run):
   ```powershell
   cd D:\Dev\SkillShare\benchmark
   .\run_benchmark.ps1 -Scales 1000 -Fresh
   ```
   `-Fresh` drops and recreates `skillshare_benchmark` from scratch — only
   needed the first time, or if you want a clean slate.

4. **If that succeeds, run the full sweep** (1k / 5k / 10k / 50k / 100k —
   the 50k/100k passes are the slow part, expect several minutes total):
   ```powershell
   .\run_benchmark.ps1
   ```
   Progress is printed live and also logged to `run_log_<timestamp>.txt` in
   this folder. If any step fails, the script stops immediately and the log
   says which `.sql` file and which scale/mode it failed on — send me that
   file (or just tell me it failed and at what step) and I'll fix the
   script.

5. **Tell me it's done.** The results land in `results/*.json` in this same
   mounted folder, so I can read them directly and run `parse_results.py`
   myself (it's plain-stdlib Python, no DB connection needed) to produce
   `results/comparison_table.md` and `.csv`, then write up the H3 findings.
   You're welcome to run `python parse_results.py` yourself first if you'd
   rather see it immediately.

## What each scale point actually generates

For `-v scale=N`: N customers + N workers (each with their own location,
scattered in a ~50km box around Nashik so some fall outside the 30km hard
filter — that's deliberate), 7×N availability rows, 2×N worker_skills, N
worker_services, and exactly 2×N historical bookings: a past/completed
booking for every worker, plus exactly one of a far-future/pending booking
or a booking in the exact window the benchmark queries test against
(mutually exclusive, chosen so 10% of workers are deliberately busy right
then — the "no overlapping booking" filter needs real work to do). Full
design rationale is commented at the top of `01_generate_synthetic_data.sql`.

## What gets compared

Two queries, each run at all 5 scales, with the relevant indexes present
and then dropped (`idx_locations_geom`, `idx_workers_status`,
`idx_workers_location_id`, `idx_worker_availability_worker_id`,
`idx_bookings_worker_id`):

- **candidate_search** — the exact hard-filter logic `fn_find_candidates`
  uses (status + service + 30km spatial + availability + no overlapping
  booking), fixed at service_id=1, Nashik center, 2026-10-05 10:00-12:00 IST.
- **nearby_workers** — the `/api/spatial/workers/nearby` radius search,
  15km around the same center.

`parse_results.py` reads every plan and prints a speedup ratio
(noindex-time / indexed-time) per query per scale — that ratio, growing
with N, is the H3 evidence.
