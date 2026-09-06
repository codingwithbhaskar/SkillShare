# frontend/

React + Bootstrap + Leaflet/React-Leaflet SPA — Phase 8 of the roadmap.
See `dev-status-and-next-steps.md` in the Claude project for full status.

## Setup

```
npm install
cp .env.example .env      # edit if your backend isn't on localhost:8080
npm run dev
```

Requires the Phase 1-7 Spring Boot backend running on `http://localhost:8080`
(`mvnw.cmd spring-boot:run` in `backend/skillshare-backend`).

## What's here so far (first installment)

- Vite + React 18, React Router v6, Axios, Bootstrap 5.
- `AuthContext` (`src/context/AuthContext.jsx`) — register/login/logout,
  JWT persisted to `localStorage`, attached to every API call via an Axios
  request interceptor (`src/api/client.js`). A response interceptor clears
  the stored session and bounces to `/login` on any 401.
- `ProtectedRoute` (`src/components/ProtectedRoute.jsx`) — route guard,
  optionally restricted to a list of roles (`allowedRoles`).
- Pages: Login, Register, Home (placeholder), Worker Dashboard (placeholder
  — blocked on the documented `GET /api/workers/me` gap from Phase 7).

## Not built yet

- Booking creation/list, allocation trigger, payment checkout UI.
- The actual worker dashboard (needs `/api/workers/me` first).
- The Leaflet map feature (`location-map-feature-design.md`) — pins, route
  line, OSRM/Nominatim calls.
- Admin views.
