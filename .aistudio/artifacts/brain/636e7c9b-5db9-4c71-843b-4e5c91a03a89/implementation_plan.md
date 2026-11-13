# Food Expenses v1.4.6 — Signed-Only GitHub Stable Release Plan

Prepare and verify the **Food Expenses `v1.4.6` (`versionCode 46`)** codebase and strict signed-only GitHub Actions release pipeline without modifying any app features or exposing unpublished Orion drafts, ready for you to push, configure signing secrets, and publish the stable release.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Environment Boundary**: This workspace has no initialized Git remote or GitHub credentials and no private upload keystore. Automated verification will validate the workflow logic, version alignment, keystore exclusion guardrails, and offline test suite, while pushing to GitHub, storing signing secrets, and triggering tag `v1.4.6` are executed in your GitHub repository.

- **Confirmed Decision 1 — Target Release Tag**: Publish **`v1.4.6` (`versionCode 46`)** as-is with no feature changes or version bump.
- **Confirmed Decision 2 — Release Signing Policy**: Keep a **strict signed-only release workflow** that rejects unsigned or debug APKs and fails fast if upload signing secrets are absent.
- **Confirmed Decision 3 — Release Documentation Scope**: Keep all **Orion gate checklists, compatibility notices, and issue drafts local and unpublished** until device and Play Protect verification gates pass.

---

## 1. Overview & Core Concept

- **What It Delivers**: A verified, reproducible **Food Expenses `v1.4.6`** release baseline paired with an automated GitHub Actions pipeline that tests, builds, signs, verifies, checksums, and publishes a stable GitHub Release (`draft: false`, `prerelease: false`) exclusively from release-signed artifacts.
- **Target Operator**: Repository maintainer publishing the held `v1.4.6` candidate on GitHub after pushing from AI Studio or a local checkout.
- **Key Value**: Eliminates the risk of accidentally shipping a debug APK as a stable release, catches version-tag mismatches before artifact upload, and preserves strict separation between public GitHub builds and private Orion review templates.

---

## 2. User Experience & Release Surface

- **In-App Experience (Unchanged for `v1.4.6`)**: Preserves the finalized `v1.4.6` Food Expenses app surface—custom adaptive icon, sign-in banner, daily meal logging, bulk bazaar entry, cycle-bounded expense dates, roommate search & contribution breakdowns, dismissible `v1.4.6` pool cash banner, Excel/CSV exports, payment reminders, and background meal/expense notifications with actor attribution. No extra settings dialogs or UI changes are added.
- **Maintainer Release Flow**:
  1. Push the project from AI Studio (or export and push locally).
  2. Configure the three signing secrets (`UPLOAD_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) in GitHub repository Actions secrets.
  3. Push tag `v1.4.6` (or dispatch the workflow manually for `v1.4.6`).
  4. GitHub Actions runs verification gates, signs the release APK and App Bundle, checks APK signature integrity and tag-to-version alignment, computes `SHA256SUMS.txt`, and publishes the stable **Food Expenses v1.4.6** GitHub Release.
- **Error & Guardrail Feedback**: Missing signing secrets, accidentally tracked keystores/environment files, failing tests, or tag/version mismatches abort the pipeline immediately with clear console errors before any release object is created.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Strict Signed-Only Pipeline (No Debug Fallback)**
  - *Chosen Approach*: Fail the release workflow immediately if any of the three signing secrets are missing and build only release-signed APK and AAB artifacts.
  - *Why*: Guarantees the published GitHub Release contains only the true release-signed candidate suitable for downstream distribution and Orion verification.
  - *Alternatives Considered*: Attaching a debug APK fallback or splitting into a debug prerelease workflow; rejected to prevent confusion between test builds and the release candidate.

- **Decision 2 — Tag-to-Version Alignment Guardrail**
  - *Chosen Approach*: Verify during CI that the requested tag (`v1.4.6`) matches the configured Android app version (`1.4.6`) before publishing.
  - *Why*: Prevents accidental mismatches between the GitHub Release tag and the compiled APK metadata.
  - *Alternatives Considered*: Deriving `versionName` dynamically from Git tags; rejected to keep `v1.4.6` (`versionCode 46`) declarative and identical across local and CI builds.

- **Decision 3 — Local-Only Orion Templates**
  - *Chosen Approach*: Retain the Orion gate checklist, release notes draft, and new issue draft strictly as unpublished local templates.
  - *Why*: Ensures Play Protect verdicts, physical device clean/upgrade checks, and signed release SHA-256 checksums are captured from the real signed candidate before filing a new Orion issue.
  - *Alternatives Considered*: Publishing release notes templates externally beforehand; rejected per release-gate policy.

---

## 4. Technical Architecture & Release Pipeline *(Technical Reference)*

### CI/CD Verification & Release Pipeline

```
┌───────────────────────────┐      ┌──────────────────────────────────┐
│ GitHub Trigger            │      │ Pre-Build Guardrails             │
│ • Push tag: v*.*.*        │─────▶│ • Reject tracked .jks/.keystore  │
│ • Manual dispatch (tag)   │      │ • Require 3 signing secrets      │
└───────────────────────────┘      │ • Validate tag == versionName    │
                                   └─────────────────┬────────────────┘
                                                     │
                                                     ▼
┌───────────────────────────┐      ┌──────────────────────────────────┐
│ Offline Test & SDK Gate   │      │ Signed Release Build & Verify    │
│ • Accept Android licenses │─────▶│ • Decode temporary upload key    │
│ • Run unit & Robolectric  │      │ • Build release APK & AAB bundle │
│   test suite              │      │ • Run apksigner cert verify      │
└───────────────────────────┘      │ • Compute SHA256SUMS.txt         │
                                   └─────────────────┬────────────────┘
                                                     │
                                                     ▼
                                   ┌──────────────────────────────────┐
                                   │ Cleanup & Stable Release Publish │
                                   │ • Always delete temp keystore    │
                                   │ • Publish GitHub Release         │
                                   │   (draft: false, prerelease: no) │
                                   └──────────────────────────────────┘
```

### Pipeline Stages & Guardrails
- **Git Hygiene Gate**: Scans tracked files in Git and halts if private keystores (`.jks`, `.keystore`) or local environment secrets are present, while permitting AI Studio export pushes without false failures.
- **Credential & Secret Gate**: Validates presence of `UPLOAD_KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` before executing Gradle release tasks.
- **Version Lock Check**: Extracts the target version from the release tag (e.g., stripping leading `v` from `v1.4.6`) and asserts exact parity with the configured application `versionName` (`1.4.6`).
- **Verification & Checksums**: Runs offline unit and Robolectric tests, generates release APK and AAB bundles, verifies APK signing certificates with `apksigner`, cleans up the ephemeral keystore in an `always()` step, and uploads `food-expenses-<tag>-release.apk`, `food-expenses-<tag>-release.aab`, and `SHA256SUMS.txt` to the stable GitHub Release.
