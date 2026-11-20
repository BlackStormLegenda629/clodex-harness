# Настоящие коннекторы (OAuth), карточка коннектора, PNG-логотипы, живой «Ход рассуждений»

Дата: 2026-11-20. Выбран вариант А: настоящий вход через браузер.

## Что проверено руками (curl с устройства, 2026-11-20)

Все серверы ниже отвечают `401` + `WWW-Authenticate: Bearer resource_metadata=…`.
Динамическая регистрация клиента (DCR, `token_endpoint_auth_method: none`) с
loopback-адресом `http://127.0.0.1:<порт>/callback` прошла у:

| Коннектор | Endpoint |
|---|---|
| Notion | `https://mcp.notion.com/mcp` |
| Linear | `https://mcp.linear.app/mcp` |
| Sentry | `https://mcp.sentry.dev/mcp` |
| Canva | `https://mcp.canva.com/mcp` |
| Stripe | `https://mcp.stripe.com` |
| Vercel | `https://mcp.vercel.com` |
| Miro | `https://mcp.miro.com/` |
| Intercom | `https://mcp.intercom.com/mcp` |
| PayPal | `https://mcp.paypal.com/mcp` |
| Todoist | `https://ai.todoist.net/mcp` |
| ClickUp | `https://mcp.clickup.com/mcp` |
| Jira, Confluence | `https://mcp.atlassian.com/v1/mcp` (PRM нет → метаданные с корня сервера) |

Схему `app.clodex.harness:/…` Stripe, Vercel и Intercom отклоняют, поэтому
возврат из браузера только через loopback.

Не подключаются кнопкой, и карточка честно об этом пишет:
- **Figma**: регистрация `403`, пускают только клиентов из каталога Figma.
- **Slack, Asana, HubSpot**: нет DCR, нужен заранее выданный client_id.
- **Supabase**: DCR есть, но только для клиентов с секретом. Не проверено,
  оставляем ручной ввод.

**GitHub**: DCR нет, но `https://api.githubcopilot.com/mcp/` принимает
Personal Access Token. Кнопка «Подключить» открывает поле для токена с уже
подставленным адресом.

Остальные коннекторы каталога (Gmail, Drive, Telegram…) остаются как сейчас:
адрес и токен вводятся вручную.

## 1. OAuth для MCP

**Каталог.** В `ConnCatalogEntry` добавляются поля `mcpUrl: String?` и
`auth: CatalogAuth`, где `CatalogAuth` — это `OAUTH`, `TOKEN`, `MANUAL` или
`Unavailable(reason)`.

**Модель.** `McpAuthMode.OAUTH`. В `McpConnectionConfig` появляется
`oauth: McpOAuthState?`:
- `clientId`, `clientSecret?`;
- `tokenEndpoint`, `resource`;
- `accessToken`, `refreshToken?`, `expiresAt`.

Хранится в том же DataStore, что и bearer-токены сейчас (`allowBackup=false`).

**Поток (`McpOAuth`, на Ktor):**
1. Discovery. Запрос без токена → `resource_metadata` из `WWW-Authenticate`.
   Если его нет, пробуем `/.well-known/oauth-protected-resource[/path]`, затем
   метаданные авторизации с корня сервера (RFC 8414, затем OIDC).
2. Регистрация `/register` с `redirect_uris=[http://127.0.0.1:<порт>/callback]`.
   Порт берётся у уже открытого сокета. Если сервер вернул `client_secret`,
   дальше используем `client_secret_post`.
3. PKCE S256, случайный `state`, параметр `resource` (RFC 8707), `scope` из
   метаданных.
4. Браузер открывается через `Intent.ACTION_VIEW`.
   `LoopbackReceiver` (`ServerSocket` на 127.0.0.1) ждёт один
   `GET /callback`, сверяет `state` и отвечает HTML-страницей «Готово» с
   кнопкой `clodex://oauth/done`. На эту схему у `MainActivity` стоит
   intent-filter, поэтому браузер возвращает в приложение. Таймаут 5 минут,
   отмена сразу закрывает сокет.
5. Обмен `code` на токены → обычная проверка (`initialize` + `tools/list`) →
   коннектор ON.

**Refresh.** Токен обновляется, если до истечения меньше 60 с или сервер
ответил `401`. Обновление один раз, затем повтор запроса. Если не вышло, статус
«Нужно войти снова», и в карточке кнопка «Войти».

**Исправления транспорта:**
- `404` при известном `Mcp-Session-Id` → сброс сессии, повторный `initialize`,
  один повтор запроса;
- заголовок `MCP-Protocol-Version` с согласованной версией после `initialize`;
- в `initialize` отправляем `2025-06-18`.

**UI.** «Подключить» в деталях коннектора и в карточке:
- `OAUTH`: запускает поток, показывает «Ждём вход в браузере…» и «Отмена»;
- `TOKEN`: поле токена с подставленным адресом;
- `Unavailable`: кнопка выключена, видна причина;
- `MANUAL`: текущая форма.

## 2. Карточка коннектора в ответе

`ConnectorSuggester.find(userText, catalog)` ищет упоминания по ключевым
словам: название, домен и русские варианты («фигм», «ноушн», «гитхаб»,
«джир», «линеар», «конфлюенс», «канв», «слак»…). В ответ попадает не больше
двух коннекторов, и только те, что не включены.

Карточка рисуется под ответом ассистента на это сообщение и в живом черновике:
логотип, название, одна строка описания, кнопка «Подключить». Когда коннектор
подключён, на кнопке «Подключено ✓». Для `Unavailable` в карточке видна
причина.

Карточка вычисляется при отрисовке. Схема БД не меняется.

Отличие от обещанного раньше: инструмента `suggest_connector` для модели не
будет. Ключевые слова детерминированы, тестируются и работают даже с моделями
без поддержки инструментов. Если окажется мало, инструмент можно добавить
отдельно.

## 3. PNG-логотипы моделей

PNG из `@lobehub/icons-static-png` (MIT), 640×640, сжимаются до 192 px в
`res/drawable-nodpi`: Anthropic (Claude), OpenAI, DeepSeek, Gemini, Mistral,
xAI/Grok, OpenRouter, Groq, Ollama, Qwen, Moonshot/Kimi.

Встроенные провайдеры получают свой PNG. Для `custom` логотип подбирается по
имени или хосту baseUrl. Если ничего не подошло, остаётся буквенный знак.

`ProviderMark` показывает PNG без `ColorFilter`, на нейтральной плашке.
Варианты light/dark выбираются по теме.

## 4. Живой «Ход рассуждений»

Только пока идёт работа:
- бегущий блик (shimmer) по тексту «Ход рассуждений»;
- счётчик секунд «· 4 с», обновляется раз в секунду;
- текущий шаг сменяется через AnimatedContent (slide + fade);
- три точки «волной».

Всегда:
- стрелка раскрытия поворачивается анимированно;
- шаги в диалоге появляются по очереди.

При `LocalMotion.enabled == false` всё статично. В тексте по-прежнему только
сводки провайдера и факты агента.

## Тесты

Unit-тесты (Ktor MockEngine):
- discovery по трём путям;
- DCR без секрета и с секретом;
- authorize URL (PKCE, state, resource);
- обмен кода и refresh, повтор после 401, «нужно войти снова»;
- повторная инициализация после 404 сессии, заголовок версии.

`LoopbackReceiver` проверяется настоящим сокетом в JVM: правильный state,
чужой state, таймаут.

Также:
- `ConnectorSuggester`: ru/en, только неподключённые, не больше двух;
- подбор логотипа провайдера.

## Честные ограничения

- Вход через браузер на устройстве не проверен: нет `adb`. Проверены discovery
  и регистрация с реальными серверами, плюс unit-тесты потока.
- Токены хранятся в DataStore без шифрования, как и сейчас bearer-токены.
- Figma, Slack, Asana и HubSpot кнопкой не подключить, пока они не выдадут
  приложению client_id.
