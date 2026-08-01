# Cycluna — Compliance & Security Checklist (living doc)

Cycluna is a **reproductive-health app** → App Store Guideline **5.1.3** + heightened
privacy law. **Not legal advice — get a professional review of the privacy policy and a
regulatory classification review before launch.**

**v1 architecture = LOCAL-ONLY.** No accounts, no server, no network calls, no analytics.
All data lives on the device. This makes the privacy story simple and strong ("Data Not
Collected"), and makes most account/backend compliance items **not applicable** until the
sync milestone (see bottom).

Legend: ✅ done · 🔨 partial · ⬜ todo (before submission) · ➖ N/A for local-only v1

## App Store review
- ➖ **Sign in with Apple** — no third-party sign-in (no accounts at all)
- ✅ **In-app data deletion** — Me › “Delete all my data” wipes the local store (5.1.1(v))
- ✅ **Data export** — Me › “Export my data” → on-device JSON via the share sheet (GDPR/CCPA)
- ✅ **Privacy Manifest** `PrivacyInfo.xcprivacy` — present; keep accurate as data use grows
- ✅ **Encryption export compliance** — `ITSAppUsesNonExemptEncryption=false`. Only OS-standard
      crypto is used (file protection); no network, no custom crypto. Confirm at submission.
- ✅ **Usage strings** — `NSFaceIDUsageDescription` present (optional app lock). Local
      notifications need no usage string.
- ⬜ **App Privacy “nutrition label”** in App Store Connect — declare **Data Not Collected**
      (accurate for local-only). Do at submission.
- ⬜ **App icon / launch** — ✅ icon + branded launch added; confirm all sizes render at submit.

## Privacy & legal
- ⬜ **Privacy Policy** — in-app screen is still a placeholder; write it (local-only makes it
      short) and host a URL for the store listing.
- 🔨 **Consent + disclaimers at onboarding** — onboarding states “wellness & education, not
      medical advice” and “everything stays on your device.” Consider an explicit health-data
      acknowledgement for GDPR / CCPA / WA My Health My Data Act.
- ✅ **No health data for ads/marketing; never sold** — zero third-party analytics/ad SDKs,
      no network path at all.
- ✅ **Data minimization** — no location, no account/PII, on-device only.
- ➖ **E2E-encrypt notes** — deferred with sync. For local-only, at-rest protection comes from
      `.completeFileProtection` + the optional app lock (added AES-at-rest was reverted as
      premature — it added a data-loss risk with no real gain without a server).
- ✅ **Medical disclaimer (in-app)** — onboarding line + hormone-chart disclaimer + educational
      framing. ⬜ Add a Terms screen for the store.
- ⬜ **Age rating** — set Health & Fitness, 13+ baseline (COPPA) in App Store Connect.
- ✅ **No pseudo-scientific claims** — mood↔cycle and headache↔cycle correlations are only
      shown from the user’s own data with confidence thresholds; the **moon is never claimed to
      correlate** with mood/cycle (folklore, not science) — it’s an honest aesthetic companion.

## Security (v1, local)
- ✅ **Encryption at rest** — `.completeFileProtection` + atomic writes on the on-device store.
- ✅ **Biometric app lock** — optional Face ID / Touch ID / passcode gate (`LocalAuthentication`).
- ✅ **Keychain (`KeyVault`)** available via KMP `expect/actual` for future secret storage.
- ✅ **No secrets in client** — nothing to leak; no network.
- ✅ **No network surface** — offline-first means no ATS/transport concerns in v1.

## Hormone data & AI (educational only — stay out of “medical device”)
General wellness/education is unregulated; **diagnosis / disease prediction / treatment makes
you a medical device** (FDA SaMD, EU MDR Rule 11, UK MHRA). Get a regulatory review before launch.
- ✅ **Do:** typical population hormone **reference curves** (relative, not IU/L), phase-based
      education, and reflecting the user’s own logged data back (with confidence gating).
- 🚫 **Don’t:** interpret lab results, diagnose (PCOS/thyroid/menopause), predict disease,
      contraceptive-efficacy claims, dosing/treatment advice.
- ➖ **AI** — none in v1 (no cloud LLM; keeps health data on-device). If added later, keep it
      educational, on-device, and never diagnostic.

## Deferred to the sync milestone (when a backend is added)
Only relevant once data leaves the device. Full plan: `../cycluna-backend/docs/native-migration-plan.md`.
- Accounts + Sign in with Apple, `DELETE /account`, `GET /account/export`
- **True E2E `enc:v1` crypto** (the real “even the server can’t read it” feature)
- Passkey-first auth, JWT authz (`WHERE user_id = ?`), strict ATS, encryption at rest/in transit on D1
