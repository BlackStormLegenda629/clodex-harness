# Устранение дыр и новые возможности — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Закрыть найденные дыры (сквозной поиск, инструменты Code, нерабочие уведомления и папки, мёртвые настройки, нет экспорта) и добавить оформление (haptic, иконка, TTS), затем выпустить versionCode 5 как `BlackStormLegenda629/clodex-harness` с тегом `v1.1` и тем же ключом подписи.

**Architecture:** Чистая доменная логика (поиск, уведомления, экспорт) живёт в `data/` и покрывается JVM-тестами; Compose и Android API — тонкая обвязка. Сквозной поиск — один экран с вкладками и `LIKE`-запросами к Room + обходом файлов по запросу. Инструменты `grep`/`find` добавляются в `WorkspaceOverlay`, чтобы видеть staged-правки.

**Tech Stack:** Kotlin, Jetpack Compose/M3, Room, DataStore, OkHttp, kotlinx.serialization, WorkManager, Android Notifications, android.text-to-speech.

**Spec:** `docs/superpowers/specs/2026-11-21-hole-plugging-and-new-features-design.md`

## Global Constraints

- minSdk 26, compileSdk/targetSdk 35; добавлять только существующие зависимости.
- `BlackStormLegenda629/clodex-harness` — канал обновлений по умолчанию, `5` — следующий versionCode, тег `v1.1`.
- Assets релиза называть ровно `clodex-<versionCode>.apk`; подпись — `~/clodex-release.jks`, alias `clodex`.
- Все новые user-facing строки — русские; сохранить совместимость с существующими тестами.
- Каждая задача: тест → красный → код → зелёный → коммит. Проверка: `sh gradlew assembleDebug testDebugUnitTest lintDebug --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process`.
- Для команд git: `GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0="$PWD"`.

## Review Focus

- Поиск с пустым/коротким запросом не должен сканировать всё и не должен падать на битых/огромных полях — задача 3 тестирует `SearchQuery` и лимиты.
- Поиск по файлам не должен читать бинарные/слишком большие файлы и должен уваживать staged-правки — задача 4 тестирует `WorkspaceSearch`.
- `grep` по невалидному regex не должен ронять агента — задача 5 тестирует `compileRegex`.
- Уведомления не должны.show/крашиться без разрешения и не должны дублироваться при的几个 событиях — задача 6 тестирует `notificationDecision`.
- Экспорт/импорт не должен терять данные или принимать чужой/битый JSON и не должен «успешно» импортировать пустой пакет — задача 7 тестирует round-trip и откат.
- Добавленная в настройках SAF-папка должна стать видимой как `WorkspaceEntity` с корректным treeUri — задача 8 тестирует `workDirToWorkspace`.

---

### Task 1: Чистые модели и репозитории для поиска

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/data/HarnessDb.kt` (добавить `@Query`-методы поиска в `MessageDao`, `ChatDao`, `ArtifactDao`, `SessionDao`)
- Test: `app/src/test/java/app/clodex/harness/updates/NoOpTest.kt` (заглушка не нужна — тесты этой задачи идут в задаче 3; задача 1 только готовит API)

**Interfaces:**
- Consumes: существующие `@Entity` из `Entities.kt`.
- Produces:
  - `ChatDao.searchByTitle(q: String): List<ChatEntity>` — `LIKE '%' || :q || '%' COLLATE NOCASE`, limit 50.
  - `MessageDao.search(q: String): List<MessageEntity>` — по `text`, limit 200, сортировка по `chatId, id`.
  - `ArtifactDao.search(q: String): List<ArtifactEntity>` — по `name` и `content`, limit 100.
  - `SessionDao.search(q: String): List<SessionEntity>` — по `title`, `summary`, `diff`, limit 100.
  - `SessionDao.searchChanges(q: String): List<SessionChangeEntity>` — по `path`, `afterText`, limit 200.

- [ ] **Step 1: Добавить `@Query`-методы** в перечисленные DAO ровно с указанными сигнатурами и лимитами.
- [ ] **Step 2: Собрать**

Run: `sh gradlew compileDebugKotlin --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process`
Expected: BUILD SUCCESSFUL (Room проверит SQL при KSP).

- [ ] **Step 3: Commit**

`git commit -m "feat: DAO-запросы для сквозного поиска"`

---

### Task 2: Домен SearchQuery и слайсы результатов

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/search/SearchModels.kt`
- Create: `app/src/main/java/app/clodex/harness/data/search/SearchQuery.kt`
- Test: `app/src/test/java/app/clodex/harness/search/SearchQueryTest.kt`

**Interfaces:**
- Consumes: entity-классы из `data/Entities.kt`.
- Produces:
  - `enum class SearchSlice { MESSAGES, CHATS, SESSIONS, CHANGES, ARTIFACTS, FILES }`
  - `data class SearchHit(val slice: SearchSlice, val title: String, val snippet: String, val route: String, val score: Int = 0)`
  - `object SearchQuery { fun normalize(raw: String): String; fun isSearchable(raw: String): Boolean; fun snippet(text: String, q: String, radius: Int = 60): String }`

`normalize`: trim + collapse whitespace + lowercase. `isSearchable`: normalize().length >= 2. `snippet`: вернуть подстроку вокруг первого совпадения без учёта регистра, ограниченную radius, с `…` на краях; для пустого q — первые `radius*2` символов.

**Review-Focus tests (из Review Focus):**
- `shortAndBlankQueriesAreNotSearchable` — `""`, `" "`, `"а"` → false; `"ab"` → true.
- `snippetIsBoundedAndMarksTruncation` — строка из 500 символов с совпадением в середине → длина.snippet <= 2*radius+2, есть `…`.

- [ ] **Step 1: Написать_fail тесты** `SearchQueryTest.kt` с двумя тестами выше.
- [ ] **Step 2: Запустить, убедиться, что fail** (компилятор: unresolved reference).
- [ ] **Step 3: Реализовать `SearchModels.kt` и `SearchQuery.kt`.**
- [ ] **Step 4: Запустить тесты, убедиться, что pass.**
- [ ] **Step 5: Commit.**

`git commit -m "feat: домен сквозного поиска"`

---

### Task 3: SearchRepository, объединяющий ROI-результаты

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/search/SearchRepository.kt`
- Test: `app/src/test/java/app/clodex/harness/search/SearchRepositoryTest.kt`

**Interfaces:**
- Consumes: DAO-методы задачи 1, `SearchQuery`/`SearchHit` задачи 2, `WorkspaceSearch` задачи 4.
- Produces: `class SearchRepository(private val db: HarnessDb, private val files: WorkspaceSearch) { suspend fun search(raw: String): List<SearchHit> }`
  - Возвращает пустой список, если `!SearchQuery.isSearchable(raw)`.
  - Объединяет все слайсы, сортирует по `score desc`, затем `title`, и обрезает до 100 результатов.
  - `route` для сообщения: `"chat/${chatId}"`, для чата: `"chat/${id}"`, для сессии: `"session/${id}"`, дляchanges: `"diff/${sessionId}"`, для артефакта: `"artifact/${id}"`, для файла: `"browse?uri=..."` (кодировать Uri).

**Review-Focus tests:**
- `shortQuerySkipsAllSources` — `repo.search("а")` не вызывает ни один источник (использовать фейковые DAO/fake workspace).
- `resultsAreCappedAndSorted` — 300fake-хитов → 100, отсортированы.

- [ ] **Step 1: Написать_fail тесты с фейковыми DAO.**
- [ ] **Step 2: Запустить, убедиться, что fail.**
- [ ] **Step 3: Реализовать `SearchRepository`.**
- [ ] **Step 4: Pass.**
- [ ] **Step 5: Commit.**

`git commit -m "feat: поиск по всем источникам"`

---

### Task 4: Поиск по файлам рабочей области

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/code/WorkspaceSearch.kt`
- Test: `app/src/test/java/app/clodex/harness/code/WorkspaceSearchTest.kt`

**Interfaces:**
- Consumes: `WorkspaceGateway`, `WorkspaceOverlay`, `WorkspacePath` (из `data/code/`).
- Produces: `class WorkspaceSearch(private val gateway: WorkspaceGateway) { suspend fun search(query: String, roots: List<WorkspacePath>, maxFiles: Int = 400, maxBytes: Long = 256*1024): List<SearchHit> }`
  - Обходит `gateway.list` по корням (BFS, не рекурсивный стек), пропускает каталоги.
  - Пропускает файлы без `entry.text`, больше `maxBytes` и после `maxFiles`.
  - Читает через `overlay.read`, чтобы учитывать staged-правки: `WorkspaceSearch(gateway, overlay)`. Перегрузка конструктора с одним gateway использует прямой `gateway.read`.
  - Возвращает `SearchHit(slice=FILES, title=path.value, snippet=SearchQuery.snippet(text, query), route="browse?uri=...")`.

**Review-Focus tests:**
- `binaryAndOversizedFilesAreSkipped` — entry.text=false и size>maxBytes не читаются.
- `stagedEditsAreVisible` — файл в overlay с новым текстом, содержащим запрос, попадает в результаты.

- [ ] **Step 1: FAIL-тесты.**
- [ ] **Step 2: RED.**
- [ ] **Step 3: Реализация.**
- [ ] **Step 4: GREEN.**
- [ ] **Step 5: Commit.**

`git commit -m "feat: поиск по файлам рабочей области"`

---

### Task 5: Инструменты Code `grep` и `find`

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/data/code/CodeAgentTools.kt`
- Test: `app/src/test/java/app/clodex/harness/code/CodeSearchToolsTest.kt`

**Interfaces:**
- Consumes: `WorkspaceOverlay`, `AgentTool`, `AgentToolSpec`, `AgentToolResult`, `CodeToolNames`, `codeEventTitle`.
- Produces:
  - `CodeToolNames.GREP = "workspace_grep"`, `CodeToolNames.FIND = "workspace_find"`.
  - `internal object SearchArg { fun compileRegex(pattern: String, ignoreCase: Boolean = true): Result<Regex> }` — ловит `RegexSyntaxException`, лимит длины паттерна 200.
  - `codeTools(...)` возвращает `ListTool, ReadTool, GrepTool, FindTool, WriteTool`.
  - `codeEventTitle` знает новые имена: `"Нашёл … в …"` / `"Искал …"`.

**Review-Focus tests:**
- `invalidRegexReturnsErrorNotThrow` — `compileRegex("[")` → `Result.failure`, `GrepTool.invoke` возвращает `AgentToolResult.error`, не бросает.
- `grepRespectsLimits` — 100 файлов, только 5 содержат совпадение, результат урезан до `maxResults=30` и содержит path+номер строки.

- [ ] **Step 1: FAIL-тесты.**
- [ ] **Step 2: RED.**
- [ ] **Step 3: Реализация `GrepTool`/`FindTool` с обходом overlay и лимитами (аналог `WorkspaceSearch`, но результат — строки `path:line: text`).**
- [ ] **Step 4: GREEN.**
- [ ] **Step 5: Commit.**

`git commit -m "feat: инструменты поиска для Code-агента"`

---

### Task 6: Рабочие уведомления

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/notifications/NotificationDecision.kt`
- Create: `app/src/main/java/app/clodex/harness/util/Notifier.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`POST_NOTIFICATIONS`)
- Modify: `app/src/main/java/app/clodex/harness/ui/vm/ChatViewModel.kt` и `CodeViewModel.kt` (emit событий)
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/SettingsScreens.kt` (запрос разрешения на Android 13+)
- Test: `app/src/test/java/app/clodex/harness/notifications/NotificationDecisionTest.kt`

**Interfaces:**
- Consumes: флаги `n1/n2/n3` из `AppSettings.on(...)`, события из VM.
- Produces:
  - `enum class NotifyEvent { APPROVAL_NEEDED, SESSION_DONE, REPLY_READY }`
  - `object NotificationDecision { fun shouldNotify(flags: Map<String,Boolean>, event: NotifyEvent): Boolean }` — `APPROVAL_NEEDED→n1`, `SESSION_DONE→n2`, `REPLY_READY→n3`.
  - `class Notifier(context: Context) { fun show(event: NotifyEvent, title: String, text: String) }` — канал `clodex_events`, IMPORTANCE_DEFAULT, уникальный id по событию, `PendingIntent` в `MainActivity`.
  - VM: `val events: SharedFlow<NotifyEvent>`; `ChatViewModel` испускает `REPLY_READY` по завершении длинного ответа (`>=10 сек`), `CodeViewModel` — `APPROVAL_NEEDED` при `perm=PENDING` и `SESSION_DONE` при завершении сессии.

**Review-Focus tests:**
- `flagsControlEvents` — каждая комбинация флагов даёт ожидаемое решение.
- `permissionNotAvailableIsNoOp` — `Notifier.show` без POST_NOTIFICATIONS не бросает (тест на чистой функции решения + проверка, что вызов обёрнут в `if (ContextCompat.checkSelfPermission...)`).

- [ ] **Step 1: FAIL-тест `NotificationDecision`.**
- [ ] **Step 2: RED.**
- [ ] **Step 3: Реализация decision + Notifier + манифест + VM-emit + UI-запрос разрешения.**
- [ ] **Step 4: GREEN + compile.**
- [ ] **Step 5: Commit.**

`git commit -m "feat: рабочие уведомления по флагам n1/n2/n3"`

---

### Task 7: Удалить мёртвые настройки и починить рабочие папки

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/data/Settings.kt` (убрать `ask`, `edit`, `agentWeb`, `p1` из `TOGGLE_DEFAULTS` и UI-записи)
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/SettingsScreens.kt` (убрать переключатель «Сохранять историю чатов»)
- Modify: `app/src/main/java/app/clodex/harness/ui/vm/SettingsViewModel.kt` (метод `addWorkDir` сохраняет SAF treeUri)
- Test: `app/src/test/java/app/clodex/harness/settings/WorkDirMappingTest.kt`

**Interfaces:**
- Consumes: `WorkDir`, `WorkspaceEntity`, `Saf` helpers.
- Produces: `internal object WorkDirMapping { fun toWorkspace(dir: WorkDir): WorkspaceEntity }` — для `kind=="saf"` в `path` кладётся исходный `treeUri`, для `"git"` — путь как есть; имя = последний сегмент path. FAIL, если `treeUri` пустой.

**Review-Focus tests:**
- `safTreeUriIsPreserved` — `WorkDir("content://.../tree/abc", "saf")` → WorkspaceEntity(path=тот же URI).
- `deadTogglesAreGone` — `TOGGLE_DEFAULTS.keys` не содержит `ask/edit/agentWeb/p1`.

- [ ] **Step 1: FAIL-тесты.**
- [ ] **Step 2: RED.**
- [ ] **Step 3: Удалить мёртвые флаги/UI, реализовать mapping и использовать его в `addWorkDir` + при создании сессии в `CodeViewModel`.**
- [ ] **Step 4: GREEN.**
- [ ] **Step 5: Commit.**

`git commit -m "fix: мёртвые настройки и SAF-папки в Code"`

---

### Task 8: Экспорт и импорт данных

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/backup/BackupPayload.kt`
- Create: `app/src/main/java/app/clodex/harness/data/backup/BackupCodec.kt`
- Create: `app/src/main/java/app/clodex/harness/data/backup/BackupRepository.kt`
- Modify: `app/src/main/java/app/clodex/harness/AppContainer.kt` (создать репозиторий)
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/SettingsScreens.kt` (кнопки «Экспорт» / «Импорт» в `priv`)
- Test: `app/src/test/java/app/clodex/harness/backup/BackupCodecTest.kt`

**Interfaces:**
- Consumes: все entity из `Entities.kt`, `SettingsRepository`, `ToastBus`, SAF `OpenDocumentTree`/`CreateDocument`.
- Produces:
  - `@Serializable data class BackupPayload(val schema: Int = 1, val exportedAt: Long, val chats: List<ChatEntity>, val messages: List<MessageEntity>, val projects: List<ProjectEntity>, val artifacts: List<ArtifactEntity>, val sessions: List<SessionEntity>, val sessionMessages: List<SessionMessageEntity>, val sessionEvents: List<SessionEventEntity>, val sessionChanges: List<SessionChangeEntity>, val workspaces: List<WorkspaceEntity>, val skills: List<SkillEntity>, val connectors: List<ConnectorEntity>, val memory: List<MemoryNote>, val scheduled: List<ScheduledMessage>, val settings: Map<String, String>)`
  - `object BackupCodec { fun encode(payload: BackupPayload): String; fun decode(json: String): Result<BackupPayload> }` — проверяет `schema==1`, непустую дату, что все id уникальны; при ошибке `Result.failure` без частичного импорта.
  - `class BackupRepository(db, settings, files) { suspend fun export(): Result<String>; suspend fun import(json: String): Result<Unit> }` — импорт в одной транзакции Room через `@Transaction` метод в `HarnessDb`.

**Review-Focus tests:**
- `roundTripPreservesEverything` — payload со всеми списками → encode → decode → equals.
- `wrongSchemaAndDuplicatesRejected` — `schema=2`, дубликат chat id → failure.

- [ ] **Step 1: FAIL-тесты.**
- [ ] **Step 2: RED.**
- [ ] **Step 3: Реализация codec + repository + UI.**
- [ ] **Step 4: GREEN.**
- [ ] **Step 5: Commit.**

`git commit -m "feat: экспорт и импорт данных"`

---

### Task 9: Оформление — haptic, иконка, TTS

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/components/Basics.kt` (haptic в `Btn`/`IconBtn`)
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (monochrome)
- Modify: `app/src/main/res/drawable/ic_launcher_monochrome.xml` (создать)
- Modify: `app/src/main/java/app/clodex/harness/util/Io.kt` (функция `speak`)
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/ChatScreens.kt` (кнопка озвучки в сообщении)
- Test: `app/src/test/java/app/clodex/harness/ui/HapticPolicyTest.kt`

**Interfaces:**
- Consumes: `HapticFeedbackType`, `TextToSpeech`, существующий `rememberSpeech`.
- Produces: `object HapticPolicy { fun typeFor(action: String): HapticFeedbackType }` — `"send"→LongPress`, `"confirm"→LongPress`, `"toggle"→TextHandleMove`, `"error"→LongPress`; `fun speak(text: String, onDone: () -> Unit)` — обёртка над TTS, `text.take(4000)`, не вызывает onDone дважды.

**Review-Focus tests:**
- `hapticActionMapping` — все 4 действия дают ожидаемый тип.
- `ttsTextIsCapped` — `speak("x".repeat(10000))` передаёт в TTS ровно 4000 символов.

- [ ] **Step 1: FAIL-тесты (чистая политика + fade).**
- [ ] **Step 2: RED.**
- [ ] **Step 3: Реализация + UI.**
- [ ] **Step 4: GREEN.**
- [ ] **Step 5: Commit.**

`git commit -m "feat: haptic, монохромная иконка и озвучка"`

---

### Task 10: Экран сквозного поиска и навигация

**Files:**
- Create: `app/src/main/java/app/clodex/harness/ui/screens/SearchScreen.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/nav/Nav.kt` (`Routes.SEARCH`, `fun search()`)
- Modify: `app/src/main/java/app/clodex/harness/ui/nav/HarnessApp.kt` (composable)
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/MainScreen.kt` (иконка в Header)
- Test: `app/src/test/java/app/clodex/harness/search/SearchScreenRoutingTest.kt`

**Interfaces:**
- Consumes: `SearchRepository`, `SearchSlice`, `SearchHit`, `Nav`.
- Produces: экран с M3 `SearchBar`, вкладками `SearchSlice` и списком `SearchHit`; тап по hit вызывает `nav.push(hit.route)`; `SearchScreenRoutingTest` проверяет, что каждая строка-routes распознаётся чистым `RouteParser`.

**Review-Focus tests:**
- `allHitRoutesAreParsable` — каждая строка-routes из таксономии распознаётся парсером.

- [ ] **Step 1: FAIL-тест.**
- [ ] **Step 2: RED.**
- [ ] **Step 3: Реализация экрана и навигации.**
- [ ] **Step 4: GREEN + compile.**
- [ ] **Step 5: Commit.**

`git commit -m "feat: экран сквозного поиска"`

---

### Task 11: Документация и релиз v1.1

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `README.md`
- Modify: `app/build.gradle.kts` (`versionCode = 5`)
- Modify: `docs/releases.md`
- Test: полный прогон: `assembleDebug testDebugUnitTest lintDebug`.

**Interfaces:**
- Consumes: всё выше.
- Produces: Release v1.1 в `BlackStormLegenda629/clodex-harness` с ассетом `clodex-5.apk`, подписанным тем же ключом; `git push` в main; тег `v1.1`.

- [ ] **Step 1: Обновить docs и versionCode.**
- [ ] **Step 2: Прогнать `assembleDebug testDebugUnitTest lintDebug`, 0 ошибок.**
- [ ] **Step 3: Собрать `assembleRelease --offline ... -x lintVital*`, подписать, проверить `apksigner verify --print-certs`: сертификат `06:54:4D:6B:D7:…:0B:A2`, versionCode 5.**
- [ ] **Step 4: `git push`, `git tag -f v1.1`, `gh release create v1.1 clodex-5.apk --latest`.**
- [ ] **Step 5: Проверить `/releases/latest` возвращает `v1.1` и `clodex-5.apk`.**
- [ ] **Step 6: Commit docs, push.**

`git commit -m "release: v1.1"`

---

## Self-Review Checklist

- [x] Spec coverage: поиск (T1–4, T10), инструменты (T5), уведомления (T6), мёртвые настройки/папки (T7), экспорт (T8), оформление (T9), релиз (T11).
- [x] Review Focus: все 6 рисков закреплены тестами в своих задачах.
- [x] Type consistency: `SearchHit`, `SearchQuery`, `WorkspaceSearch`, `NotifyEvent`, `BackupPayload` и их сигнатуры определены один раз.
- [x] Proportion: план фиксирует интерфейсы и тесты, не переписывает код.
