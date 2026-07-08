# PRODUCT_DOCUMENT.md sync log

Append-only. Each entry records the HEAD commit synced to in each repo, what changed in PRODUCT_DOCUMENT.md, and review status.

## 2026-07-06 (first run)

- No prior log existed — used `git log --since="30 days ago" --stat` in both repos as the starting window (both repos' entire history falls inside 30 days: backend created 2026-06-30, frontend created 2026-06-26).
- Synced backend HEAD: `7a95b2e235140200e9849065eb232fd69178dfc8`
- Synced frontend HEAD: `96544685fcf156ac358b9d8d35921154574e261c`
- Commits reviewed:
  - Backend: `d0b3828` (initial setup, 90 files — already reflected in existing doc content), `44c7fd4`/`c0590f3`/`f72206c`/`1f279ef`/`129d0bd` (CORS allowed-origins bug fixes for production, deployment-only, no doc-visible behavior change), `7a95b2e` (fixed NPE / null-field bug on GET /checks, regulatory-history, report, evidence via lazy-loading JOIN FETCH — internal correctness fix, doesn't change any documented route or behavior, no doc change made).
  - Frontend: `1590d7d` (create-next-app init), `a612505` (regulatory history component, verify flow, dashboard — already reflected), `d752f76` (landing page + Resend waitlist), `9da627c` (JSX fix), `7e16798`/`b0fb03d` (brand mark + navbar contrast fix), `5d214b0` (next-intl i18n added, routes moved under `app/[locale]/`), `3a42748` (locale switcher fix), `b7b025f` (waitlist replaced with direct `/register` CTA), `9654468` (PUBLISHER role-gating on verify form, report download fix).
- Changes made to PRODUCT_DOCUMENT.md:
  - §2 Architecture: added note that frontend routes are locale-prefixed (`/[locale]/...`, en/hi/te via next-intl, `proxy.ts` middleware) and that cited paths omit the prefix.
  - §3: added new "Landing page" entry (public marketing site, direct-signup CTA, dead `/api/waitlist` route left over from removed waitlist flow).
  - §3 Module A: noted `RecyclerDetailsForm` role-gating (PUBLISHER vs CONSULTANT/others) and the report-download JWT-fetch fix.
  - §4 Data model: added `PUBLISHER` to `User` role list (was already in code since the initial commit, just missing from the doc).
- Edit is uncommitted in the backend repo working tree, waiting for Eshwar's review.

## 2026-07-06 (second run)

- No changes since last sync — both repos' HEAD are identical to the last-synced hashes.
- Backend HEAD unchanged: `7a95b2e235140200e9849065eb232fd69178dfc8`
- Frontend HEAD unchanged: `96544685fcf156ac358b9d8d35921154574e261c`
- No commits to review; PRODUCT_DOCUMENT.md not touched.
- Note (observation only, not a doc change): both repos have substantial uncommitted working-tree changes not yet reflected in any commit — backend touches SecurityConfig, EvidenceController, RecyclerController, RegulatoryHistoryController, VerificationCheckController, auth/check/recycler DTOs, and Producer/Recycler/VerificationCheck entities; frontend touches dashboard page, AuthForm, LandingPage, Navbar, RecyclerDetailsForm, api types, and all three locale message files, plus a new untracked `app/[locale]/used-oil/` directory. Per task scope this run only tracks committed history, so none of this is reflected above — will pick it up once committed.

## 2026-07-07 (third run)

- Synced backend HEAD: `b36e5bbf8087724b2391b11e62951d63b4a835f9`
- Synced frontend HEAD: `456bca21b16485c7346cb1749d414064add39ecf`
- Commits reviewed:
  - Backend: `b36e5bb` — "feat: add used oil registration module with tier determination, fee calculation, and PDF generation." This single commit contains the full Module A0 (KYC gate)/Module D (tyre)/Module E (used-oil) build plus `PlausibilityStrategy` refactor, migrations V8/V9, and — notably — it also swept up PRODUCT_DOCUMENT.md and PRODUCT_DOCUMENT_SYNC_LOG.md as committed files for the first time (150 and 26 lines respectively), meaning the doc content from this task's first two runs is now committed, not just sitting in the working tree. Checked the doc against the actual diff: already accurate, no correction needed for the code this commit adds.
  - Frontend: `8b6e1b7` (Used-Oil page, CredentialChecksCard, UsedOilAssistantForm/SummaryResult, waste-stream selector, landing page waste-streams promo section + nav links, RegisterForm KYC fields — all already reflected in the doc except the landing/nav additions below), `9bc0b49` (translation-only: waste-stream fields added to recycler-details-form strings in en/hi/te, no doc-visible change), `456bca2` (empty commit, redeploy trigger only, no file changes).
- Changes made to PRODUCT_DOCUMENT.md:
  - §3 Landing page: added mention of the new `StreamsSection` (3-card Battery/Tyre/Used-Oil promo, added when Module E shipped) and the new direct `/used-oil` link added to both the public landing nav and the authenticated app `Navbar`. Everything else in this commit (RegisterForm KYC fields, CredentialChecksCard on dashboard, RecyclerDetailsForm waste-stream selector, UsedOilAssistantForm/SummaryResult) was already correctly documented from prior runs.
- Note (observation only, not a doc change): the same uncommitted working-tree changes flagged in the second-run entry above are still uncommitted in the backend repo (SecurityConfig, EvidenceController, RecyclerController, RegulatoryHistoryController, VerificationCheckController, auth/check/recycler DTOs, Producer/Recycler/VerificationCheck entities) — unchanged since last run, still awaiting a commit before this task can review them.
- Edit is uncommitted in the backend repo working tree, waiting for Eshwar's review.

## 2026-07-07 (fourth run)

- Synced backend HEAD: `3b02fa8baecb180e869f2c67ad1613c480e367ea`
- Synced frontend HEAD: `c949c40c7779c3a22a90d03dbff7d3520280775e`
- Commits reviewed:
  - Backend: `b674b96` — "feat: implement tyre EPR credit reconciliation logic and update related models." Replaces the earlier 400–450 L/tonne TPO-yield-ratio benchmark (`TyreBenchmarks`, deleted) with CPCB's own certificate-generation formula, QEPR = QP × CF × WP (`TyreEndProduct` enum + `TyreEprReconciliation`, migration V10 adds `tyre_end_product`/`tyre_imported`/`claimed_epr_credit_kg` to `verification_checks`). This commit's own diff also happened to carry the previous (third) run's uncommitted PRODUCT_DOCUMENT.md/SYNC_LOG.md edits into the repo's history — noted for context, not a code change to document.
  - Backend: `3b02fa8` — "feat: implement composite risk scoring and hard-disqualification logic for verification checks." New `CompositeScoringService` + `CompositeScoreWeights` (PRD §7.1a): five weighted sub-scores (registration, capacity, invoice, forensics, regulatory), recomputed at check creation / evidence upload / regulatory-history completion, mapped to LOW/MEDIUM/HIGH/CRITICAL (new `RiskRating.CRITICAL`). Hard-disqualification (capacity ratio >3x or active NGT/suspension finding) forces score=100. Migration V11 adds the score columns to `verification_checks`. Also added `GET /api/v1/checks/{id}/evidence` (read-later evidence summary endpoint) and a PDF-text sanitizer in `ReportService` (WinAnsi encoding, was throwing on curly quotes/em-dashes from LLM regulatory summaries).
  - Frontend: `330ee6f` (RecyclerDetailsForm tyre fields — end-product dropdown, EPR credit input, imported checkbox — matching the backend's new tyre DTO fields; translations added to all 3 locales), `c949c40` (new `/checks` list page and `/verify/[checkId]` detail page, both rendering a new shared `CheckResults` component; new `CompositeScoreCard` and shared `RiskPill` — extracted from dashboard, gained a CRITICAL style; `VerifyFlow`'s inline results step now delegates to `CheckResults` instead of duplicating the layout; dashboard links to `/checks` instead of `/verify` and rows now navigate to the detail page).
- Changes made to PRODUCT_DOCUMENT.md:
  - §3 Module A: rewrote intro to mention composite scoring + CRITICAL rating; added a full new paragraph on `CompositeScoringService` (recompute timing, 5 sub-scores, neutral-50 default, uncalibrated weights caveat, hard-disqualification and which of the PRD's rules are/aren't wired); noted the PDF report now includes the score breakdown and sanitizes text; added the new `GET .../evidence` route; rewrote the frontend bullet for `/checks`, `/verify/{checkId}`, shared `CheckResults`/`CompositeScoreCard`/`RiskPill`.
  - §3 Module D: replaced the stale TPO-yield-benchmark description with the QEPR=QP×CF×WP reconciliation logic, end-product enum, and the imported-tyre WP override; noted the old benchmark file was deleted.
  - §4 Data model: added the new tyre fields (`tyreEndProduct`, `tyreImported`, `claimedEprCreditKg`) and the composite-scoring fields on `VerificationCheck`; noted `RiskRating` gained `CRITICAL`.
  - §8 Known gaps: updated the Module D gap (yield benchmark now CPCB-sourced, but deviation tolerances still provisional); added two new gaps for composite-score calibration and partial hard-disqualification-rule coverage.
- Note (observation only, not a doc change): the same uncommitted backend working-tree changes flagged in runs two and three are still present and largely unchanged in scope (SecurityConfig, EvidenceController, RecyclerController, RegulatoryHistoryController, VerificationCheckController, auth/check/recycler DTOs, Producer/Recycler/VerificationCheck entities, plus gradle wrapper files and `application.yml` this run) — still uncommitted, still awaiting a commit before this task can review them. Frontend working tree is currently clean (only an untracked `.claude/settings.local.json`, not reviewed — tooling config, not product code).
- Edit is uncommitted in the backend repo working tree, waiting for Eshwar's review.

## 2026-07-08 (fifth run)

- Synced backend HEAD: `d01948b254ee869a47048be00df0c71ca35cc0fe`
- Synced frontend HEAD: `f16eae1bf074fbc928ce0615c77d065d873684b7`
- Commits reviewed:
  - Backend: `d01948b` — "feat: add CPCB recycler directory with ingestion, scoring, and search functionality." New standalone module: `CpcbRecycler`/`CpcbRecyclerAuthorization`/`CpcbRecyclerScore`/`CpcbGeoRiskHotspot` entities (migrations V12 + V13 seed), CSV ingestion (`CpcbRecyclerCsvParser`, Apache Commons CSV added to `build.gradle.kts`) with a partial-capture heuristic, `CpcbRecyclerScoring` (Entity Health Score — registration/authorization/geography only, explicitly not a Certificate Risk Score since this data source has no certificate-volume/invoice fields), `CpcbRecyclerSearchService` (excludes individual-contact PII from responses), `CpcbRecyclerController` (`POST .../ingest` ADMIN-only, `GET .../search` any authenticated role). This commit's own diff also carried the previous (fourth) run's uncommitted PRODUCT_DOCUMENT.md/SYNC_LOG.md edits into the repo's history — noted for context, not new code to document.
  - Frontend: `f16eae1` — new `/cpcb-directory` page (sign-in gate, `CpcbRecyclerSearchForm` + `CpcbRecyclerResultCard`), `lib/api/cpcbRecyclers.ts` + new types in `lib/api/types.ts`, `Navbar` link (hidden for `RECYCLER` role), translations in all 3 locales.
- Changes made to PRODUCT_DOCUMENT.md:
  - §3: added new "Module F — CPCB Recycler Directory" section covering ingestion/upsert-by-CPCB-id/partial-capture heuristic, the Entity Health Score point values (which are spec-given vs. assumed placeholders), the geographic-hotspot check, the not-implemented ISO bonus, PII exclusion from search results, and backend/frontend routes.
  - §4 Data model: added the four new entities (`CpcbRecycler`, `CpcbRecyclerAuthorization`, `CpcbRecyclerScore`, `CpcbGeoRiskHotspot`).
  - §8 Known gaps: added a Module F bullet (Entity-Health-only scope, unverified point values/capacity units, ISO bonus not implemented, static hotspot list).
- Note (observation only, not a doc change): the same uncommitted backend working-tree changes flagged in runs two through four are still present, unchanged in scope (SecurityConfig, EvidenceController, RecyclerController, RegulatoryHistoryController, VerificationCheckController, auth/check/recycler DTOs, Producer/Recycler/VerificationCheck entities, gradle wrapper files, `application.yml`) — still uncommitted, still awaiting a commit before this task can review them. Also new this run: an untracked `CLAUDE.md` at the backend repo root (agent/tooling config, not product code — not reviewed) and an untracked `.claude/settings.local.json` in both repos.
- Edit is uncommitted in the backend repo working tree, waiting for Eshwar's review.
