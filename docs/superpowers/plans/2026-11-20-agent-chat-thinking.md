# Shared Agent Chat and Thinking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace text-only provider streaming with one bounded, tool-capable agent loop and wire factual thinking summaries/status into ordinary chat.

**Architecture:** Provider clients emit typed protocol events. `AgentRunner` owns bounded tool rounds and emits UI-safe agent events; chat adapts MCP tools and persists only completed answer/thinking summaries. Provider-specific parsing remains below the runner.

**Tech Stack:** Kotlin coroutines/Flow, Ktor streaming, kotlinx.serialization, Room, Compose.

**Spec:** `docs/superpowers/specs/2026-11-20-real-connectors-code-thinking-design.md`

## Global Constraints

- Never display raw/private chain-of-thought; only explicit provider summary fields and factual operation events.
- Maximum 8 tool rounds, 64KB per tool result, and 1MB total provider stream payload.
- Mutating or unannotated MCP tools require explicit user approval; `readOnlyHint == true` may run directly.
- Existing quota-provider fallback behavior must remain.
- Upgrade Room without deleting existing chats.
- Do not initialize Git in this workspace.

## Review Focus

- A catalog alias absent from the provider's fetched model list is visibly unverified and an exact fetched model ID remains selectable — Task 2.
- Fragmented OpenAI arguments and Anthropic mixed text/tool blocks preserve IDs and ordering — Task 2.
- A provider repeatedly requesting tools stops at 8 rounds with a visible error — Task 3.
- Cancellation while approval is pending releases the job and does not execute the tool — Task 3.
- A completed empty answer becomes a clear error, not an ellipsis presented as success — Task 4.

---

### Task 1: Agent and provider event contracts

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/agent/AgentModels.kt`
- Test: `app/src/test/java/app/clodex/harness/AgentModelTest.kt`

**Interfaces:**
- Produces: `AgentMessage`, `AgentToolSpec`, `AgentToolResult`, `AgentRequest`
- Produces: sealed `ProviderEvent` (`TextDelta`, `SummaryDelta`, `ToolCallDelta`, `ToolCallComplete`, `Completed`)
- Produces: sealed `AgentEvent` (`ThinkingStep`, `TextDelta`, `ToolApprovalRequired`, `ToolStarted`, `ToolFinished`, `Completed`)
- Produces: `interface AgentTool { val spec: AgentToolSpec; suspend fun invoke(arguments: JsonObject): AgentToolResult }`
- Produces: `interface ProviderAgentClient { fun stream(request: ProviderRequest): Flow<ProviderEvent> }`

- [ ] **Step 1: Write failing tests** for namespaced tool-name validation, read-only annotation defaults, summary sanitization, result-size truncation, and secret-free event `toString` behavior.
- [ ] **Step 2: Run** focused tests; expect unresolved contracts.
- [ ] **Step 3: Implement the immutable contracts and validation helpers.** Summary text accepts only explicit summary/event text, not raw `thinking` blocks.
- [ ] **Step 4: Run** focused tests; expect pass.

### Task 2: Typed Anthropic and OpenAI streams

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/agent/AnthropicAgentClient.kt`
- Create: `app/src/main/java/app/clodex/harness/data/agent/OpenAiAgentClient.kt`
- Create: `app/src/main/java/app/clodex/harness/data/agent/SseParser.kt`
- Modify: `app/src/main/java/app/clodex/harness/data/Remote.kt`
- Modify: `app/src/main/java/app/clodex/harness/data/Settings.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/vm/SettingsViewModel.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/Sheets.kt`
- Test: `app/src/test/java/app/clodex/harness/ProviderAgentClientTest.kt`

**Interfaces:**
- Consumes: `ProviderCfg`, resolved API model ID, `ProviderRequest`
- Produces: `AnthropicAgentClient : ProviderAgentClient`
- Produces: `OpenAiAgentClient : ProviderAgentClient`
- Produces: `SseParser.feed(line: String): List<String>`

- [ ] **Step 1: Write fixture-driven failing tests** for fragmented SSE, plain text, explicit reasoning-summary deltas, Anthropic `tool_use` JSON deltas/results, OpenAI fragmented `tool_calls`, `[DONE]`, HTTP errors, cancellation, raw-thinking suppression, and exact fetched model-ID resolution ahead of an unverified catalog alias.
- [ ] **Step 2: Run** focused tests; expect unresolved clients.
- [ ] **Step 3: Move chat-stream construction/parsing out of `Remote.kt` into typed clients.** Preserve headers, model routing, max-token settings, web-search tool, and quota classification.
- [ ] **Step 4: Ensure only documented summary fields emit `SummaryDelta`; ignore/redact raw thinking content.**
- [ ] **Step 5: Merge fetched provider model IDs into built-in model sheets, mark catalog-only aliases as unverified when absent, and select an exact fetched ID after a successful refresh.** This makes a configured provider usable even when the decorative catalog aliases are not real API IDs.
- [ ] **Step 6: Run** provider tests; expect pass.

### Task 3: Bounded tool runner and approval gate

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/agent/AgentRunner.kt`
- Create: `app/src/main/java/app/clodex/harness/data/agent/McpAgentTools.kt`
- Test: `app/src/test/java/app/clodex/harness/AgentRunnerTest.kt`

**Interfaces:**
- Produces: `interface ToolApprovalGate { suspend fun await(call: PendingToolCall): ToolDecision }`
- Produces: `class AgentRunner(provider: ProviderAgentClient, approval: ToolApprovalGate, limits: AgentLimits = AgentLimits())`
- Produces: `fun AgentRunner.run(request: AgentRequest, tools: List<AgentTool>): Flow<AgentEvent>`
- Consumes: `McpRepository.enabledTools()` and `McpRepository.call(...)` from the MCP plan.

- [ ] **Step 1: Write failing tests** for no-tool text completion, read-only invocation, mutating approval/deny, provider continuation with tool result, 8-round stop, unknown tool, tool failure, 64KB truncation, and cancellation during approval.
- [ ] **Step 2: Run** focused tests; expect unresolved runner.
- [ ] **Step 3: Implement `AgentRunner` as the sole owner of the provider/tool loop.** Never recurse; use an explicit round loop and accumulated provider messages.
- [ ] **Step 4: Implement MCP adapters and factual step labels (`Ищу…`, `Вызываю <connector> · <tool>`, `Получен результат`).**
- [ ] **Step 5: Run** focused tests; expect pass.

### Task 4: Chat persistence and non-destructive migration

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/data/Entities.kt`
- Modify: `app/src/main/java/app/clodex/harness/data/HarnessDb.kt`
- Create: `app/src/main/java/app/clodex/harness/data/ThinkingCodec.kt`
- Test: `app/src/test/java/app/clodex/harness/ThinkingCodecTest.kt`

**Interfaces:**
- Extends: `MessageEntity.thinkingSeconds: Int = 0`
- Extends: `MessageEntity.thinkingSteps: String = ""`
- Produces: `ThinkingCodec.encode(List<String>): String`, `ThinkingCodec.decode(String): List<String>`
- Produces: `MIGRATION_2_3`

- [ ] **Step 1: Write failing codec tests** for empty/list round trips, Unicode/newlines, malformed legacy data, and a maximum 30 steps/240 chars each.
- [ ] **Step 2: Run** focused tests; expect unresolved codec.
- [ ] **Step 3: Add message columns and explicit SQL migration 2→3 with non-null defaults.** Register it in `HarnessDb.create`; do not use destructive migration for version 2.
- [ ] **Step 4: Implement bounded JSON thinking codec and run tests/build.**

### Task 5: Real chat thinking/status integration

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/AppContainer.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/vm/ChatViewModel.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/ChatScreens.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/nav/HarnessApp.kt`
- Test: `app/src/test/java/app/clodex/harness/ChatAgentViewModelTest.kt`

**Interfaces:**
- Consumes: `ThinkingPresentation(chatId, thinking, startedAt, seconds, steps)` from the UI plan.
- Produces: `ChatViewModel.thinking: StateFlow<ThinkingPresentation?>`
- Produces: `ChatViewModel.generatingChatIds: StateFlow<Set<Long>>`
- Produces: `ChatViewModel.pendingToolApproval: StateFlow<PendingToolApproval?>`
- Consumes: `AgentRunner`, `ThinkingRow`, `ThinkingSummaryDialog`

- [ ] **Step 1: Add fake-runner tests** for live text, step accumulation/deduplication, duration, completed persistence, chat ID generating status, approval allow/deny, cancellation cleanup, quota fallback, and empty-completion error.
- [ ] **Step 2: Run** focused tests; expect current text-only view model to fail.
- [ ] **Step 3: Replace `ClaudeRepository.reply` collection with `AgentRunner` events while preserving project context/history.** Start with neutral factual steps `Анализирую запрос` and `Формирую ответ` only when no richer event exists.
- [ ] **Step 4: Persist answer plus completed duration/summary and expose generating IDs.** Never persist a synthetic hidden-reasoning claim.
- [ ] **Step 5: Render `ThinkingRow` above draft/completed assistant text, open the centered summary dialog, and add the approval bar.**
- [ ] **Step 6: In chat lists/sidebar show `В работе · только что` plus a dot for active IDs; restore relative time after completion.**
- [ ] **Step 7: Run** chat tests, all tests, and build.

### Task 6: Agent/chat verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Run** `sh gradlew testDebugUnitTest`; expect pass.
- [ ] **Step 2: Run** `sh gradlew assembleDebug`; expect success.
- [ ] **Step 3: Update README** from text-only streaming to bounded tools/thinking summaries and document that raw chain-of-thought is intentionally not exposed.
