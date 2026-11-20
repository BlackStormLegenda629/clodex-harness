# Real MCP Connectors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace local connector status toggles with verified MCP connections and callable tools.

**Architecture:** A focused MCP module owns JSON-RPC, Streamable HTTP/SSE parsing, session IDs, configuration persistence, discovery, and invocation. Catalog metadata stays static; runtime endpoint/auth/server/tool state is stored separately and exposed through `McpRepository`.

**Tech Stack:** Kotlin, Ktor 2.3.12/OkHttp, kotlinx.serialization JSON, DataStore, Compose.

**Spec:** `docs/superpowers/specs/2026-11-20-real-connectors-code-thinking-design.md`

## Global Constraints

- A connector is connected only after successful `initialize`, `notifications/initialized`, and `tools/list`.
- No invented endpoint or one-tap OAuth claim for any of the 41 branded cards.
- Secrets never appear in logs, error text, or test output.
- Support MCP Streamable HTTP and `text/event-stream` responses; cap responses and timeouts.
- Do not initialize Git in this non-Git workspace.

## Review Focus

- A server returning HTTP 200 with malformed JSON stays disconnected — Task 2.
- Session ID returned only after initialize is sent on later requests — Task 2.
- Duplicate tool names across connectors are namespaced — Task 4.
- Cancellation/timeouts do not become “connected” or leak secrets — Tasks 2 and 3.
- A previously connected endpoint that later fails becomes warning, not silently on — Task 4.

---

### Task 1: MCP protocol models and framing

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/mcp/McpModels.kt`
- Create: `app/src/main/java/app/clodex/harness/data/mcp/McpCodec.kt`
- Test: `app/src/test/java/app/clodex/harness/McpCodecTest.kt`

**Interfaces:**
- Produces: `McpConnectionConfig`, `McpServerInfo`, `McpToolSpec`, `McpProbe`, `McpCallResult`, `McpProtocolException`
- Produces: `McpCodec.request(id: Long, method: String, params: JsonElement?): String`
- Produces: `McpCodec.parsePayload(contentType: String?, body: String, expectedId: Long): JsonObject`

- [ ] **Step 1: Write failing tests** for JSON-RPC request IDs, plain JSON result/error, SSE `event:`/multi-line `data:`, wrong IDs, malformed payload, and 1MB response rejection.
- [ ] **Step 2: Run** `sh gradlew testDebugUnitTest --tests app.clodex.harness.McpCodecTest`; expect unresolved codec.
- [ ] **Step 3: Implement immutable protocol/config types and a codec that never includes auth fields in exception messages.**
- [ ] **Step 4: Run** the focused tests; expect pass.

### Task 2: Ktor MCP transport

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/mcp/McpClient.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/app/clodex/harness/McpClientTest.kt`

**Interfaces:**
- Produces: `interface McpGateway { suspend fun probe(config: McpConnectionConfig): Result<McpProbe>; suspend fun call(config: McpConnectionConfig, tool: String, arguments: JsonObject): Result<McpCallResult> }`
- Produces: `class KtorMcpGateway(http: HttpClient, clock: () -> Long = System::currentTimeMillis) : McpGateway`

- [ ] **Step 1: Add Ktor MockEngine as a test dependency and write failing tests** asserting request headers, `Accept: application/json, text/event-stream`, initialize protocol version/capabilities, `Mcp-Session-Id` reuse, initialized notification, tools/list, tools/call, non-2xx errors, timeout/cancellation, and redaction.
- [ ] **Step 2: Run** the focused tests; expect unresolved gateway.
- [ ] **Step 3: Implement `KtorMcpGateway` with a mutexed per-endpoint session map, monotonically increasing IDs, 20s connect/60s request limits, and codec parsing.**
- [ ] **Step 4: Ensure probe returns server metadata and tool annotations, and call flattens MCP text content while preserving structured JSON.**
- [ ] **Step 5: Run** focused tests; expect pass.

### Task 3: Connector configuration store

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/mcp/McpConfigStore.kt`
- Test: `app/src/test/java/app/clodex/harness/McpConfigCodecTest.kt`

**Interfaces:**
- Produces: `interface McpConfigRepository { val configs: Flow<Map<String, McpConnectionConfig>>; suspend fun get(id: String): McpConnectionConfig?; suspend fun save(config: McpConnectionConfig); suspend fun remove(id: String) }`
- Produces: `class DataStoreMcpConfigRepository(store: DataStore<Preferences>)`

- [ ] **Step 1: Write failing codec tests** for endpoint/auth/headers/tool metadata round trips, unknown-field tolerance, missing config, and a bearer token containing quotes/newlines.
- [ ] **Step 2: Run** focused tests; expect unresolved store/codec.
- [ ] **Step 3: Add a dedicated `connector_settings` DataStore and JSON encoding keyed by connector ID.** Do not overload `ConnectorEntity.description`.
- [ ] **Step 4: Run** tests; expect pass.

### Task 4: Repository and connector state semantics

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/mcp/McpRepository.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/vm/ConnectorsViewModel.kt`
- Modify: `app/src/main/java/app/clodex/harness/AppContainer.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/vm/SettingsViewModel.kt`
- Modify: `app/src/test/java/app/clodex/harness/TestSupport.kt`
- Modify: `app/src/test/java/app/clodex/harness/ViewModelTests.kt`

**Interfaces:**
- Produces: `McpRepository.probeAndSave(connectorId: String, config: McpConnectionConfig): Result<McpProbe>`
- Produces: `McpRepository.enabledTools(): List<NamespacedMcpTool>`
- Produces: `McpRepository.call(namespacedName: String, args: JsonObject): Result<McpCallResult>`
- Produces: `ConnectorRuntimeUi(id, checking, serverInfo, toolCount, tools, lastCheckedAt, error)`

- [ ] **Step 1: Replace fake connector tests** with failures proving connect does not turn on before probe success, success stores tool count, later failure sets `WARN`, disconnect disables calls, duplicate tool names become `<connectorId>__<toolName>`, and cancellation leaves prior state unchanged.
- [ ] **Step 2: Run** connector tests; expect current fake behavior to fail.
- [ ] **Step 3: Implement the repository facade and inject it through `AppContainer`/`VmFactory`.**
- [ ] **Step 4: Rewrite `ConnectorsViewModel.connect` to accept config and perform a real probe; remove the unconditional `ConStatus.ON` path.**
- [ ] **Step 5: Run** connector tests; expect pass.

### Task 5: Connector configuration and diagnostics UI

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/vm/AppViewModel.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/Sheets.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/SkillsConnectors.kt`

**Interfaces:**
- Consumes: `ConnectorRuntimeUi`, `McpConnectionConfig`, `McpProbe`
- Produces: `SheetKind.ConnectorConfig`

- [ ] **Step 1: Add a connector-config sheet** with endpoint, None/Bearer/custom-header auth, custom headers, Check/Connect, progress, and inline errors.
- [ ] **Step 2: Make catalog Connect open configuration unless a saved config exists; saved config still must probe before connected.**
- [ ] **Step 3: Show actual server name/version, last check, tool count, and tool chips in connector detail.**
- [ ] **Step 4: Make add-by-URL create a custom catalog row plus prefilled runtime config, then probe; never report success on validation alone.**
- [ ] **Step 5: Build and manually inspect long endpoint/header/tool lists for clipping and secret visibility.**

### Task 6: MCP verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Run** all unit tests; expect pass.
- [ ] **Step 2: Run** `assembleDebug`; expect success.
- [ ] **Step 3: Document direct MCP requirements and remove README claims that connector logic is deferred.** State that branded OAuth is not bundled.
