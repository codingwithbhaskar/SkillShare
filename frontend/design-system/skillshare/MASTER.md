# Design System Master File

> **LOGIC:** When building a specific page, first check `design-system/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file.
> If not, strictly follow the rules below.

---

**Project:** SkillShare
**Generated:** 2026-08-30 (via nextlevelbuilder/ui-ux-pro-max-skill, run from the Claude session's cloud container against this project's real product description)
**Category:** Home Services (Plumber/Electrician)
**Design Dials:** Variance 4/10 (Balanced / Modern) | Motion 2/10 (Subtle) | Density 5/10 (Standard)

---

## Global Rules

### Color Palette

| Role | Hex | CSS Variable |
|------|-----|--------------|
| Primary | `#1E40AF` | `--color-primary` |
| On Primary | `#FFFFFF` | `--color-on-primary` |
| Secondary | `#3B82F6` | `--color-secondary` |
| On Secondary | `#000000` | `--color-on-secondary` |
| Accent/CTA | `#EA580C` | `--color-accent` |
| On Accent/CTA | `#000000` | `--color-on-accent` |
| Background | `#EFF6FF` | `--color-background` |
| Foreground | `#1E3A8A` | `--color-foreground` |
| Card | `#FFFFFF` | `--color-card` |
| Card Foreground | `#1E3A8A` | `--color-card-foreground` |
| Muted | `#E9EEF6` | `--color-muted` |
| Muted Foreground | `#475569` | `--color-muted-foreground` |
| Border | `#BFDBFE` | `--color-border` |
| Destructive | `#DC2626` | `--color-destructive` |
| On Destructive | `#FFFFFF` | `--color-on-destructive` |
| Ring | `#1E40AF` | `--color-ring` |

**Color Notes:** Professional blue + urgent orange [Accent adjusted from #F97316]

### Typography

- **Heading Font:** Poppins
- **Body Font:** Open Sans
- **Mood:** modern, professional, clean, corporate, friendly, approachable
- **Google Fonts:** [Poppins + Open Sans](https://fonts.googleapis.com/css2?family=Open+Sans:wght@300;400;500;600;700&family=Poppins:wght@400;500;600;700&display=swap)

**CSS Import:**
```css
@import url('https://fonts.googleapis.com/css2?family=Open+Sans:wght@300;400;500;600;700&family=Poppins:wght@400;500;600;700&display=swap');
```

### Spacing Variables

*Density: 5/10 — Standard*

| Token | Value | Usage |
|-------|-------|-------|
| `--space-xs` | `4px` / `0.25rem` | Tight gaps |
| `--space-sm` | `8px` / `0.5rem` | Icon gaps, inline spacing |
| `--space-md` | `16px` / `1rem` | Standard padding |
| `--space-lg` | `24px` / `1.5rem` | Section padding |
| `--space-xl` | `32px` / `2rem` | Large gaps |
| `--space-2xl` | `48px` / `3rem` | Section margins |
| `--space-3xl` | `64px` / `4rem` | Hero padding |

### Shadow Depths

| Level | Value | Usage |
|-------|-------|-------|
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)` | Subtle lift |
| `--shadow-md` | `0 4px 6px rgba(0,0,0,0.1)` | Cards, buttons |
| `--shadow-lg` | `0 10px 15px rgba(0,0,0,0.1)` | Modals, dropdowns |
| `--shadow-xl` | `0 20px 25px rgba(0,0,0,0.15)` | Hero images, featured cards |

---

## Style Guidelines

**Style:** Flat Design — 2D, minimalist, bold colors, no shadows, clean lines, simple shapes, typography-focused, modern, icon-heavy. Best for web apps, dashboards, SaaS, corporate — matches this project (a booking/dashboard app, not a marketing site).

**Key Effects:** No gradients/heavy shadows, simple hover (color/opacity shift), fast loading, clean transitions (150-200ms ease), minimal icons.

---

## Anti-Patterns (Do NOT Use)

- Hidden contact info, no certifications/trust signals (this is a trades-services marketplace — worker verification/trust badges matter)
- Emojis as icons — use SVG icons
- Missing `cursor: pointer` on clickable elements
- Layout-shifting hovers
- Low contrast text (4.5:1 minimum)
- Instant state changes without transitions
- Invisible focus states

## Pre-Delivery Checklist

- [ ] No emojis used as icons
- [ ] `cursor-pointer` on all clickable elements
- [ ] Hover states with smooth transitions (150-300ms)
- [ ] Light mode: text contrast 4.5:1 minimum
- [ ] Focus states visible for keyboard navigation
- [ ] Responsive: 375px, 768px, 1024px, 1440px
- [ ] No content hidden behind fixed navbars
- [ ] No horizontal scroll on mobile

---

## Applied in `src/theme.css` (2026-08-30)

This palette/typography/spacing was wired into the actual app as CSS custom
properties plus Bootstrap 5 variable overrides (`--bs-primary` etc.), rather
than left as a reference-only doc — see `src/theme.css` and its import in
`src/main.jsx`.

---

## v2 Amendment (2026-08-30) — Overrides the "Flat Design" section above

**Why:** Live user feedback on the first pass: *"i does not like designing and UI UX i want
interactive and attractive evrything not simple."* Re-running `search.py` with higher
variance/motion dials (variance 8 → "Brutalism"; variance 6 with "polished/delightful" framing →
still "Flat Design") showed the "Home Services" product-category match overrides dial-based style
switching for this category. Decision: keep the trust-blue/orange palette (not itself the
complaint) but hand-build materially more visual richness on top of it.

**What actually changed vs. the Flat Design spec above:**
- Gradients are used (hero panel, primary buttons, feature icons) — the "no gradients" rule above
  is superseded.
- Shadows are used deliberately, including colored "glow" shadows on the accent button and feature
  icons (`--shadow-glow-accent`, `--shadow-glow-primary`), not just the neutral `--shadow-*` scale.
- Cards lift and scale slightly on hover (`.card-interactive`) rather than the flat "opacity/color
  shift only" rule above.
- Load-in motion: `.fade-in-up` / `.stagger` keyframe animations on page sections and lists
  (respecting `prefers-reduced-motion`), plus a `badge-pulse` animation on `in_progress` status
  badges and a `shimmer` skeleton-loading style — this is beyond the "Subtle / 2/10" motion dial
  the tool generated.
- Icons: a full hand-rolled SVG set (`src/components/icons.jsx`, Lucide-style 24x24/2px-stroke) is
  now used throughout — nav, hero, feature cards, buttons, booking detail, dashboard stat cards,
  star ratings — not just "icon-heavy" as a loose keyword.
- New interactive elements added this pass: a clickable 5-star `StarPicker` on the review form
  (`BookingDetailPage.jsx`) replacing the plain `<select>`, and toggle-chip skill selection on the
  new-booking form (`NewBookingPage.jsx`) replacing plain checkboxes.
- `Spinner` / `SkeletonCard` components (`src/components/Spinner.jsx`) replace plain "Loading…"
  text across all data-fetching pages.

**Bug fix bundled into this pass (functional, not styling):** `leaflet/dist/leaflet.css` was never
imported anywhere in the app — the confirmed root cause of the reported "pinned location map does
not work" — fixed via `import 'leaflet/dist/leaflet.css'` in `src/main.jsx`.

**Pages fully updated in this pass:** Navbar, HomePage, LoginPage, RegisterPage,
BookingsListPage, WorkerDashboardPage, BookingDetailPage, NewBookingPage, AddressMapPicker,
RouteMap. **Not yet touched:** AdminPage (low-priority placeholder).
