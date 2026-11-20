# OAuth-коннекторы, карточка коннектора, PNG-логотипы, анимация рассуждений, фоновая работа: план реализации

> **Для исполнителя:** выполнение нативное, без subagent'ов. Каждая задача заканчивается
> сборкой и unit-тестами. Шаги отмечаются чекбоксами (`- [ ]`).

**Цель.**
- Коннекторы из каталога подключаются кнопкой: Custom Tab, OAuth и возврат через loopback.
- Карточка коннектора в ответе чата.
- Логотипы для всех моделей и провайдеров.
- Живой «Ход рассуждений».
- Ответ модели продолжается, когда приложение закрыто.

**Архитектура.**
- `McpOAuth` (Ktor) делает discovery, DCR, PKCE, обмен кода и refresh.
- `LoopbackReceiver` (ServerSocket 127.0.0.1) принимает код.
- `KtorMcpGateway` берёт bearer из OAuth-состояния через `McpRepository` и обновляет его при 401.
- Чат и Code живут в области приложения, а не Activity. `GenerationService` (foreground,
  dataSync) держит процесс живым, пока идёт хотя бы один ответ.

**Стек:** Kotlin 2.0.21, Compose, Ktor 2.3.12 (+mock), androidx.browser 1.10.0 (есть в кэше gradle).

**Спецификация:** `docs/superpowers/specs/2026-11-20-oauth-connectors-logos-thinking-design.md`.
К ней добавляются пункты из сообщения пользователя: экран как в Claude по скриншотам,
Google-коннекторы, иконки моделей у всех провайдеров, фоновая работа.

## Глобальные ограничения
- Без демо-данных и выдуманных результатов. Коннектор включается только после реального
  `initialize` + `tools/list`.
- В «Ходе рассуждений» только сводки провайдера и факты агента.
- Анимации учитывают `LocalMotion.enabled`.
- Секреты не попадают в логи, toString и сообщения об ошибках.
- Вход в Google, Slack, Asana, HubSpot требует своего OAuth-клиента пользователя: у них нет DCR.
- Figma недоступна: регистрация отвечает 403, причина показывается в UI.

## На что смотреть при проверке
1. Пользователь закрыл вкладку браузера и не вошёл: через 5 минут или по «Отмена» сокет
   закрывается, кнопка снова «Подключить».
2. Пришёл чужой `state` или `error=access_denied`: подключение не происходит, в toast видна причина.
3. Истёк access token, а refresh token отозван: статус WARN «Нужно войти снова», без цикла 401.
4. Приложение свайпнуто из недавних во время ответа: ответ дописывается в БД, уведомление гаснет.
5. Свой провайдер с моделью `google/gemini-…` или `qwen/…`: иконка по семейству модели, а не буква.

---

### Задача 1: транспорт MCP: 404-реинициализация, версия протокола, OAuth-bearer
**Файлы:** `data/mcp/McpClient.kt`, `data/mcp/McpModels.kt`; тест `McpClientTest`.
- В `initialize` отправляется `protocolVersion = "2025-06-18"`. Согласованная версия из ответа
  уходит в заголовке `MCP-Protocol-Version` во всех следующих запросах.
- Ответ 404 при отправленном `Mcp-Session-Id`: сессия сбрасывается, выполняется probe и один повтор.
- Ответ 401 бросает `McpUnauthorizedException(wwwAuthenticate: String?)`.
- `McpAuthMode.OAUTH`: заголовок `Authorization: Bearer <oauth.accessToken>`.
- Тесты:
  - `call_reinitializes_after_404_session`;
  - `sends_protocol_version_header_after_init`;
  - `oauth_mode_sends_access_token`;
  - `unauthorized_maps_to_exception`.

### Задача 2: McpOAuth и LoopbackReceiver
**Файлы:** создать `data/mcp/McpOAuth.kt` и `data/mcp/LoopbackReceiver.kt`; тесты `McpOAuthTest`,
`LoopbackReceiverTest`.

`McpOAuthState(clientId, clientSecret?, tokenEndpoint, resource, accessToken, refreshToken?, expiresAt)`
хранится в `McpConnectionConfig.oauth` и кодируется в `McpConfigCodec`.

Интерфейс `McpOAuth`:
- `suspend fun discover(endpoint): OAuthServer`
  - discovery идёт в таком порядке: `resource_metadata` из 401 → PRM path/root → метаданные AS
    с корня сервера;
  - для AS по порядку пробуются `oauth-authorization-server{path}`, `openid-configuration{path}`
    и `{issuer}/.well-known/openid-configuration`.
- `suspend fun register(server, redirectUri, override: OAuthClient?): OAuthClient`
  - если задан `override`, DCR пропускается;
  - если `registration_endpoint` нет и `override` не задан, бросает `OAuthClientRequired(asHost)`.
- `fun authorizeUrl(server, client, redirectUri, pkce, state): String`
  - параметры: `response_type=code`, `client_id`, `redirect_uri`, `code_challenge`,
    `code_challenge_method=S256`, `state`, `resource`, `scope` (из PRM `scopes_supported`,
    иначе из `www-authenticate scope`).
- `suspend fun exchange(server, client, code, verifier, redirectUri): McpOAuthState`
- `suspend fun refresh(state): McpOAuthState`

`LoopbackReceiver.open(): LoopbackReceiver` (порт 0) с полями `port`, `redirectUri` и методом
`suspend fun await(state, timeoutMs = 300_000): String`:
- возвращает `code`;
- бросает исключение при `error` или чужом `state`;
- отвечает HTML-страницей «Готово, можно вернуться в приложение» со ссылкой `clodex://oauth/done`;
- `close()` закрывает сокет.

Тесты:
- discovery: header, PRM, fallback;
- `register`: без секрета, с секретом, `client_required`;
- `authorizeUrl` содержит все параметры;
- `exchange` и `refresh` разбирают `expires_in`;
- loopback: правильный state, чужой state, error.

### Задача 3: каталог с адресами, подключение в один тап, экран как в Claude
**Файлы:**
- `data/ConnectorCatalog.kt`: поля `mcpUrl` и `auth`;
- `data/mcp/McpRepository.kt`: `ensureFresh` и retry при 401;
- `ui/vm/ConnectorsViewModel.kt`: `oauthConnect` и `saveClient`;
- `ui/screens/Sheets.kt`: новая деталь коннектора;
- `MainActivity`: intent-filter `clodex://oauth`;
- `build.gradle`: `androidx.browser`.

`CatalogAuth`:
- `Oauth`;
- `OauthOwnClient(console: String)` для Google, Slack, Asana и HubSpot;
- `Token(hint)` для GitHub;
- `Manual`;
- `Unavailable(reason)` для Figma.

Адреса берутся из spec, плюс Google:
- `gmailmcp.googleapis.com/mcp/v1`;
- `drivemcp.googleapis.com/mcp/v1`;
- `calendarmcp.googleapis.com/mcp/v1`;
- `docsmcp.googleapis.com/mcp/v1`;
- `sheetsmcp.googleapis.com/mcp/v1`;
- Slack `mcp.slack.com/mcp`;
- Asana `mcp.asana.com/v2/mcp`;
- HubSpot `mcp.hubspot.com/`.

Экран детали:
- шапка: логотип, имя, справа pill «Подключить» или «Отключить»;
- под шапкой подзаголовок, ниже описание;
- «Инструменты N»: чипы, первые 8 и «+K ещё»;
- «Сведения»: Автор (ссылка на сайт), URL коннектора с кнопкой копирования.

Во время входа кнопка показывает «Ждём вход…», рядом «Отмена». После успеха toast
«Подключено: X».

Тест `ConnectorsViewModelTest`: `oauthConnect` с фейковым flow ставит ON и показывает toast
«Подключено: Notion». При `OAuthClientRequired` открывается форма client id.

### Задача 4: карточка коннектора в чате
**Файлы:** создать `data/ConnectorSuggester.kt`; изменить `ui/screens/ChatScreens.kt`;
тест `ConnectorSuggesterTest`.
- `ConnectorSuggester.find(text: String, statuses: Map<String,String>): List<ConnCatalogEntry>`
  возвращает не больше двух записей и только со статусом не ON.
- Ключи: имя, домен и русские алиасы. Пример: «сделай в фигме макет» → `[fg]`.
- Карточка показывается под ответом на это сообщение и под живым черновиком.

### Задача 5: PNG-логотипы
**Файлы:**
- `res/drawable-nodpi/logo_*.png` (lobe-icons, 128 px);
- `ui/components/ProviderMark.kt`: `logoFor(providerId, name, baseUrl)` и `modelLogo(apiId, providerId)`;
- `ModelRow` в `Sheets.kt`;
- тест `ProviderPresentationTest`.

Семейства моделей:
- claude → anthropic;
- gpt/o1/o3/o4/chatgpt → openai;
- deepseek;
- gemini/gemma → gemini;
- mistral/mixtral/codestral/magistral → mistral;
- grok → grok;
- llama → meta;
- qwen/qwq → qwen;
- kimi/moonshot → kimi;
- glm → zhipu;
- minimax;
- остальное → логотип провайдера, иначе буква.

### Задача 6: живой «Ход рассуждений»
**Файл:** `ui/components/Thinking.kt`.
- Shimmer-градиент по тексту.
- Тикающий счётчик секунд от `startedAt`.
- AnimatedContent последнего шага.
- Три точки волной.
- Анимированный поворот шеврона.
- Тест `ThinkingPresentationTest`: `liveLabel(startedAt, now)` → «Ход рассуждений · 4 с».

### Задача 7: работа в фоне
**Файлы:**
- создать `GenerationService.kt`;
- `AppContainer`: `activeWork: StateFlow<Int>` и app-scoped `ChatViewModel`/`CodeViewModel`
  через `ViewModelStore` контейнера;
- `HarnessApp`: VMs из контейнера;
- манифест: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, service.

Сервис стартует, когда `generatingChatIds` или `CodeViewModel.running` не пусты. Уведомление
«Clodex отвечает…». Сервис останавливается, когда работ ноль. Запрос `POST_NOTIFICATIONS`
делается при первом ответе.

### Задача 8: README, сборка, APK
`sh gradlew assembleDebug testDebugUnitTest` должен дать BUILD SUCCESSFUL. Обновить README
(разделы «Коннекторы» и «Отложено») и скопировать APK.
