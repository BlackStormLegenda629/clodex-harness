# Material 3 and GitHub Updater Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deliver a coherent Material Design 3 shell and a reliable GitHub Releases update flow without silent installation.

**Architecture:** Keep the existing Compose, Room, OkHttp, FileProvider, and update-domain layers. Strengthen the update protocol with focused pure tests, then make the app shell use standard Material 3 navigation and scaffold primitives while preserving existing view models and screens.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, OkHttp, kotlinx.serialization, Android PackageInstaller intent.

**Spec:** User-approved full redesign and updater design in the conversation.

## Global Constraints

- New user-facing strings are Russian.
- Updates use `BlackStormLegenda629/clodex-harness` unless `-PclodexUpdatesRepo` overrides it; an empty value disables checks.
- The APK asset name is exactly `clodex-<versionCode>.apk`.
- Installation always goes through Android's confirmation UI; no root, Shizuku, device-owner, or silent installation.
- Release APKs use `~/clodex-release.jks`, alias `clodex`.
- Do not delete files unless repository references and build usage show they are unnecessary.

## Review Focus

- Malformed, draft, prerelease, ambiguous, or oversized GitHub releases must not be offered.
- A redirect or downloaded APK from a non-GitHub asset host must remain bounded and verified.
- A failed foreground check must not block the UI or suppress the next retry.
- Update dialogs must not appear over unrelated navigation destinations or sheets.
- Compact and wide layouts must expose the same primary destinations and accessible selected states.

---

### Task 1: Updater protocol hardening

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/updates/ReleaseProtocol.kt`
- Modify: `app/src/main/java/app/clodex/harness/updates/GitHubUpdates.kt`
- Test: `app/src/test/java/app/clodex/harness/updates/ReleaseProtocolTest.kt`

**Interfaces:**
- Consumes: existing `ReleaseOffer`, `checkRelease`, `matchesUpdate`, and `ForegroundUpdateChecks`.
- Produces: deterministic validation of the latest-release payload, exact asset selection, and bounded download verification.

- [ ] Add failing tests for draft/prerelease rejection, duplicate APK rejection, exact version asset selection, and signed identity matching.
- [ ] Run the focused tests and verify they fail for the missing/incorrect behavior.
- [ ] Implement only the validation changes required by the tests; retain the existing FileProvider installation path.
- [ ] Run focused tests, then the complete unit suite.
- [ ] Commit `fix: harden github release updater`.

### Task 2: Material 3 design tokens and shell

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/theme/*`
- Modify: `app/src/main/java/app/clodex/harness/ui/components/Basics.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/nav/HarnessApp.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/MainScreen.kt` or its current owning file
- Test: existing UI/domain tests plus a new pure navigation contract test if needed

**Interfaces:**
- Consumes: existing `H`, `ScreenFrame`, `TabBar`, `Sidebar`, `Nav`, and view models.
- Produces: standard M3 `Scaffold`, `NavigationBar` on compact widths, `NavigationRail`/drawer-style navigation on wide widths, consistent top-level content insets, and accessible selected navigation items.

- [ ] Add or update a pure test pinning the four primary destinations and selected-tab mapping.
- [ ] Run it red if the current navigation contract does not satisfy it.
- [ ] Replace hand-painted top-level navigation surfaces with M3 components while retaining existing destination content and state.
- [ ] Normalize shared button, card, field, chip, dialog, and surface tokens through the existing theme layer; do not add a second design system.
- [ ] Run unit tests and lint.
- [ ] Commit `refactor: align app shell with material 3`.

### Task 3: Update presentation and lifecycle integration

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/UpdateControls.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/nav/HarnessApp.kt`
- Test: `app/src/test/java/app/clodex/harness/updates/ForegroundUpdateChecksTest.kt` and updater tests

**Interfaces:**
- Consumes: `GitHubUpdates.check/install`, `ForegroundUpdateChecks`, `ReleaseOffer`.
- Produces: M3 update dialog with current/new version, release notes, progress, retry/error states, and manual check action in settings; automatic checks run only while the app is resumed.

- [ ] Add failing lifecycle/state tests for cooldown, retry, and dismissal semantics where coverage is absent.
- [ ] Run focused tests and confirm the failure.
- [ ] Refactor the update UI into explicit checking/downloading/error/ready presentation without changing the install security boundary.
- [ ] Ensure update overlays are gated by the main destination and no active sheet.
- [ ] Run the complete unit suite and lint.
- [ ] Commit `feat: polish material update flow`.

### Task 4: Cleanup, verification, and release readiness

**Files:**
- Modify: `CHANGELOG.md`, `README.md`, `docs/releases.md` only where behavior changed
- Delete: only confirmed unused files or code found by reference/build analysis

- [ ] Search references before every deletion and preserve test fixtures and documented examples.
- [ ] Run `assembleDebug testDebugUnitTest lintDebug`.
- [ ] Build release offline, sign with `~/clodex-release.jks`, and verify package/version/signature.
- [ ] Record the APK checksum and final status; do not publish a release without explicit user confirmation.
- [ ] Commit cleanup and documentation changes.
