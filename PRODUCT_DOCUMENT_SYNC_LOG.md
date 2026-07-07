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
