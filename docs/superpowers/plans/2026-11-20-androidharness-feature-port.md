# AndroidHarness Feature Port Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans or superpowers:subagent-driven-development once the plan is approved. Verify each stage before moving on.

**Goal:** Bring the requested AndroidHarness features to Clodex, with the functional **Настройки → Программирование** hub delivered last.

**Architecture:** Use `AndroidHarness-main/` and the supplied screenshots as references. Reuse Clodex’s Compose screens, DataStore preferences, workspace gateways, agent approval flow and verified updater; add Room storage and background scheduling only where required.

**Tech stack:** Kotlin, Jetpack Compose, DataStore, Room, WorkManager, Android SDK 26+.

## Constraints

- Keep Classic/Studio, tablet layout, the read-only file window and the isolated HTML preview. Blur is opt-in, without glow or decorative gradients.
- Preserve the upstream MIT copyright and permission notice for copied code; do not stage the reference checkout, screenshots, credentials or keys.
- GitHub updates target Clodex only. No configured repository means no network check. Keep explicit installation consent and APK package, version and signer verification.
- No new root, Shizuku, SSH, shell or arbitrary plugin execution in this port. The Coding hub is settings, not a rewrite of the main Code screen.

## 1. Appearance, About, Setup and Updates

- [ ] Extend `data/Settings.kt` and `ui/screens/SettingsScreens.kt`: add straight/wave progress preferences and optional scroll blur. Apply the selected style to actual update progress. Keep text readable and use the unblurred surface on unsupported Android versions. Verify both preferences survive reopening; verify Classic stays unchanged by default.
- [ ] Add separate navigation for **О приложении**, **Обновления** and **Руководство по настройке**. About shows installed version and licenses; Setup links to real provider, model, workspace and search settings. Updates reuses `ui/screens/UpdateControls.kt` and `updates/GitHubUpdates.kt`. Show measured bytes during downloads and indeterminate progress when no total is known. Verify progress ends on failure and no APK is requested before consent.

## 2. Workspace, permissions and web search

- [ ] Add the AndroidHarness-style **Сменить рабочую область** picker to Code using existing SAF/GitHub gateways. Validate access before changing the selection; don't move pending diffs to another workspace. Test canceled picker, revoked access and staged diffs.
- [ ] Wire permission choices to the actual agent approval and Code apply policies, reusing existing Plan/Edits/Auto modes. Keep remembered grants scoped and revocable. Verify Plan cannot write and revocation restores the prompt.
- [ ] Add **Поиск в интернете** settings backed by the existing Anthropic/OpenRouter implementations. Verify selected mode changes the real request, citations survive the stream, and unsupported providers are reported rather than silently pretending to search.

## 3. Scheduled messages and memory

- [ ] Port the schedule form, saved entries and cancel action using Room and unique WorkManager jobs. Start with once/daily/hourly, a fixed chat/model/workspace, visible run state and no write actions without the normal approval flow. Verify offline delay, cancellation, busy-chat handling, and no auto-retry after an ambiguous dispatch that could duplicate a message. Document that Android may delay execution.
- [ ] Add user-editable global and project memory for Chat and Code. Include only the enabled scope in model context with a size limit; support view, search, edit and delete. Verify no cross-project recall and no inclusion after disabling or deleting a note.

## 4. Coding settings hub — last

- [ ] Add **Настройки → Программирование**, grouped like `SettingsNavigation.kt` → `settingsCodingGroups` in AndroidHarness. Connect working controls for agent instructions and behavior, Code chat defaults, workspace, skills and supported configuration. Where an upstream section has no Clodex implementation, omit it rather than add a dead control.
- [ ] Verify persistence and behavior: changed Code defaults affect a new session, instructions reach a model request, disabled skills are not advertised, and existing sessions keep their workspace and drafts.

## Verification

For each stage, add/run targeted tests before and after the change, then run `sh gradlew assembleDebug testDebugUnitTest --offline`. Check migration and work resumption on device for scheduled messages, scrolling and layout on tablet for UI, and real install with two same-signer APK versions for updates. Keep the previous APK intact; distinguish emulator/device checks from unit tests.

### Stage 3 device checklist (not automated)

1. Install the stage 3 APK over stage 2 without uninstalling. Open an existing chat and project to confirm their data survived. Save separate shared and project notes, then verify that disabling a scope excludes its notes from a new request.
2. With a test provider, schedule a once-only message. Go offline before it is due, then reconnect. Verify that the chat gets exactly one prompt/reply pair. Repeat while a manual reply is in progress; the scheduled message should wait.
3. Cancel a waiting message and a separate in-flight message before a reply arrives. Neither should appear in the chat or in the next manual request. Cancellation cannot retract a request the provider has already received.
4. Interrupt the app process after a test provider receives a scheduled request but before it sends a reply. Reopen the app and verify that the occurrence ends with an error rather than sending again. Also verify that an untouched waiting schedule still runs after reopening.
5. Schedule a project chat, move it to another project before the due time, and verify that no request is sent. During a separate in-flight occurrence, verify that the chat cannot be moved and the original project cannot be deleted until the occurrence stops.
