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
Producer submits recycler + batch claim + evidence uploads. System runs 3 underlying checks, rolls them into a composite risk score, and produces a Low/Medium/High/Critical risk rating + downloadable PDF report.

1. **Plausibility check** (sync, rule-based) — recovery-rate sanity, capacity-ceiling vs recycler's self-reported annual capacity, absolute batch-size sanity. `PlausibilityCheckService`.
2. **Document forensics** — EXIF (GPS state match via Nominatim, timestamp vs processing date, device ID) for photos; PDFBox metadata (creation/mod dates, author) for PDFs; perceptual-hash duplicate detection (dHash, Hamming ≤10). `DocumentForensicsOrchestrator`.
3. **Regulatory history** (async) — Claude API checks CPCB/SPCB/NGT enforcement history for the recycler, returns findings + risk + rationale. `RegulatoryHistoryService`.

**Composite risk scoring** (`CompositeScoringService`, PRD §7.1a) — recomputed after check creation, after evidence upload, and after regulatory history completes (each of the three signals above lands at a different time; every recompute re-derives all five sub-scores from current data rather than patching one field, so out-of-order/repeat calls are safe). Five weighted sub-scores (0–100 risk, 0=clean/100=red flag; unrun signal defaults to neutral 50 rather than being excluded, so withholding evidence can't improve a score): registration/KYC (Module A0 credential-check history), capacity/plausibility, invoice forensics, document forensics, regulatory history. Weights differ by waste stream (`CompositeScoreWeights.BATTERY`/`.TYRE`) and are an explicitly uncalibrated draft — there's no labeled fraud/clean case set yet to tune them against. Composite score maps to a LOW/MEDIUM/HIGH/CRITICAL band with fixed, advisory (never "reject") report language. **Hard-disqualification** forces score=100/CRITICAL regardless of the weighted sum, but only 2 of the PRD's several disqualification rules are wired up because only these have real backing data today: batch-to-capacity ratio >3x, or an active NGT closure/show-cause finding. CPCB registration expiry, composition-range chemistry violations, and tyre geographic hotspots are **not** implemented — nothing in the codebase tracks those inputs yet.

Report generator (`ReportService`, PDFBox) aggregates plausibility, forensics, regulatory history, and the composite score breakdown into one PDF. Free-text fields (LLM regulatory summaries, user input) are sanitized to WinAnsi-safe ASCII before rendering — PDFBox's Standard14 fonts throw on curly quotes/em-dashes/non-Latin characters otherwise.

- Backend routes: `POST/GET /api/v1/checks`, `POST /api/v1/checks/{id}/evidence`, `GET /api/v1/checks/{id}/evidence` (read-later summary list, no per-sub-check breakdown — used when reopening a check outside the upload flow), `GET /api/v1/checks/{id}/plausibility`, `POST|GET /api/v1/checks/{id}/regulatory-history`, `GET /api/v1/checks/{id}/report`
- Frontend: `/verify` → `VerifyFlow` (step machine: `RecyclerDetailsForm` → `EvidenceUpload` → results). `/checks` → `ChecksListPage`, a table of every check the user has run (recycler, producer, waste stream, composite score, risk pill, status), linking into `/verify/{checkId}` → `CheckDetailPage`. Both the wizard's final step and the standalone detail page render the same shared `CheckResults` component (composite score card, plausibility, forensics/evidence summary, regulatory history, report download) so results look identical whether you just finished the wizard or came back later. `CompositeScoreCard` shows the score, band, hard-disqualification banner, and a per-layer sub-score breakdown bar; `RiskPill` (shared, also used on the dashboard) renders LOW/MEDIUM/HIGH/CRITICAL. `RecyclerDetailsForm` is role-gated: `PUBLISHER` (self-service producer) only sees Recycler + Batch fields, producer name auto-filled from their account; `CONSULTANT` and other roles still see the Producer fieldset since they check recyclers on behalf of multiple producer clients. Report download fetches the PDF from the backend origin with the JWT client-side (not a relative URL) so it works behind the Vercel/Railway split.

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
Same `/verify` flow as battery, with a waste-stream selector. Selecting Tyre swaps the "claimed recovery %" field for three tyre-specific fields (end-product type, quantity sold, claimed EPR certificate credit in kg) plus an "imported tyre" toggle, and runs a different plausibility sub-check: **EPR credit reconciliation**, `QEPR = QP × CF × WP` — read directly from CPCB's tyre EPR certificate guidance document (PRD §7.5). `QP` = declared end-product quantity; `CF`/`WP` (conversion factor / weightage) are fixed per end-product category (`TyreEndProduct` enum: crumb rubber, reclaimed rubber, CRMB, recovered carbon black, pyrolysis oil continuous, char batch); `WP` is forced to 1.0 if the underlying tyre was imported. The check compares the recycler's claimed credit against the formula-computed value: within 5% deviation → PASS, 5–20% → WARN, beyond 20% → FAIL; `UNVERIFIABLE` if any required field is missing. This **supersedes** the earlier 400–450 L/tonne TPO-yield-ratio benchmark (`TyreBenchmarks`, now deleted), which the PRD flagged as an unverified secondary-source estimate — the 5%/20% deviation tolerances themselves are still a provisional default, not CPCB-specified. Capacity-ceiling and absolute-batch-size checks are reused unchanged from battery (generic, not battery-specific). Forensics, regulatory-history, and PDF report are waste-stream-agnostic — zero changes needed.

- Backend: `TyrePlausibilityStrategy`, `TyreEndProduct`, `TyreEprReconciliation`, shared `GenericPlausibilityChecks.kt`
- Frontend: `RecyclerDetailsForm` waste-stream selector + conditional field swap (end-product dropdown, EPR credit input, imported-tyre checkbox)

### Module E — Used-Oil CA-1/CA-2 Registration Assistant (public, no login, standalone)
Guided CPCB registration helper for used-oil Collection Agents. Determines CA-1 (pickup/transport only) vs CA-2 (storage + fleet) tier fit, checks the CA-1 signed-agreement prerequisite gate, shows the CA-2 physical-readiness checklist, calculates registration fee + 25% annual processing charge off CPCB's quantity-tiered fee schedule, and builds a downloadable registration summary. A 4th step ("Application details") lets the user fill Authorized Person / Vehicle / Oil Collection (CA-1) or Storage/GST/Lab (CA-2) fields — all optional — and download a **prefilled application worksheet PDF** with those values laid out by CPCB form section; unfilled fields are marked `— fill in on CPCB portal —` rather than silently omitted. Explicitly informational only — E-PRid submits nothing to CPCB on the user's behalf; copy warns about false-info penalties (fee forfeiture, up to 5-yr revocation) and that portal payment isn't the final step (SPCB verification still required).

- Backend: `UsedOilAssistantService` (stateless, no repository), `UsedOilSummaryPdfService`, `UsedOilApplicationPdfService`, `UsedOilController` (`/api/v1/used-oil/*`, fully public) — zero shared entities/constants with battery or tyre
- Frontend: `/used-oil` → `UsedOilAssistantForm` (4-step: Tier → Prerequisites → Application details → Fee & summary) → `UsedOilSummaryResult` (two PDF downloads: plain summary, prefilled application)
- Open item: fee tiers and the 75km/150km CA-1 service-radius rule are grounded in CPCB's guidance document (read July 2026) — re-verify against future CPCB amendments

### Module F — CPCB Recycler Directory (auth required, standalone browse/search tool)
Separate from Module A: this is CPCB's own public battery-recycler registry (ingested from a CSV snapshot of the unauthenticated `eprbattery.cpcb.gov.in` DataTables endpoint), not a producer-submitted check. `CpcbRecycler` is a distinct entity from `Recycler` — no FK between them. Scored on an **Entity Risk Score** only (registration/authorization/geography) — deliberately has no certificate-volume, Form 4, or invoice data, so it cannot produce the fuller "Certificate Risk Score" Module A aims for; API docs and search descriptions say this explicitly so it isn't mistaken for a stronger signal than it is. Renamed from "Entity Health Score" (2026-07-08, `V16` migration): higher `composite_score` means riskier, and "health score" read as higher-is-better to anyone who hadn't read the scoring code — backwards. UI now shows an explicit "higher score = higher risk" note next to the score.

Ingestion (`POST /api/v1/cpcb-recyclers/ingest`, `ADMIN` only) upserts rows by CPCB's own row id via `CpcbRecyclerCsvParser` (Apache Commons CSV). Rows missing a source id are inserted fresh rather than rejected. A `data_quality_partial_capture` heuristic flags rows where ≥6 of 10 tracked optional fields are blank (GST, consents, HWMD, type, capacity, lat/long, staff/worker counts) — this marks the row as under-queried rather than treating those blanks as confirmed-absent, and downstream scoring treats a partial-capture row's blanks as "unassessed" instead of a flag. Ingestion re-scores every row it touches immediately. `CpcbRecycler` also carries a second batch of optional CPCB fields (web address, phone, install/operating dates, ISO 9001/14001 and APCM/WPCM upload flags, raw application/payment status codes, certificate no./date, MRAI membership, SOP/ESG policy flags) added once a fuller CSV pull was available — none of these are scored on except the ISO flags below.

`CpcbRecyclerScoring` (additive points, baseline 0, capped [0,100], mapped to the same LOW/MEDIUM/HIGH/CRITICAL bands as Module A) checks: expired Consent-to-Operate air/water (+40, spec-given), expired HWMD authorization (+40, spec-given), expired DIC validity (+10, assumed value), missing GST (+15, assumed), no chemistry/process authorization category on file (+10, assumed), declared capacity >10,000 with no recorded staff/workforce (+15, assumed, new heuristic not in the original methodology, and `recycling_capacity`'s unit is unconfirmed against CPCB documentation so the threshold is a raw-number not unit-aware comparison), plus a geographic-hotspot check (`cpcb_geo_risk_hotspots`, static point+radius reference data, not derived from the CPCB feed) that adds hotspot-specific points when the recycler's lat/long falls within a known risk radius. The ISO 9001/14001 bonus described in the source spec **is now wired up** (`-10` points when both `iso9001Upload` and `iso14001Upload` are true, assumed value) — but it's currently inert: all 553 rows in the 2026-07-08 pull have both flags blank, so no recycler gets the bonus yet; NULL is treated as false (no bonus), never guessed at true. Every point value except the two spec-given ones is an explicit placeholder pending real fraud/clean cases to calibrate against (same caveat as `CompositeScoreWeights` in Module A). Each scoring run is preserved as a new `CpcbRecyclerScore` row (history, not overwrite).

Search (`GET /api/v1/cpcb-recyclers/search`, any authenticated role, filters: name/gst/state) takes a **state name**, not the raw CPCB `state_id` — resolved via a new `cpcb_state_codes` table (`V17`) that maps `state_id` to a human state name; an unrecognized state name returns zero results rather than silently ignoring the filter. `GET /api/v1/cpcb-recyclers/states` lists the known state names for a frontend dropdown. The mapping is **inferred, not CPCB-published** — built by cross-referencing each `state_id` against address text in the same pull, covers only the 20 `state_id`s actually observed (one further `state_id` in the data, 25, was excluded as a CPCB test/placeholder row, not a real state). Search results also now include a resolved `stateName` alongside the raw `stateId`. Deliberately excludes `authorizedName`/`authorizedEmail`/`authorizedMobile` from the response — PII of an individual contact at the recycler, not the company, kept internal/ops-only.

- Backend: `CpcbRecyclerController`, `CpcbRecyclerIngestionService`, `CpcbRecyclerCsvParser`, `CpcbRecyclerScoring`/`CpcbRecyclerScoringService`/`CpcbRecyclerScoringBackfillRunner`, `CpcbRecyclerSearchService`, `CpcbStateCodeRepository`; migrations V12 (`cpcb_recyclers`/`cpcb_recycler_authorizations`/`cpcb_recycler_scores`/`cpcb_geo_risk_hotspots`) + V13 (seed data) + V14 (extra `CpcbRecycler` columns incl. ISO flags) + V15 (full 553-row directory seed, supersedes V13's sample) + V16 (`ENTITY_HEALTH`→`ENTITY_RISK` rename, data + column default) + V17 (`cpcb_state_codes`); routes `POST /api/v1/cpcb-recyclers/ingest`, `GET /api/v1/cpcb-recyclers/search`, `GET /api/v1/cpcb-recyclers/states`
- Frontend: `/cpcb-directory` (sign-in gate if logged out) → `CpcbRecyclerSearchForm` (name/GST text inputs, state as a `<select>` populated from `/states`) + `CpcbRecyclerResultCard` list, each card showing an explicit risk-level `RiskPill` next to the numeric score (previously score-only) plus the score-direction note. Linked from `Navbar` — hidden for `RECYCLER` role (not relevant to a recycler checking on other recyclers)

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
| `CpcbRecycler` | Module F — CPCB's own directory row, ingested from CSV, no FK to `Recycler` |
| `CpcbRecyclerAuthorization` | Module F — one row per chemistry/process category code (`R1`..`R4`, or `UNKNOWN`) on a `CpcbRecycler` |
| `CpcbRecyclerScore` | Module F — one row per scoring run (history preserved), composite score/band/flags/unassessed/layer breakdown as JSON |
| `CpcbGeoRiskHotspot` | Module F — static, hand-maintained geographic risk reference (name, risk level, points, point+radius) |
| `CpcbStateCode` | Module F — static, inferred mapping of CPCB's raw `state_id` codes to state names (not CPCB-published; covers 20 observed `state_id`s) |

`Producer`/`Recycler`/`VerificationCheck` all carry a `wasteStream` (`BATTERY`/`TYRE`/`USED_OIL`) column, default `BATTERY`. Module E (used-oil) has **no entities** — fully stateless, no persistence.

`VerificationCheck` tyre fields: nullable `claimedOutputQuantity` (QP, unit depends on end-product), nullable `tyreEndProduct` (enum), `tyreImported` (boolean, default false), nullable `claimedEprCreditKg`.

`VerificationCheck` composite-scoring fields (§7.1a, all nullable except `hardDisqualified`): `compositeScore`, `registrationSubScore`, `capacitySubScore`, `invoiceSubScore`, `forensicsSubScore`, `regulatorySubScore` (each 0–100), `hardDisqualified` (boolean, default false), `hardDisqualificationReason` (text). `RiskRating` enum gained a fourth value, `CRITICAL`, set automatically on hard-disqualification.

## 5. Auth

JWT (HMAC-SHA256), 24h expiry. Public: `/auth/**`, `/calculator/**`, `/used-oil/**`, swagger, health. Everything else Bearer-token gated. Frontend stores token in `localStorage` (`eprid_token`/`eprid_user`), `AuthContext` exposes `useAuth()`.

Self-registration (`AuthService.register`) is now allow-listed to `CONSULTANT`/`PRODUCER_STAFF`/`RECYCLER`/`PUBLISHER` — any other role (including `ADMIN`) throws and is rejected. Closes a prior hole where a caller could self-register with any role, `ADMIN` included. The allow-list is deliberate (not a deny-list) so a future new `UserRole` defaults to non-self-registerable until explicitly opted in.

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
- Module D: capacity-ceiling/batch-size checks reused from battery thresholds as an accepted approximation, not tyre-specific benchmarks. EPR credit reconciliation now uses CPCB's own QEPR=QP×CF×WP formula (no longer an unverified benchmark), but the 5%/20% PASS/WARN deviation tolerances are still a provisional default, not CPCB-specified
- Module E: fee schedule and CA-1 service-radius rule need re-verification against future CPCB amendments; no portal integration (informational only, by design)
- Composite risk scoring (§7.1a): sub-score weights and the LOW/MEDIUM/HIGH/CRITICAL score bands are an uncalibrated draft mechanism — no labeled fraud/clean case set exists yet to tune against
- Hard-disqualification only implements 2 of the PRD's rules (capacity ratio >3x, active NGT/suspension finding) — CPCB registration expiry, composition-range chemistry violations, and tyre geographic hotspots are not wired up because the codebase doesn't track those inputs
- Module F (CPCB directory): Entity Risk Score only — no certificate-volume/invoice data exists in this source, so it can't become a full Certificate Risk Score without a different data feed; all point values except expired-consent/expired-HWMD (+40 each, spec-given) are unverified placeholders; `recycling_capacity` units unconfirmed (likely MT/year); ISO 9001/14001 bonus is now wired up (-10 pts) but inert — 0 of 553 rows in the current pull have both flags populated; `cpcb_state_codes` state-name mapping is inferred from address text, not CPCB-published, and only covers the 20 `state_id`s observed in the 2026-07-08 pull; geographic hotspot list is static/hand-maintained, not sourced from CPCB

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
