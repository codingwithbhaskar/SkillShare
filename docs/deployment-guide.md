# Deployment guide — Neon + Render + Vercel (free tier, student demo)

Status: code-side prep done (2026-09-10) — `application-prod.yml`, `Dockerfile`,
`.dockerignore` delivered to `backend/skillshare-backend/`. Not yet committed
to git (commit them via IntelliJ's Commit panel — they'll show up as new
"Changes" now) or deployed. Everything below is the remaining, user-side
account/dashboard work.

Why this combo: PostGIS is the hard constraint (most free Postgres hosts
don't support extensions at all) — Neon does, and auto-wakes on connection
rather than needing manual un-pausing. Vercel can't run Spring Boot at all
(no JVM runtime, 10s function timeout on free tier, no long-running
processes) — confirmed via Vercel's own runtime docs — so the backend needs
a real container host; Render is the free one that doesn't need a credit
card. Frontend is a static React build, which Vercel is genuinely good for.

## Order matters

Render's CORS setting needs Vercel's URL, and Vercel's API URL setting needs
Render's URL — so deploy Render first, then Vercel, then loop back to Render
once to fill in the real CORS origin.

## Step 1 — Neon (database)

1. Sign up at neon.tech (free, no card).
2. Create a project. Note the region — pick one geographically close to
   wherever you'll pick for Render, to keep latency down.
3. From the project dashboard, open **Connection Details** and copy the
   connection string. It looks like:
   `postgresql://<user>:<password>@ep-xxxx-xxxx.<region>.aws.neon.tech/<dbname>?sslmode=require`
4. From that string, pull out three values for later:
   - `SKILLSHARE_DB_URL` = `jdbc:postgresql://ep-xxxx-xxxx.<region>.aws.neon.tech/<dbname>?sslmode=require`
     (same host/db/query string, just prefixed `jdbc:` and with the
     `postgresql://user:password@` part removed — those go in the next
     two variables instead)
   - `SKILLSHARE_DB_USERNAME` = the `<user>` part
   - `SKILLSHARE_DB_PASSWORD` = the `<password>` part
5. Nothing else to do here — Flyway runs `CREATE EXTENSION IF NOT EXISTS
   postgis;` and all 18 tables/triggers/views automatically on the
   backend's first boot against this database. No manual schema step.

## Step 2 — Render (backend)

1. Sign up at render.com with GitHub (free, no card).
2. **New +** → **Web Service** → pick the SkillShare GitHub repo.
3. Settings:
   - **Root Directory**: `backend/skillshare-backend`
   - **Runtime**: Render should auto-detect the `Dockerfile` and switch to
     Docker mode. If it doesn't, select Docker manually.
   - **Instance Type**: Free
4. **Environment** tab — add these variables:
   | Key | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `SKILLSHARE_DB_URL` | (from Step 1) |
   | `SKILLSHARE_DB_USERNAME` | (from Step 1) |
   | `SKILLSHARE_DB_PASSWORD` | (from Step 1) |
   | `JWT_SECRET` | a random string — generate one with `openssl rand -base64 48` (Git Bash has `openssl`), or any long random string |
   | `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` for now — **come back and update this in Step 4** |
   | `RAZORPAY_KEY_ID` | your existing test-mode key (optional — app boots fine without it, only payment calls would fail) |
   | `RAZORPAY_KEY_SECRET` | same |
   | `RAZORPAY_WEBHOOK_SECRET` | same |
5. **Create Web Service.** First build takes a few minutes (compiling +
   pulling the JDK image). Once live, copy its URL — something like
   `https://skillshare-backend-xxxx.onrender.com`.
6. Sanity check: visit `https://<that-url>/actuator/health` — should return
   `{"status":"UP"}`.

Free-tier behavior to expect: the service spins down after 15 minutes with
no traffic, and the next request wakes it (~30–60s). Fine for a demo — just
open the site a minute before you actually need it live.

## Step 3 — Vercel (frontend)

1. Sign up at vercel.com with GitHub (free, no card).
2. **Add New** → **Project** → pick the SkillShare repo.
3. Settings:
   - **Root Directory**: `frontend`
   - Framework preset: Vite (should auto-detect)
4. **Environment Variables** — add:
   | Key | Value |
   |---|---|
   | `VITE_API_BASE_URL` | `https://<your-render-url>/api` (from Step 2) |
5. **Deploy.** Copy the resulting URL, e.g. `https://skillshare-xxxx.vercel.app`.

## Step 4 — close the loop on CORS

Back on Render → your service → **Environment** → edit
`APP_CORS_ALLOWED_ORIGINS` → set it to your real Vercel URL from Step 3
(e.g. `https://skillshare-xxxx.vercel.app`). Render redeploys automatically
on save. If you also want Vercel's preview-deploy URLs to work (a different
URL per branch/PR), add them comma-separated.

## Step 5 — verify end to end

- Open the Vercel URL, register an account, log in, browse services.
- If you see CORS errors in the browser console: double-check Step 4 used
  the exact origin (scheme + host, no trailing slash) and that Render
  finished redeploying.
- If login/API calls fail outright: check Render's **Logs** tab for a
  Flyway or datasource error first — most first-deploy issues are a typo
  in the Neon connection details.

## Optional — Razorpay webhook (closes a known gap)

`phase-6-payment-gateway-code-complete.md` flagged the webhook path as
unit-tested only, since it needs a public HTTPS URL. Once Render is live,
you have one: in the Razorpay dashboard (test mode) → Webhooks → add
`https://<your-render-url>/api/payments/webhook`, select the payment events
you care about, and use the same secret as `RAZORPAY_WEBHOOK_SECRET`.

## Known free-tier limits (fine for a student demo, worth knowing)

- **Neon**: 0.5 GB storage, compute auto-suspends after 5 min idle
  (auto-wakes on the next query, no manual action needed).
- **Render**: 512 MB RAM, spins down after 15 min idle, ~750 free
  instance-hours/month.
- **Vercel**: generous free static hosting, no practical limit for a
  project this size.
