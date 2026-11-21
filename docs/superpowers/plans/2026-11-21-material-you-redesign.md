# Material You Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Переписать интерфейс Clodex Harness в Material You, сделать «Программирование» единой страницей с Caveman и сохранить поведение приложения.

**Architecture:** Сначала тема и общие компоненты, затем экраны поверх них. `AppStyle.Material` становится стилем по умолчанию, динамические цвета берутся из системы на Android 12+, Caveman добавляется как текстовый набор инструкций.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 1.3.x (Compose BOM 2024.12.01), DataStore, Room, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-21-material-you-design.md`

## Global Constraints

- minSdk 26, targetSdk 35, Compose BOM 2024.12.01.
- Material You — стиль по умолчанию только для установок без сохранённого `style`; сохранённые Classic/Studio не перезаписываются.
- Семантические цвета (`ok`, `warn`, диффы, статусы) фиксированы во всех стилях; от обоев зависят только акцент и поверхности.
- Code не запускает команды, сборки и тесты; интерфейс не должен это утверждать.
- Не добавлять ADB, Shizuku, SSH, Linux, root, новые разрешения и сетевые вызовы.
- Лимит инструкций скиллов — 12 000 символов; блок Caveman резервируется первым.
- Все команды сборки: `sh gradlew <task> --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process`.
- Коммит после каждой задачи.

## Review Focus

1. Динамические цвета на произвольных обоях: текст на акцентных и error-поверхностях обязан брать роль `on*`, иначе контраст может упасть ниже 4.5:1.
2. Сохранённый выбор Classic/Studio не должен сбрасываться при появлении нового стиля; установка без ключа получает Material.
3. При переполнении пользовательских скиллов блок Caveman обязан остаться в промпте, а общий размер — не превысить 12 000 символов.
4. Планшетная раскладка и нижний док после переписывания компонентов: контент не должен обрезаться, а поле ввода — уезжать под IME.
5. Установка Caveman идемпотентна, удаление убирает только пять его навыков и не трогает пользовательские.

---

### Task 1: Material theme foundation

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/data/Settings.kt:96,120`
- Modify: `app/src/main/java/app/clodex/harness/ui/theme/Tokens.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/theme/Theme.kt`
- Modify: `app/src/test/java/app/clodex/harness/AppearanceStyleTest.kt`
- Create: `app/src/test/java/app/clodex/harness/ThemeRolesTest.kt`

**Interfaces:**
- Produces: `enum class AppStyle { Classic, Studio, Material }`; `AppSettings.style` default `AppStyle.Material`.
- Produces: `fun materialColors(scheme: ColorScheme, dark: Boolean): HColors`.
- Produces: `HDim.card = 24.dp`, `input = 16.dp`, `sheetTop = 28.dp`, `pill = 24.dp`, `chip = 18.dp`, `tabsBar = 72.dp`, `composer = 24.dp`, `header = 64.dp`.
- Keeps: `colorsFor(dark, accent, style = AppStyle.Classic)` signature used by tests.

- [ ] **Step 1: Update the failing default-style test**

In `AppearanceStyleTest.kt`, rename the test and change expectations:

```kotlin
@Test fun `existing and unrecognized preferences use Material`() = runBlocking {
    withRepository { settings ->
        assertEquals(AppStyle.Material, settings.flow.first().style)
        settings.set(stringPreferencesKey("style"), "FutureStyle")
        assertEquals(AppStyle.Material, settings.flow.first().style)
    }
}
```

Add a persistence case for Material in `style survives reopening...`: set `AppStyle.Material.name`, assert it reads back, then set `AppStyle.Classic.name`.

- [ ] **Step 2: Run it to verify it fails**

Run: `sh gradlew testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process --tests '*AppearanceStyleTest'`
Expected: FAIL because `Material` does not exist and the default is `Classic`.

- [ ] **Step 3: Add the style and the Material color mapping**

In `Settings.kt`: `enum class AppStyle { Classic, Studio, Material }` and `val style: AppStyle = AppStyle.Material`.

In `Tokens.kt`, add two static fallback palettes and the mapper. `materialColors` maps roles, never raw white/black:

```kotlin
fun materialColors(scheme: ColorScheme, dark: Boolean): HColors = HColors(
    bg = scheme.surface, s1 = scheme.surfaceContainerLow, s2 = scheme.surfaceContainerHigh,
    line = scheme.outlineVariant, ink = scheme.onSurface, mu = scheme.onSurfaceVariant,
    warn = if (dark) Color(0xFFF0925A) else Color(0xFF9A3412),
    ok = if (dark) Color(0xFF5BBF9F) else Color(0xFF1F6B5C),
    bb = scheme.primaryContainer, bt = scheme.onPrimaryContainer,
    ab = if (dark) Color(0xFF12291F) else Color(0xFFE7F4EE),
    at = if (dark) Color(0xFF86DDB5) else Color(0xFF14573F),
    db = scheme.errorContainer, dt = scheme.error,
    wb = if (dark) Color(0xFF3A2410) else Color(0xFFFBEBD9),
    onAc = scheme.onPrimary, ac = scheme.primary, dark = dark,
)
```

Add `MaterialLight`/`MaterialDark` `HColors` constants with the violet-blue palette from the spec, and extend `colorsFor` with `AppStyle.Material -> if (dark) MaterialDark else MaterialLight`. Update `HDim` values listed in Interfaces; keep the other dimensions.

- [ ] **Step 4: Wire dynamic color**

In `HarnessTheme`, read `LocalContext.current` and choose:

```kotlin
val colors = remember(dark, accent, settings.style) {
    if (settings.style == AppStyle.Material && Build.VERSION.SDK_INT >= 31) {
        materialColors(if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx), dark)
    } else colorsFor(dark, accent, settings.style)
}
```

Update the `LocalColors` default to `colorsFor(false, Accents[0], AppStyle.Material)`. Keep `H.studio` and the existing `MaterialTheme` role copy unchanged.

- [ ] **Step 5: Add the role-mapping test**

Create `ThemeRolesTest.kt`:

```kotlin
@Test fun materialColorsTakeForegroundsFromTheScheme() {
    val scheme = darkColorScheme(
        primary = Color(0xFF112233), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF334455), onPrimaryContainer = Color(0xFFDDEEFF),
        surface = Color(0xFF101010), onSurface = Color(0xFFEEEEEE),
        surfaceContainerLow = Color(0xFF161616), surfaceContainerHigh = Color(0xFF222222),
        onSurfaceVariant = Color(0xFFBBBBBB), outlineVariant = Color(0xFF444444),
        error = Color(0xFFFF8888), errorContainer = Color(0xFF330000),
    )
    val c = materialColors(scheme, true)
    assertEquals(scheme.primary, c.ac); assertEquals(scheme.onPrimary, c.onAc)
    assertEquals(scheme.surface, c.bg); assertEquals(scheme.onSurface, c.ink)
    assertEquals(scheme.surfaceContainerLow, c.s1); assertEquals(scheme.surfaceContainerHigh, c.s2)
    assertEquals(scheme.outlineVariant, c.line); assertEquals(scheme.error, c.dt)
}
```

- [ ] **Step 6: Run the tests**

Run: `sh gradlew testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process --tests '*AppearanceStyleTest' --tests '*ThemeRolesTest' --tests '*SheetPaletteTest'`
Expected: PASS. `SheetPaletteTest` still uses the Classic default and must stay green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/clodex/harness/data/Settings.kt app/src/main/java/app/clodex/harness/ui/theme app/src/test/java/app/clodex/harness/AppearanceStyleTest.kt app/src/test/java/app/clodex/harness/ThemeRolesTest.kt
git commit -m "feat: добавлен стиль Material You и динамические цвета"
```

---

### Task 2: Shared Material components

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/components/Basics.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/components/Rows.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/components/Sheet.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/components/Thinking.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/components/TransferProgress.kt`

**Interfaces:**
- Keeps every public composable name and signature already imported by screens: `Ic`, `hClick`, `rise`, `IconBtn`, `Header`, `Btn`, `HChip`, `Seg`, `Sw`, `Srch`, `Emp`, `Bdg`, `Fab`, `Dock`, `TabBar`, `ScreenFrame`, `Body`, `ToastHost`, `Sg`, `Grp`, `GrpOf`, `Li`, `LiField`, `It`, `IconPlate`, `Titles`, `R2`, `R2Choice`.
- Produces: `Btn(..., tonal: Boolean = false)` for the M3 tonal variant used by settings.

- [ ] **Step 1: Restyle the primitives**

In `Basics.kt`: `Header` title 22 sp semibold, no hard divider; `IconBtn` 40 dp tonal circle; `Btn` height 48 dp, `RoundedCornerShape(HDim.pill)`, filled primary, tonal uses `primaryContainer`/`onPrimaryContainer`, outlined/text keep transparent background; `Fab` extended FAB 56 dp; `HChip` height 36 dp with `HDim.chip`; `Seg` unchanged API; `Sw` 52×32 with 28 dp thumb; `Srch` filled `s1` with 16 dp radius and no border; `ScreenFrame` max width 1000 dp and `bg`; `TabBar` height 80 dp, indicator pill 64×32 with `ac.copy(alpha = 0.16f)`, selected icon `ac`, selected label `ink`.

- [ ] **Step 2: Restyle rows and cards**

In `Rows.kt`: `Sg` becomes 14 sp semibold, primary color, sentence case, padding `start = 20.dp, top = 24.dp, bottom = 8.dp`; `Grp` becomes a filled `s1` card with `HDim.card`, no border, 8 dp inset dividers; `Li` min height 56 dp with `padding(horizontal = 16.dp, vertical = 12.dp)`; `IconPlate` default 40 dp circle with `ac.copy(alpha = 0.14f)` background; `R2` filled card with `HDim.card`, no border; `LiField` renders a filled field: label 12 sp `mu`, value area 16 dp radius `s2`, 12 dp inner padding.

- [ ] **Step 3: Restyle sheets and thinking**

In `Sheet.kt`: top radius `HDim.sheetTop`, drag handle 32×4 dp `mu.copy(alpha = 0.4f)`, scrim `Color.Black.copy(alpha = 0.32f)`. In `Thinking.kt` and `TransferProgress.kt`: replace hard borders with `s1`/`s2` fills and `primary` accents; keep all state logic and progress semantics.

- [ ] **Step 4: Build and run the suite**

Run: `sh gradlew assembleDebug testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process`
Expected: BUILD SUCCESSFUL, all existing tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/clodex/harness/ui/components
git commit -m "feat: общие компоненты переведены на Material You"
```

---

### Task 3: Navigation shell

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/nav/HarnessApp.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/components/Basics.kt` (`TabBar` only if a separate rail helper is added)

**Interfaces:**
- Keeps routes, `Sidebar` contents, and the bottom dock behavior.
- Produces: `@Composable fun RailItem(label: String, icon: HIcon, selected: Boolean, onClick: () -> Unit)` used by the tablet sidebar.

- [ ] **Step 1: Restyle the sidebar**

Replace the sidebar's bordered rows with M3 navigation items: 56 dp height, 28 dp pill indicator behind the selected icon, `ac.copy(alpha = 0.18f)`, label 12 sp. Keep brand, «Новый чат», search, tabs, recent chats, tools and settings in the same order and with the same actions.

- [ ] **Step 2: Restyle the phone shell**

Use the new `TabBar` in `ScreenFrame`, keep the dock above the tab bar, and keep `AnimatedContent` transitions. Do not change route names or `Nav` methods.

- [ ] **Step 3: Check tablet widths**

Keep the sidebar fixed and content capped at 1000 dp; verify no horizontal scrollbar appears at 800–1200 dp widths.

- [ ] **Step 4: Build and run the suite**

Run: `sh gradlew assembleDebug testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/clodex/harness/ui/nav/HarnessApp.kt app/src/main/java/app/clodex/harness/ui/components/Basics.kt
git commit -m "feat: навигация и планшетная оболочка в стиле Material You"
```

---

### Task 4: Chat screen

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/ChatScreens.kt`

**Interfaces:**
- Keeps all existing callbacks and state: `ChatHeader`, `ChatBody`, `onPrefill`, attachment actions, model picker, web-search toggle, generation state.

- [ ] **Step 1: Restyle the chat**

Use the new `Header`, filled `s1` composer card with 24 dp radius, 20/6 dp message bubbles, 16 dp inner padding, tonal model/attachment chips, and `Thinking` card from Task 2. Keep `H.studio` welcome behavior and the exact factual text about no command execution.

- [ ] **Step 2: Check IME behavior**

Ensure the composer stays above the keyboard; `ScreenFrame` already unions navigation-bar and IME insets — do not remove it.

- [ ] **Step 3: Build and run the suite**

Run: `sh gradlew assembleDebug testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/clodex/harness/ui/screens/ChatScreens.kt
git commit -m "feat: чат переведён на Material You"
```

---

### Task 5: Code screens

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/CodeScreens.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/CodeFilesScreen.kt`

**Interfaces:**
- Keeps session list, file window, diff cards, mode controls and every existing action, including the terminal entry.

- [ ] **Step 1: Restyle sessions and the file window**

Use 24 dp cards, tonal icon circles, 16 dp file rows, and a 24 dp diff container. Keep diff add/remove colors fixed from `HColors` and keep the compact file window behavior.

- [ ] **Step 2: Verify factual copy**

Search the two files for claims about running commands/builds/tests; keep the existing negative wording («не запускает…»). Do not add new capability claims.

- [ ] **Step 3: Build and run the suite**

Run: `sh gradlew assembleDebug testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/clodex/harness/ui/screens/CodeScreens.kt app/src/main/java/app/clodex/harness/ui/screens/CodeFilesScreen.kt
git commit -m "feat: экраны Code переведены на Material You"
```

---

### Task 6: Projects, artifacts and viewers

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/ProjectsScreens.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/ArtifactScreens.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/FilesScreen.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/HtmlViewer.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/TerminalScreen.kt`

**Interfaces:**
- Keeps project/artifact actions, SAF browsing, isolated HTML viewer behavior and terminal commands exactly as they are.

- [ ] **Step 1: Restyle the four browse surfaces**

Use filled cards, 24 dp containers, tonal icon plates and the new empty state. On tablets, project/artifact cards may use two columns within the 1000 dp cap; on phones keep one column.

- [ ] **Step 2: Keep the HTML isolation**

Do not change the viewer's URL handling, file access or JavaScript settings; only the surrounding toolbar and container change.

- [ ] **Step 3: Build and run the suite**

Run: `sh gradlew assembleDebug testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/clodex/harness/ui/screens/ProjectsScreens.kt app/src/main/java/app/clodex/harness/ui/screens/ArtifactScreens.kt app/src/main/java/app/clodex/harness/ui/screens/FilesScreen.kt app/src/main/java/app/clodex/harness/ui/screens/HtmlViewer.kt app/src/main/java/app/clodex/harness/ui/screens/TerminalScreen.kt
git commit -m "feat: проекты, артефакты и просмотрщики в стиле Material You"
```

---

### Task 7: Settings and sheets

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/SettingsScreens.kt` (all branches except `"coding"`)
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/Sheets.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/ConnectorSheets.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/SkillsConnectors.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/ScheduleScreen.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/UpdateControls.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/GoogleGmailLogin.kt`

**Interfaces:**
- Keeps every route, setting key and action; only presentation changes.
- `"coding"` stays a temporary redirect to the old content until Task 10.

- [ ] **Step 1: Restyle settings pages**

Use the new `Sg`, `Grp`, `Li`, `LiField`, `Btn`, `Seg` throughout. Provider/model/effort sheets and the workspace sheet use the new sheet surface. Keep the saved-permission revocation rows and the workspace GitHub/SAF/terminal actions.

- [ ] **Step 2: Restyle secondary screens**

Connectors, skills, schedule, updates, Gmail login: filled cards, tonal icons, 24 dp radii. Do not add OAuth scopes, tokens or new network calls.

- [ ] **Step 3: Build and run the suite**

Run: `sh gradlew assembleDebug testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/clodex/harness/ui/screens
git commit -m "feat: настройки и шторки переведены на Material You"
```

---

### Task 8: Appearance controls

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/SettingsScreens.kt:239-275`
- Modify: `app/src/main/java/app/clodex/harness/ui/theme/Tokens.kt`
- Create: `app/src/test/java/app/clodex/harness/AccentAvailabilityTest.kt`

**Interfaces:**
- Produces: `fun manualAccentAvailable(style: AppStyle): Boolean = style != AppStyle.Material`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `manual accent is hidden only for Material You`() {
    assertTrue(manualAccentAvailable(AppStyle.Classic))
    assertTrue(manualAccentAvailable(AppStyle.Studio))
    assertFalse(manualAccentAvailable(AppStyle.Material))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `sh gradlew testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process --tests '*AccentAvailabilityTest'`
Expected: FAIL because the function does not exist.

- [ ] **Step 3: Implement and wire**

Add the helper. In the `"look"` branch, show the style segment (`Classic`, `Studio`, `Material`), then show the accent row only when `manualAccentAvailable(s.style)`. For Material, show a note: «Цвета берутся из обоев на Android 12+. На более старых версиях используется палитра Material You.» Keep theme, text size, fonts, progress style and scroll blur controls.

- [ ] **Step 4: Run the tests**

Run: `sh gradlew testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process --tests '*AccentAvailabilityTest' --tests '*AppearanceStyleTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/clodex/harness/ui/screens/SettingsScreens.kt app/src/main/java/app/clodex/harness/ui/theme/Tokens.kt app/src/test/java/app/clodex/harness/AccentAvailabilityTest.kt
git commit -m "feat: выбор стиля и акцента для Material You"
```

---

### Task 9: Caveman instructions and repository

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/data/Entities.kt` (add `Src.CAVEMAN`)
- Modify: `app/src/main/java/app/clodex/harness/data/Settings.kt` (fields and keys)
- Modify: `app/src/main/java/app/clodex/harness/data/SkillInstructions.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/vm/ChatViewModel.kt:182`
- Modify: `app/src/main/java/app/clodex/harness/AppContainer.kt:82`
- Modify: `app/src/main/java/app/clodex/harness/ui/vm/SkillsViewModel.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/vm/SettingsViewModel.kt:107`
- Create: `app/src/main/java/app/clodex/harness/data/Caveman.kt`
- Create: `app/src/main/java/app/clodex/harness/data/CavemanRepository.kt`
- Create: `app/src/test/java/app/clodex/harness/CavemanTest.kt`
- Create: `app/src/test/java/app/clodex/harness/CavemanRepositoryTest.kt`

**Interfaces:**
- Produces: `enum class CavemanIntensity(val label: String) { LITE("Lite"), FULL("Full"), ULTRA("Ultra") }`.
- Produces: `object Caveman { val skillIds: List<String>; fun prompt(intensity: CavemanIntensity): String; val skills: List<SkillEntity> }`.
- Produces: `class CavemanRepository(skills: SkillRepository, settings: SettingsRepository) { suspend fun install(); suspend fun uninstall(); suspend fun setIntensity(i: CavemanIntensity); suspend fun setSkillEnabled(id: String, enabled: Boolean) }`.
- Produces: `internal fun skillInstructions(skills: List<SkillEntity>, caveman: String = ""): String`.
- Produces: `AppSettings.cavemanInstalled: Boolean = false`, `AppSettings.cavemanIntensity: CavemanIntensity = CavemanIntensity.FULL`; keys `K.cavemanInstalled`, `K.cavemanIntensity`.
- Produces: `SkillsViewModel.installCaveman()`, `uninstallCaveman()`, `setCavemanIntensity(i)`, `toggleCavemanSkill(id)`.

- [ ] **Step 1: Write the failing tests**

`CavemanTest.kt`:

```kotlin
@Test fun `every intensity has a nonempty style prompt`() {
    CavemanIntensity.entries.forEach { assertTrue(Caveman.prompt(it).contains("Caveman")) }
}
@Test fun `five skills ship with stable ids and bodies`() {
    assertEquals(listOf("caveman-commit", "caveman-review", "caveman-investigate", "caveman-patch", "caveman-verify"), Caveman.skills.map { it.id })
    assertTrue(Caveman.skills.all { it.body.isNotBlank() && it.source == Src.CAVEMAN })
}
@Test fun `caveman survives oversized user skills within the cap`() {
    val user = SkillEntity("x", "Big", "", Src.MINE, true, "Pen", body = "a".repeat(20_000))
    val block = Caveman.prompt(CavemanIntensity.FULL)
    val out = skillInstructions(listOf(user), block)
    assertTrue(out.length <= 12_000)
    assertTrue(out.contains("Caveman"))
    assertTrue(out.indexOf("Caveman") > out.indexOf("Скилл «Big»") || !out.contains("Скилл «Big»"))
}
```

`CavemanRepositoryTest.kt` uses an in-memory `SkillRepository` and a `TemporaryFolder` DataStore:

```kotlin
@Test fun `install is idempotent and uninstall only removes caveman skills`() = runBlocking {
    val repo = CavemanRepository(fakeSkills, settings)
    fakeSkills.upsert(SkillEntity("mine", "Mine", "", Src.MINE, true, "Pen", body = "x"))
    repo.install(); repo.install()
    assertEquals(6, fakeSkills.all().size)
    assertTrue(settings.flow.first().cavemanInstalled)
    repo.uninstall()
    assertEquals(listOf("mine"), fakeSkills.all().map { it.id })
    assertFalse(settings.flow.first().cavemanInstalled)
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `sh gradlew testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process --tests '*CavemanTest' --tests '*CavemanRepositoryTest'`
Expected: FAIL with unresolved `Caveman`, `CavemanRepository`, `Src.CAVEMAN`.

- [ ] **Step 3: Port the prompt and skills**

Create `Caveman.kt`. Copy the prompt text and five skill bodies from `AndroidHarness-main/app/src/main/java/com/androidharness/app/caveman/CavemanPolicy.kt`, keeping the upstream MIT notice comment (Julius Brussee; adapted by AndroidHarness). Drop `wenyan` and token-report text. `skills` returns `SkillEntity` rows with `source = Src.CAVEMAN`, `icon = "Code"`, stable `sortKey` 100–104.

Implement `skillInstructions` so the Caveman block is reserved first and appended last:

```kotlin
internal fun skillInstructions(skills: List<SkillEntity>, caveman: String = ""): String {
    val style = if (caveman.isBlank()) "" else "\n\nСтиль ответов Caveman:\n" + caveman.trim().take(4_000)
    val budget = 12_000 - style.length
    val body = buildString { /* existing loop, capped at budget */ }
    return body + style
}
```

- [ ] **Step 4: Implement the repository and settings**

`install()` upserts the five skills, then sets `cavemanInstalled = true`; `uninstall()` deletes the five ids, keeps other skills, then sets false. `setSkillEnabled` updates only the named caveman skill. Parse intensity from `K.cavemanIntensity` with `CavemanIntensity.entries.firstOrNull { it.name == raw } ?: FULL`.

- [ ] **Step 5: Wire Chat and Code**

In `ChatViewModel`: `append(skillInstructions(c.skillRepo.skills.first(), cavemanPrompt(s)))` where `cavemanPrompt` returns `Caveman.prompt(s.cavemanIntensity)` when installed, else `""`. In `AppContainer`, pass `{ val s = settings.flow.first(); skillInstructions(skillRepo.skills.first(), if (s.cavemanInstalled) Caveman.prompt(s.cavemanIntensity) else "") }` to `DefaultCodeAgent`. Add `val caveman by lazy { CavemanRepository(skillRepo, settings) }` and update `VmFactory` to pass it into `SkillsViewModel`.

- [ ] **Step 6: Run the tests**

Run: `sh gradlew testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process`
Expected: PASS, including the new Caveman tests and the existing `CodeInstructionsTest`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/clodex/harness/data app/src/main/java/app/clodex/harness/ui/vm app/src/main/java/app/clodex/harness/AppContainer.kt app/src/test/java/app/clodex/harness/CavemanTest.kt app/src/test/java/app/clodex/harness/CavemanRepositoryTest.kt
git commit -m "feat: Caveman как набор текстовых инструкций"
```

---

### Task 10: Programming page

**Files:**
- Create: `app/src/main/java/app/clodex/harness/ui/screens/ProgrammingScreen.kt`
- Create: `app/src/main/java/app/clodex/harness/data/ProgrammingSections.kt`
- Create: `app/src/test/java/app/clodex/harness/ProgrammingSectionsTest.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/SettingsScreens.kt:154-175`

**Interfaces:**
- Produces: `enum class ProgrammingSection { CAVEMAN, PERMISSIONS, WORKSPACE, MODE, MODEL, LINKS }`.
- Produces: `fun programmingSections(): List<ProgrammingSection>` in the order above.
- Produces: `@Composable fun ProgrammingScreen()`.
- Consumes: `SkillsViewModel`, `SettingsViewModel`, `CodeViewModel` from `LocalVms`.

- [ ] **Step 1: Write the failing section test**

```kotlin
@Test fun `programming page lists only supported sections in order`() {
    assertEquals(
        listOf(ProgrammingSection.CAVEMAN, ProgrammingSection.PERMISSIONS, ProgrammingSection.WORKSPACE,
            ProgrammingSection.MODE, ProgrammingSection.MODEL, ProgrammingSection.LINKS),
        programmingSections(),
    )
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `sh gradlew testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process --tests '*ProgrammingSectionsTest'`
Expected: FAIL because the enum and function do not exist.

- [ ] **Step 3: Build the page layout**

Create `ProgrammingScreen.kt` with a scrolling `Body` and the shared components:

1. Header card with a short description.
2. `CavemanCard`: status, install/uninstall `Btn`, intensity `Seg` (Lite/Full/Ultra), five toggle rows from `vms.skills.skills` filtered by `Caveman.skillIds`, and a «Источник · MIT» text that opens `https://github.com/JuliusBrussee/caveman` only on click.
3. `PermissionsCard`: reuse the grant rows and `sv.revokeMcp(grant)`; empty state «Нет сохранённых разрешений».
4. `WorkspaceCard`: `vms.code.workspaces`, `vms.code.workspace`, «Управлять» opens `SheetKind.Workspace`; keep GitHub/SAF/delete/terminal inside that sheet.
5. `ModeCard`: `CodeMode.entries` radios with the existing descriptions.
6. `ModelCard`: model, effort, web search.
7. `LinksCard`: instructions, skills, permissions, folders.
8. Footer note: Code reads files and prepares changes but does not run commands, builds or tests.

In `SettingsScreens.kt`, replace the whole `"coding"` branch with `{ ProgrammingScreen() }`. Do not delete the `perm`, `search`, `instr`, `dirs` or `skills` routes.

- [ ] **Step 4: Run the tests**

Run: `sh gradlew testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process --tests '*ProgrammingSectionsTest' --tests '*CavemanRepositoryTest'`
Expected: PASS.

- [ ] **Step 5: Build and check the page on device**

Run: `sh gradlew assembleDebug --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process`
Install the APK and verify: install/uninstall Caveman, intensity changes, five switches, permissions, workspace sheet, mode/model/search, links, footer, phone and tablet, light and dark, and that Android's «Убрать анимации» still removes motion.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/clodex/harness/ui/screens/ProgrammingScreen.kt app/src/main/java/app/clodex/harness/ui/screens/SettingsScreens.kt app/src/main/java/app/clodex/harness/data/ProgrammingSections.kt app/src/test/java/app/clodex/harness/ProgrammingSectionsTest.kt
git commit -m "feat: страница «Программирование» с Caveman и рабочей областью"
```

---

### Task 11: Regression, docs and build

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `docs/third-party.md`
- Modify: `docs/releases.md`
- Modify: `app/build.gradle.kts` (`versionCode = 3`)
- Modify: `.superpowers/sdd/2026-11-20-androidharness-feature-port/progress.md` (local record, not committed)

**Interfaces:**
- Produces: versionCode 3, release APK rebuilt later with `-PclodexUpdatesRepo=BlackStormLegenda629/clodex-harness`.

- [ ] **Step 1: Update documentation**

Add the Material You redesign, Programming page and Caveman to `CHANGELOG.md`. In `docs/third-party.md`, add Caveman attribution: upstream MIT, source link, adapted prompt and five skills; note that brand artwork remains removed. In `docs/releases.md`, keep the signing procedure and mention `versionCode 3`.

- [ ] **Step 2: Bump the version**

Set `versionCode = 3`; keep `versionName = "1.0"` unless the user asks otherwise.

- [ ] **Step 3: Full offline verification**

Run:

```sh
sh gradlew assembleDebug testDebugUnitTest --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process
```

Expected: BUILD SUCCESSFUL, all tests pass, including the 175 pre-existing tests plus the new theme, accent, programming and Caveman tests.

- [ ] **Step 4: Export a debug APK for device checks**

Copy `app/build/outputs/apk/debug/app-debug.apk` to `/storage/emulated/0/Download/clodex-harness-material-you-debug.apk`, verify the signer matches `9311dc9a4d485b4909b47536a758e1c6e352e354ca901063c5e0955abc406e6e`, and record the SHA-256. Do not publish it.

- [ ] **Step 5: Commit**

```bash
git add CHANGELOG.md docs/third-party.md docs/releases.md app/build.gradle.kts
git commit -m "docs: Material You, Caveman и версия 3"
```

- [ ] **Step 6: Stop before publication**

Do not create a GitHub Release. Report the manual check results (phone/tablet, light/dark, dynamic color, Caveman, workspace, update banner) and wait for the user's approval to rebuild and sign the release with the existing key.
