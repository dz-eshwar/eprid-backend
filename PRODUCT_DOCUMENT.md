# E-PRid — Product Document

## 1. What it is

E-PRid = compliance SaaS for India's Battery Waste Management Rules (BWMR) 2022 Extended Producer Responsibility (EPR) regime.

**Problem**: BWMR 2022 certificate market has no verification tooling. Analogous plastic-EPR system documented ~700k fraudulent certs. Producers pay recyclers for EPR certificates with no way to check if claims (batch weight, recovery %, recycler legitimacy) are real.

**Who for**:
- Producers/Producer staff — buy EPR certs, need risk-check before trusting a recycler
- Consultants — run checks on behalf of producer clients
- Recyclers — issue certs, want document vault + (future) faster EPR paperwork

## 2. Architecture

```
eprid-frontend (Next.js 16, React 19, TS)  ──HTTP/JWT──▶  EPRid backend (Kotlin/Spring Boot 3.3.6)
                                                                │
                                                                ├─ PostgreSQL (Flyway, schema `eprid`)
                                                                ├─ Anthropic Claude API (regulatory-history analysis)
                                                                └─ Nominatim OSM (reverse geocode, EXIF GPS match)
```

Backend package `com.rorapps.eprid`, port 8080. Frontend calls backend via `NEXT_PUBLIC_API_URL` (defaults `localhost:8080`).

**Frontend is locale-prefixed.** All app routes live under `app/[locale]/...` (next-intl), routed by middleware in `proxy.ts` against `i18n/routing.ts` (`locales: en/hi/te`, default `en`). Frontend paths cited below (`/verify`, `/calculator`, etc.) omit the locale prefix for brevity — actual paths are `/en/verify`, `/hi/verify`, etc. Translated strings live in `messages/{en,hi,te}.json`.

**Waste streams are loosely coupled.** Battery (Module A/B/C1), Tyre/TPO (Module D), and Used-Oil (Module E) can each be deplugged without touching the other two:
- Plausibility checks dispatch via a `PlausibilityStrategy` interface (Spring `List<PlausibilityStrategy>` injection, `supports(wasteStream)`) — battery logic untouched, tyre plugs in as a second strategy.
- A `waste_stream` discriminator column (`BATTERY`/`TYRE`/`USED_OIL`) was added to `producers`/`recyclers`/`verification_checks` rather than new entity hierarchies.
- Module E (used-oil) has zero shared entities/repos/constants with battery or tyre — fully independent package tree. Deplugging it means deleting one package + one `SecurityConfig` line.

## 3. Modules

### Landing page (public marketing site)
Marketing homepage at `/` (hero, problem bar, waste-streams section, how-it-works, who-it's-for, footer). CTA links straight to `/register` — no waitlist gate (an earlier Resend-backed waitlist form was replaced with direct signup once the app went live; the `/api/waitlist` route handler is still present but no longer called from the page). Waste-streams section is a 3-card promo (Battery/Tyre → `/verify`, Used-Oil → `/used-oil`) added when Module E shipped. Landing nav and the authenticated app `Navbar` both got a direct `/used-oil` link at the same time.

- Frontend: `components/landing/LandingPage.tsx` (`StreamsSection`), `components/layout/Navbar.tsx`, locale-aware nav with language switcher

### Module B — EPR Compliance Calculator (public, no login, ships first)
Producer enters battery category (Portable/Automotive/Industrial/EV), FY, quantity placed in market, optional quantity already fulfilled.
Returns: target tonnes (Schedule II recovery % × quantity), shortfall, EC (Environmental Compensation) exposure with 3-yr carry-forward/refund schedule (75%/60%/40%, forfeit after yr3) and late-interest tiers. CTA into Module A.

- Backend: `ComplianceCalculatorService`, `POST /api/v1/calculator/estimate`
- Frontend: `/calculator` → `CalculatorForm` → `CalculatorResult`

### Module A — Recycler/Certificate Risk Check (core MVP, auth required)
Producer submits recycler + batch claim + evidence uploads. System runs 3 checks, produces Low/Medium/High risk rating + downloadable PDF report.

1. **Plausibility check** (sync, rule-based) — recovery-rate sanity, capacity-ceiling vs recycler's self-reported annual capacity, absolute batch-size sanity. `PlausibilityCheckService`.
2. **Document forensics** — EXIF (GPS state match via Nominatim, timestamp vs processing date, device ID) for photos; PDFBox metadata (creation/mod dates, author) for PDFs; perceptual-hash duplicate detection (dHash, Hamming ≤10). `DocumentForensicsOrchestrator`.
3. **Regulatory history** (async) — Claude API checks CPCB/SPCB/NGT enforcement history for the recycler, returns findings + risk + rationale. `RegulatoryHistoryService`.

Report generator (`ReportService`, PDFBox) aggregates all three into one PDF.

- Backend routes: `POST/GET /api/v1/checks`, `POST /api/v1/checks/{id}/evidence`, `GET /api/v1/checks/{id}/plausibility`, `POST|GET /api/v1/checks/{id}/regulatory-history`, `GET /api/v1/checks/{id}/report`
- Frontend: `/verify` → `VerifyFlow` (step machine: `RecyclerDetailsForm` → `EvidenceUpload` → `ForensicsResults` → `PlausibilityResults` → `RegulatoryHistory`). `RecyclerDetailsForm` is role-gated: `PUBLISHER` (self-service producer) only sees Recycler + Batch fields, producer name auto-filled from their account; `CONSULTANT` and other roles still see the Producer fieldset since they check recyclers on behalf of multiple producer clients. Report download fetches the PDF from the backend origin with the JWT client-side (not a relative URL) so it works behind the Vercel/Railway split.

### Module C1 — Recycler Document Vault (MVP, standalone)
Recycler-side document storage (registration cert, GST cert, processing receipts) for the recycler's own records. Mandatory consent screen before first upload (documents may become verification evidence in future update — DPDP Act consent copy still pending, blocks full launch). **Not linked to Module A at MVP** — vault and risk-check are separate.

- Backend: `VaultController` (`/api/v1/vault/*`), soft-delete, ownership-checked download
- Backend: `RecyclerController` — `GET /api/v1/recyclers/me` (recycler self-profile, RECYCLER role only)
- Frontend: `/vault` — state machine: sign-in gate → recycler-ID pick (non-RECYCLER roles) → consent dialog → upload form + doc list. RECYCLER role auto-resolves own recycler ID.

### Module C2 — Evidence-linkage + EPR Form Autofill (post-MVP, concept only)
Not built. Vault docs become usable as Module A evidence; autofill generates a downloadable EPR form draft (no portal integration). Needs a compliance-consultant reviewer (candidate not confirmed) and live recycler adoption of C1 first. Risk: only "clean" recyclers may opt in (selection bias); chicken-and-egg on producer-mandated use.

### Module A0 — Recycler KYC/Credential Gate (auth required, registration-time, non-blocking)
At RECYCLER registration, user optionally supplies GSTIN, legal business name, Udyam number, CIN/DIN. Backend runs credential checks (GST/Udyam/MCA) through a `KycProvider` interface — a real vendor can drop in later behind the same interface with zero call-site changes. **Never blocks registration**: the check call is wrapped in try/catch and always swallowed on failure. No vendor is wired up yet — the stub provider (`app.kyc.provider=stub`, default) always returns `COULD_NOT_VERIFY`. GST OTP verification exists on the interface but isn't called from `register()` (needs a two-step synchronous flow, PRD open item).

- Backend: `KycProvider`/`StubKycProvider`, `RecyclerCredentialCheck` entity (history, one row per attempt), `GET /api/v1/recyclers/me/credential-checks`
- Frontend: `RegisterForm` reveals the 4 optional fields for RECYCLER role; `dashboard` → `CredentialChecksCard` shows PASS/FAIL/COULD_NOT_VERIFY pills with framing copy ("step one of an ongoing verification relationship — not full vetting")

### Module D — Tyre/TPO Recycler Risk Check (auth required, plugs into Module A)
Same `/verify` flow as battery, with a waste-stream selector. Selecting Tyre swaps the "claimed recovery %" field for "claimed TPO output (litres)" and runs a different plausibility sub-check: yield (litres ÷ tonnes) plausibility against a 400–450 L/tonne benchmark range (`TyreBenchmarks`) — `UNVERIFIABLE` if output wasn't given, `WARN` under-yield, `FAIL` beyond 10% over ceiling. Capacity-ceiling and absolute-batch-size checks are reused unchanged from battery (generic, not battery-specific). Forensics, regulatory-history, and PDF report are waste-stream-agnostic — zero changes needed.

- Backend: `TyrePlausibilityStrategy`, `TyreBenchmarks`, shared `GenericPlausibilityChecks.kt`
- Frontend: `RecyclerDetailsForm` waste-stream selector + conditional field swap

### Module E — Used-Oil CA-1/CA-2 Registration Assistant (public, no login, standalone)
Guided CPCB registration helper for used-oil Collection Agents. Determines CA-1 (pickup/transport only) vs CA-2 (storage + fleet) tier fit, checks the CA-1 signed-agreement prerequisite gate, shows the CA-2 physical-readiness checklist, calculates registration fee + 25% annual processing charge off CPCB's quantity-tiered fee schedule, and builds a downloadable registration summary. A 4th step ("Application details") lets the user fill Authorized Person / Vehicle / Oil Collection (CA-1) or Storage/GST/Lab (CA-2) fields — all optional — and download a **prefilled application worksheet PDF** with those values laid out by CPCB form section; unfilled fields are marked `— fill in on CPCB portal —` rather than silently omitted. Explicitly informational only — E-PRid submits nothing to CPCB on the user's behalf; copy warns about false-info penalties (fee forfeiture, up to 5-yr revocation) and that portal payment isn't the final step (SPCB verification still required).

- Backend: `UsedOilAssistantService` (stateless, no repository), `UsedOilSummaryPdfService`, `UsedOilApplicationPdfService`, `UsedOilController` (`/api/v1/used-oil/*`, fully public) — zero shared entities/constants with battery or tyre
- Frontend: `/used-oil` → `UsedOilAssistantForm` (4-step: Tier → Prerequisites → Application details → Fee & summary) → `UsedOilSummaryResult` (two PDF downloads: plain summary, prefilled application)
- Open item: fee tiers and the 75km/150km CA-1 service-radius rule are grounded in CPCB's guidance document (read July 2026) — re-verify against future CPCB amendments

## 4. Data model (backend entities)

| Entity | Purpose |
|---|---|
| `User` | login, role: PRODUCER_STAFF / CONSULTANT / ADMIN / RECYCLER / PUBLISHER (self-service producer, no consultant intermediary) |
| `Producer` | name, CPCB reg number, battery category mix |
| `Recycler` | name, BWMR reg number, self-reported capacity, state |
| `VerificationCheck` | one Module-A run; batch claim + status + risk rating |
| `Evidence` | uploaded file + extracted forensics metadata, linked to a Check |
| `PlausibilityCheck` | 1:1 with Check, 3 sub-check results |
| `RegulatoryFinding` | per-finding CPCB/SPCB/NGT/news record, linked to Recycler (+ optional Check) |
| `ComplianceEstimate` | one Module-B calculator session; optional CTA link to a Check |
| `VaultDocument` | recycler's own stored doc, consent timestamp, soft-delete |
| `RecyclerCredentialCheck` | Module A0 — one row per KYC check attempt (GST/Udyam/MCA), result history preserved |

`Producer`/`Recycler`/`VerificationCheck` all carry a `wasteStream` (`BATTERY`/`TYRE`/`USED_OIL`) column, default `BATTERY`. `VerificationCheck` additionally has a nullable `claimedOutputQuantity` (tyre TPO litres). Module E (used-oil) has **no entities** — fully stateless, no persistence.

## 5. Auth

JWT (HMAC-SHA256), 24h expiry. Public: `/auth/**`, `/calculator/**`, `/used-oil/**`, swagger, health. Everything else Bearer-token gated. Frontend stores token in `localStorage` (`eprid_token`/`eprid_user`), `AuthContext` exposes `useAuth()`.

## 6. Regulatory content (must stay current)

**Schedule II recovery targets** (fraction of quantity placed in market):
| Category | FY24-25 | FY25-26 | FY26-27+ |
|---|---|---|---|
| Portable | 70% | 80% | 90% |
| Automotive | 55% | 60% | 60% |
| Industrial | 55% | 60% | 60% |
| EV | 70% | 80% | 90% |

**EC (Environmental Compensation) rate** — confirmed from CPCB "Notice_EC Guidelines" (Aug 2024): per-tonne deposit varies by category; 3-yr refund taper (75/60/40%, forfeit after); late-payment interest 12% p.a. (≤1mo) / 24% p.a. (1-3mo) / unit closure + EPA §15(1) (>3mo).

⚠️ Open risk: target %s need reverification against the 2025 gazette amendment before each FY rollover; `RecoveryTargets` object only covers FY24-25→FY26-27, throws on unknown FY.

## 7. Brand

Primary teal `#0F6E56` · Graphite `#444441` · Page bg `#F1EFE8` · Coral CTA `#D85A30` · Success `#3B6D11` · Warning `#854F0B` · Danger `#A32D2D` · Verified badge `#534AB7`

## 8. Known gaps / launch blockers

- DPDP Act-compliant consent copy for vault not drafted — blocks Module C1 full launch
- Module C1 untested with a real recycler — unproven whether recyclers use a vault with no verification payoff yet
- Nominatim reverse-geocode has no rate-limit handling (1 req/sec external limit) — risk at scale
- Duplicate-image check does a full-table scan (`findAllByImagePhashNotNull`) — needs index/bloom filter before volume grows
- Redis in stack list but unused; coroutines dependency present but unused in service layer
- Module C2 has no entity/migration yet — concept stage only
- Consultant reviewer for C2 (Ruchi Shrivastava candidate) not confirmed, comp terms undecided
- Module A0: no real KYC vendor wired up — stub always returns `COULD_NOT_VERIFY`; GST OTP verification interface exists but unused (needs a two-step sync flow)
- Module D: capacity-ceiling/batch-size checks reused from battery thresholds as an accepted approximation, not tyre-specific benchmarks
- Module E: fee schedule and CA-1 service-radius rule need re-verification against future CPCB amendments; no portal integration (informational only, by design)

## 9. Build sequence (per PRD)

1. Module B calculator (rule-based, fastest demo)
2. Document forensics (Module A)
3. Plausibility check (Module A)
4. Regulatory history monitoring (Module A)
5. PDF report generator
6. Orchestration + risk-score weighting
7. Module C1 (vault + consent), in parallel with A/B
8. Module C2 — post-MVP
9. Module A0 (KYC gate), Module D (tyre/TPO), Module E (used-oil assistant) — added as loosely-coupled extensions, each deplug-able independently
