# Manual Test Cases — Module A0 (KYC gate), Module D (Tyre/TPO), Module E (Used-Oil)

Prereqs for all cases:
- Backend running (`./gradlew bootRun`) against a Postgres instance with migrations applied (V9 head)
- Frontend running (`npm run dev`), `NEXT_PUBLIC_API_URL` pointed at that backend
- Swagger UI available at `/swagger-ui.html` for API-level cases

---

## Module A0 — Recycler KYC/Credential Gate

### TC-A0-01: Register RECYCLER with all KYC fields
1. Go to `/register`
2. Fill name/email/password, select role "Recycler"
3. Confirm optional fields appear: GSTIN, Legal business name, Udyam registration number, CIN/DIN
4. Fill GSTIN + Legal name + Udyam number, leave CIN/DIN blank
5. Submit

**Expected**: Account created, redirected to `/dashboard`. No error shown regardless of KYC outcome.

### TC-A0-02: Register RECYCLER with no KYC fields
1. Register as Recycler, leave all 4 optional fields blank
2. Submit

**Expected**: Account created successfully (KYC fields must never be required).

### TC-A0-03: Credential check results appear on dashboard
1. After TC-A0-01, go to `/dashboard`
2. Locate "Account verification" card

**Expected**: Shows one row per submitted field (GST verification, Udyam verification — not MCA, since CIN/DIN was blank). Each shows result pill. With no KYC vendor configured, all rows show "Could not verify" (amber). Card includes the line "Step one of an ongoing verification relationship — not full vetting."

### TC-A0-04: Non-recycler roles unaffected
1. Register as "Publisher / Producer"

**Expected**: No KYC fields shown. Registration flow identical to before this change.

### TC-A0-05: KYC failure never blocks registration (backend)
1. Via Swagger/curl, POST `/api/v1/auth/register` with role RECYCLER and a malformed/edge-case GSTIN (e.g. empty string, very long string)

**Expected**: 201 Created regardless. Registration must succeed even if the credential-check call internally throws.

### TC-A0-06: Non-recycler cannot read credential-check endpoint
1. Log in as a PUBLISHER/CONSULTANT user
2. GET `/api/v1/recyclers/me/credential-checks` with that user's token

**Expected**: 403 Forbidden.

### TC-A0-07: Recycler with no linked profile
1. This shouldn't be reachable via normal registration (Recycler row always created), but if testing directly: GET `/api/v1/recyclers/me/credential-checks` for a RECYCLER user whose Recycler row was manually deleted

**Expected**: 404 Not Found.

---

## Module D — Tyre/TPO Risk Check

### TC-D-01: Default check is still BATTERY (regression)
1. Go to `/verify`, leave Waste stream on "Battery" (default)
2. Fill recycler/producer/batch details as before, submit

**Expected**: Behaves identically to pre-change battery flow — plausibility sub-checks named "Recovery rate plausibility" / "Capacity ceiling check" / "Absolute batch size check", same thresholds as before.

### TC-D-02: Switch to Tyre — field swap
1. On `/verify` step 1, change Waste stream to "Tyre / TPO"

**Expected**: "Claimed recovery %" field is replaced by "Claimed TPO output (litres) *". BWMR field label becomes generic "Registration number".

### TC-D-03: Tyre check — plausible yield (PASS)
1. Waste stream = Tyre, batch weight = 100 T, claimed TPO output = 42,000 L (→ 420 L/T)
2. Submit

**Expected**: Plausibility result shows "TPO yield plausibility" = PASS, detail cites "420.00 L/tonne ... within the typical 400.0–450.0 L/tonne range."

### TC-D-04: Tyre check — implausible yield (FAIL)
1. Same as above but claimed TPO output = 90,000 L (→ 900 L/T)

**Expected**: "TPO yield plausibility" = FAIL, overall status FAIL. Detail cites the yield exceeding the physical ceiling.

### TC-D-05: Tyre check — low yield (WARN, not FAIL)
1. Batch weight = 100 T, claimed TPO output = 30,000 L (→ 300 L/T, below the 400 L/T floor)

**Expected**: "TPO yield plausibility" = WARN (not FAIL) — under-yield is atypical but not physically impossible.

### TC-D-06: Tyre check — no output quantity provided
1. Via API, POST `/api/v1/checks` with `wasteStream: "TYRE"` and `claimedOutputQuantity` omitted

**Expected**: "TPO yield plausibility" = UNVERIFIABLE, not a validation error / not silently passing.

### TC-D-07: Capacity ceiling and batch size checks still apply to tyre
1. Create a tyre check for a recycler with a known `recyclerSelfReportedCapacityT`, batch weight close to/over that capacity

**Expected**: "Capacity ceiling check" behaves the same way it does for battery (WARN/FAIL at the same ratio thresholds) — confirms the shared generic checks are reused correctly for tyre.

### TC-D-08: Read-back label correctness
1. Create a tyre check, note its ID
2. GET `/api/v1/checks/{id}` (or view it again later in the UI)

**Expected**: Sub-check name still reads "TPO yield plausibility" (not "Recovery rate plausibility") on re-fetch — confirms the label isn't hardcoded to battery on the read path.

### TC-D-09: Forensics/regulatory/report unaffected by waste stream
1. Create a tyre check, upload evidence, trigger regulatory history, download the PDF report

**Expected**: All three work exactly as they do for battery checks (no crashes, no missing sections) — confirms these layers are genuinely waste-stream-agnostic.

### TC-D-10: Existing battery checks list correctly alongside tyre checks
1. Create one battery check and one tyre check under the same account
2. GET `/api/v1/checks` (or view `/dashboard` recent checks)

**Expected**: Both appear; each `VerificationCheckResponse` shows its own correct `wasteStream`.

---

## Module E — Used-Oil CA-1/CA-2 Registration Assistant

### TC-E-01: Access without login
1. Log out (or use incognito), navigate to `/used-oil`

**Expected**: Page loads, fully usable — no auth redirect.

### TC-E-02: Tier determination — CA-1 path
1. On `/used-oil`, leave both checkboxes ("storage facility", "truck fleet") unchecked
2. Submit

**Expected**: "You fit: CA-1" with rationale explaining pickup/transport-only fits CA-1.

### TC-E-03: Tier determination — CA-2 path
1. Check both "storage facility" and "truck fleet"
2. Submit

**Expected**: "You fit: CA-2", CA-2 readiness checklist displayed (storage facility, truck fleet, geo-tagged photos, lab access, GST-linked account, attached CA-1/recycler list, storage capacity/equipment).

### TC-E-04: CA-1 prerequisite gate — agreement missing
1. Reach the CA-1 branch (TC-E-02)
2. Click "Not yet" (no signed agreement)

**Expected**: Message states CA-1 registration requires a signed agreement in place before applying, portal won't accept without it. Lists required agreement contents (identity of both parties, scope, validity period, signatures). "Continue to fee estimate" button disabled.

### TC-E-05: CA-1 prerequisite gate — agreement present
1. Same branch, click "I have a signed agreement"

**Expected**: Message confirms can proceed. "Continue" button enabled.

### TC-E-06: Fee calculation — each tier boundary
Test each bracket via the fee-estimate step or directly against `/api/v1/used-oil/fee-calculation`:
| Input (avgAnnualQuantityMt) | Expected registrationFeeRs | Expected annualProcessingChargeRs |
|---|---|---|
| 15000 | 10000 | 2500 |
| 7000 | 5000 | 1250 |
| 3000 | 2000 | 500 |
| 1200 | 1000 | 250 |
| 100 | 500 | 125 |

**Expected**: `totalFirstYearRs` = registrationFee + processingCharge in every case; `tierLabel` matches the bracket.

### TC-E-07: Summary — CA-1 with prerequisite met
1. Complete CA-1 path with agreement = yes, quantity = 1200
2. View summary

**Expected**: "Prerequisites met" lists the agreement; "Prerequisites outstanding" empty; next step references the 3-part CA-1 form (Authorized Person, Vehicle, Oil Collection Details) and the "payment isn't the final step, contact SPCB" reminder.

### TC-E-08: Summary — CA-1 with prerequisite NOT met
1. Complete CA-1 path with agreement = no, quantity = 1200
2. View summary

**Expected**: "Prerequisites outstanding" lists the missing agreement; next step tells the user to prepare the agreement first, not to start the form.

### TC-E-09: Summary — CA-2
1. Complete CA-2 path, quantity = 8000
2. View summary

**Expected**: "Prerequisites outstanding" lists confirming all CA-2 readiness items; next step references the multi-tab CPCB application sections.

### TC-E-10: PDF download
1. From any completed summary, click "Download summary (PDF)"

**Expected**: A PDF file downloads (`eprid-used-oil-summary.pdf`), opens correctly, contains the fee breakdown, prerequisites, next step, and disclaimer matching what's on screen.

### TC-E-11: "Start over" resets state
1. From a completed summary, click "Start over"

**Expected**: Returns to step 1 (Tier) with all fields cleared — no stale tier/checklist/fee data bleeding into a fresh run.

### TC-E-12: No shared-state leakage with battery/tyre
1. Run a Module E flow, then separately run a battery or tyre check in `/verify`

**Expected**: No cross-contamination — Module E has no Producer/Recycler/Check records; verify no unexpected rows appear tied to Module E usage.

---

## Cross-cutting / Loose-coupling sanity checks

### TC-X-01: Battery-only regression suite
Run the full pre-existing battery flow (Module A risk check + Module B calculator + Module C1 vault) end-to-end once, with zero mention of tyre/used-oil/KYC fields, and confirm it behaves identically to before this change.

### TC-X-02: CORS / public routes
Confirm `/api/v1/used-oil/**` is reachable without a token (public) and that `/api/v1/checks/**`, `/api/v1/recyclers/**` still correctly require a Bearer token.

### TC-X-03: Swagger docs show all new endpoints
Open `/swagger-ui.html`, confirm three new tag groups appear: "Used-Oil Registration Assistant", the existing "Recycler Profile" tag now includes `/me/credential-checks`, and `POST /api/v1/checks` schema shows the new `wasteStream`/`claimedOutputQuantity` fields.
