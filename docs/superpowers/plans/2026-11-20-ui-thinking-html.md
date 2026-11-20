# UI Polish and Thinking HTML Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add model/provider marks, bundled font choices, globally opaque sheets, reusable thinking UI, and the requested standalone `thinking.html`.

**Architecture:** Keep visual metadata local and deterministic. Route all typography through `HarnessTheme`, all sheets through one opaque `SheetSurface`, and expose the thinking row/dialog as stateless Compose components so the agent/chat plan can supply live state later.

**Tech Stack:** Kotlin 2.0, Jetpack Compose, DataStore Preferences, Android resources, plain HTML/CSS/JS.

**Spec:** `docs/superpowers/specs/2026-11-20-real-connectors-code-thinking-design.md`

## Global Constraints

- No runtime font or logo downloads.
- The sheet itself must be opaque; only the screen scrim remains translucent.
- Thinking text is a summary of provider/agent events, never hidden chain-of-thought.
- Motion uses 200–250ms ease-out and honors disabled Android animations / `prefers-reduced-motion`.
- The workspace is not a Git repository; do not initialize Git or fabricate commit checkpoints.

## Review Focus

- Unknown custom provider IDs render a generic mark instead of crashing — covered in Task 1.
- A saved font name removed in a later build falls back to Inter/System Mono — covered in Task 2.
- Every sheet brush stop is alpha 1.0 in light and dark themes — covered in Task 3.
- Empty and one-step thinking summaries remain usable — covered in Task 4.
- HTML modal restores focus and closes by X, scrim, and Escape — covered in Task 5.

---

### Task 1: Provider/model marks

**Files:**
- Create: `app/src/main/java/app/clodex/harness/ui/components/ProviderMark.kt`
- Create: `app/src/main/res/drawable-nodpi/provider_anthropic.png`
- Create: `app/src/main/res/drawable-nodpi/provider_openai.png`
- Create: `app/src/main/res/drawable-nodpi/provider_deepseek.png`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/Sheets.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/ChatScreens.kt`
- Test: `app/src/test/java/app/clodex/harness/ProviderPresentationTest.kt`

**Interfaces:**
- Produces: `fun providerPresentation(providerId: String): ProviderPresentation`
- Produces: `@Composable fun ProviderMark(providerId: String, name: String, size: Dp = 34.dp)`
- Extends: `Composer(..., providerId: String, model: String, ...)` so the compact chip never guesses a provider from display text
- Consumes: `ModelInfo.providerId`, `ProviderCfg.id`, `ProviderCfg.name`

- [ ] **Step 1: Write failing tests** for Anthropic/OpenAI/DeepSeek resource selection and generic custom fallback initials/color.
- [ ] **Step 2: Run** `sh gradlew testDebugUnitTest --tests app.clodex.harness.ProviderPresentationTest`; expect failures because presentation mapping does not exist.
- [ ] **Step 3: Add local logo assets and `ProviderPresentation(providerId, drawableRes, fallbackLetter, tint)` mapping.** Custom IDs must return no drawable and the first uppercase character of the provider name.
- [ ] **Step 4: Replace letter-only `ProviderBadge`/`ProvDot` with `ProviderMark` and add the compact mark to the composer model chip.**
- [ ] **Step 5: Run** the focused test and `sh gradlew assembleDebug`; expect success.

### Task 2: Bundled UI and code font catalogs

**Files:**
- Create: `app/src/main/java/app/clodex/harness/ui/theme/FontCatalog.kt`
- Add: `app/src/main/res/font/manrope_{regular,medium,semibold}.ttf`
- Add: `app/src/main/res/font/noto_sans_{regular,medium,semibold}.ttf`
- Add: `app/src/main/res/font/fira_code_{regular,medium}.ttf`
- Add: `app/src/main/res/font/ibm_plex_mono_{regular,medium}.ttf`
- Add: `app/src/main/res/font/source_code_pro_{regular,medium}.ttf`
- Modify: `app/src/main/java/app/clodex/harness/data/Settings.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/theme/Theme.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/SettingsScreens.kt`
- Test: `app/src/test/java/app/clodex/harness/FontCatalogTest.kt`

**Interfaces:**
- Produces: `UiFontCatalog.names: List<String>` and `UiFontCatalog.family(name: String): FontFamily`
- Produces: `CodeFontCatalog.names: List<String>` and `CodeFontCatalog.family(name: String): FontFamily`
- Produces: `AppSettings.uiFont: String`, preference key `K.uiFont`

- [ ] **Step 1: Write failing tests** asserting exact UI names `Inter`, `Manrope`, `Noto Sans`, `System`; code names `JetBrains Mono`, `Fira Code`, `IBM Plex Mono`, `Source Code Pro`, `System Mono`; and fallback names.
- [ ] **Step 2: Run** the focused test; expect unresolved catalogs.
- [ ] **Step 3: Add verified TTF resources and catalog mappings.** Keep all weights referenced by `HText` available.
- [ ] **Step 4: Persist `uiFont` in `AppSettings`/`K` and select both families in `HarnessTheme`.** Unknown names fall back to Inter and JetBrains Mono.
- [ ] **Step 5: Replace the two-row code-font selector with UI-font and code-font groups generated from the catalogs.**
- [ ] **Step 6: Run** focused tests plus `assembleDebug`; expect success.

### Task 3: Fully opaque sheet surface

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/components/Sheet.kt`
- Test: `app/src/test/java/app/clodex/harness/SheetPaletteTest.kt`

**Interfaces:**
- Produces: `fun opaqueSheetStops(colors: HColors): List<Color>`

- [ ] **Step 1: Write failing tests** for light and dark `HColors` asserting every returned stop has `alpha == 1f` and the final stop equals `colors.bg`.
- [ ] **Step 2: Run** the focused test; expect `opaqueSheetStops` unresolved.
- [ ] **Step 3: Implement opaque pre-composited accent stops and paint a solid `c.bg` before the decorative brush in `SheetSurface`.** Keep scrim alpha unchanged.
- [ ] **Step 4: Run** tests/build and inspect Add Skill, Model, Provider, Connector, Workspace, and delete-confirmation call sites to ensure none adds a transparent parent.

### Task 4: Reusable Android thinking components

**Files:**
- Create: `app/src/main/java/app/clodex/harness/ui/components/Thinking.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/ChatScreens.kt`
- Test: `app/src/test/java/app/clodex/harness/ThinkingPresentationTest.kt`

**Interfaces:**
- Produces: `ThinkingPresentation(chatId: Long? = null, thinking: Boolean, startedAt: Long? = null, seconds: Int? = null, steps: List<String>)` for the agent/chat plan.
- Produces: `@Composable fun ThinkingRow(state: ThinkingPresentation, onOpen: () -> Unit)`
- Produces: `@Composable fun ThinkingSummaryDialog(state: ThinkingPresentation, onDismiss: () -> Unit)`

- [ ] **Step 1: Write failing presentation tests** asserting in-progress label `Ход рассуждений`, completed label `Ход рассуждений · 12 с`, no dots after completion, and safe empty steps.
- [ ] **Step 2: Run** the focused test; expect unresolved type/functions.
- [ ] **Step 3: Implement the pure label/dot presentation model and Compose row.** Two `#d97757` dots pulse sequentially only while active.
- [ ] **Step 4: Implement the centered summary `Dialog`:** `#1f1f1f`, 24dp radius, 1dp white 6% border, no shadow, X/back/outside dismiss, serif title/steps, 220ms fade + 6dp rise, pulsing last marker.
- [ ] **Step 5: Add optional thinking rendering above assistant blocks; live integration is deferred to the shared-agent plan.**
- [ ] **Step 6: Run** tests and build.

### Task 5: Standalone single-file component

**Files:**
- Create: `thinking.html`

**Interfaces:**
- Produces browser globals: `window.addStep(text: string)` and `window.finishThinking(seconds: number)`

- [ ] **Step 1: Create semantic HTML** for a chat list item, thinking trigger, answer area, scrim, modal header, and step list in one file with no dependencies.
- [ ] **Step 2: Add exact dark tokens** `#141414`, `#1f1f1f`, `#ececec`, `#8a8a8a`, `#d97757`; responsive phone/tablet CSS; serif summary text; 24px modal corners; thin border; no shadows.
- [ ] **Step 3: Implement `addStep(text)`** so the previous marker becomes complete and a new current step enters with 220ms fade/rise.
- [ ] **Step 4: Implement `finishThinking(seconds)`** so indicator dots disappear, heading becomes `Ход рассуждений · N с`, current marker stops, and chat status becomes a time.
- [ ] **Step 5: Implement accessible open/close behavior** for click, X, scrim, Escape, focus trap/restore, `aria-modal`, and reduced motion.
- [ ] **Step 6: Add restartable demo logic** with 4–5 steps at exactly 1.2s intervals.
- [ ] **Step 7: Open the file manually at phone and tablet widths and verify no network requests, console errors, shadows, or overflow.**

### Task 6: UI plan verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Run** `sh gradlew testDebugUnitTest`; expect all tests pass.
- [ ] **Step 2: Run** `sh gradlew assembleDebug`; expect `BUILD SUCCESSFUL`.
- [ ] **Step 3: Update README** with font choices, opaque-sheet fix, model marks, and `thinking.html` path; do not claim Android runtime validation without adb.
