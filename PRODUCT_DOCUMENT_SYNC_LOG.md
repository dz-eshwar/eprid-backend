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
