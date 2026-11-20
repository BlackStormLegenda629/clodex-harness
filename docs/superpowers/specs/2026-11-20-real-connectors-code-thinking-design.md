# Real connectors, Code chat, and thinking UI

Date: 2026-11-20

## Intent

Turn the existing Android prototype into an honest working client: model choices have recognizable marks, typography is selectable, every sheet is opaque, connectors perform real MCP handshakes and tool calls, Code sessions use the selected provider instead of scripted responses, and chat exposes a Claude-like progress summary without claiming to reveal private model chain-of-thought. Also provide a standalone single-file `thinking.html` implementation of the requested component.

## Goals

- Show local provider/model icons for Anthropic, OpenAI, DeepSeek, and custom providers.
- Add bundled UI and code fonts; no runtime font downloads.
- Make the custom sheet surface fully opaque everywhere while retaining the dimmed scrim.
- Connect to MCP servers over Streamable HTTP, with SSE-compatible responses, configurable authorization, real `initialize`, `tools/list`, and `tools/call`.
- Never show a connector as connected merely because a button was pressed.
- Give chat and Code one shared tool-capable provider loop.
- Make Code read and modify a selected SAF tree; support GitHub repositories through the GitHub API when a token permits it.
- Replace scripted Code timeline, diff, replies, commit, and PR behavior with results derived from actual operations.
- Add a Claude-like “Ход рассуждений” progress row, summary modal, duration, and chat-list generation status.
- Produce one standalone HTML/CSS/JS file with `addStep(text)` and `finishThinking(seconds)` and a 1.2-second demo sequence.

## Non-goals and constraints

- The app will not reveal hidden chain-of-thought. It displays provider-supplied reasoning summaries when available and factual agent events such as “reading files” or “calling tool”; otherwise it uses neutral progress labels.
- There is no app-owned backend in this phase. Therefore the 41 branded catalog entries cannot offer universal one-tap vendor OAuth. Each needs a real MCP endpoint and credentials supplied by the user, unless an official endpoint requires no credentials.
- SAF grants file access, not a shell working directory. `/system/bin/sh` remains sandboxed. The Code agent can inspect and edit SAF documents, but cannot truthfully run repository builds there.
- GitHub write, branch, commit, and PR actions require a token with the relevant repository permissions. Local SAF workspaces do not expose PR actions.
- Provider/model IDs remain configurable because catalog display names may not be valid IDs on every endpoint.
- Secrets follow the app’s current local settings storage in this pass; moving all existing provider and connector secrets to Android Keystore is a separate security migration.

## Approaches considered

### 1. Android-first, direct MCP and provider calls — selected

The app talks directly to configured MCP servers, model providers, SAF, and GitHub. It is deployable as one APK and removes the fake paths now. The tradeoff is that branded services need user-supplied endpoints/tokens rather than app-owned OAuth.

### 2. Backend-assisted OAuth and remote sandbox

An app backend owns OAuth callbacks for all vendors and runs code in remote containers. This can deliver one-tap integrations and real builds, but requires a deployed service, domain, secrets, per-vendor app reviews, and operational security. Rejected for this phase by the chosen Android-first scope.

### 3. Keep local status toggles and scripted Code behavior

Cheapest, but directly conflicts with “working” and “not demo”; rejected.

## UX design

### Models and fonts

- Each model row starts with a local provider logo: Anthropic, OpenAI, DeepSeek, or a generated custom-provider mark.
- The model chip in chat uses the same compact mark.
- Appearance settings gain “Шрифт интерфейса”: Inter, Manrope, Noto Sans, and System.
- “Шрифт кода” gains JetBrains Mono, Fira Code, IBM Plex Mono, Source Code Pro, and System Mono.
- Font files are bundled under `res/font`; selection is stored in DataStore.

### Opaque sheets

- `SheetSurface` paints an opaque `c.bg`/`c.s1` surface before any decorative gradient.
- Accent tint is pre-composited over the opaque surface; no alpha-only color is allowed as the first brush stop.
- The background scrim remains translucent black because it is intentionally outside the sheet.
- Add-skill, provider, connector, workspace, model, and confirmation sheets all inherit this one surface.

### Connector setup

- Pressing “Подключить” opens configuration, not a fake success state.
- Fields: endpoint, transport (`Streamable HTTP` with SSE response support), auth mode (`None`, `Bearer`, custom headers), and secret/header values.
- “Проверить” performs `initialize`, sends `notifications/initialized`, then `tools/list`.
- Only a successful tool list sets status to connected. Failures store/display the last error and leave the connector disconnected or warning.
- Detail view shows server name/version, last check, tool count, and tool names.
- Enabled connector tools are available to ordinary chat and Code through a namespaced tool name, preventing collisions.

### Real Code sessions

- Starting a session resolves the selected workspace and starts a real provider request.
- The model receives the task, operating mode, workspace capabilities, selected project instructions, and tool schemas.
- SAF tools: list directory, read text, create text file, replace/write text, and inspect metadata. Every path is resolved below the granted tree URI.
- GitHub tools: read tree/file; with sufficient token permission, create branch, write files/commits, and open a PR.
- `План` forbids writes; `Правки` queues write/remote actions for approval; `Авто` may execute scoped workspace writes but still blocks unsupported shell claims.
- Timeline rows come from real tool events. Diff is computed from captured before/after text. Assistant messages stream from the selected provider.
- Errors remain visible and retryable. No fabricated “tests passed”, commit number, or PR number is shown.

### Thinking summary

The Android UI mirrors the standalone component:

```text
  • •  Ход рассуждений                         >
       [streaming assistant answer below]
```

- While generating, two terracotta dots pulse in sequence.
- Completion hides the dots and shows `Ход рассуждений · 12 с`.
- Tapping opens a centered `Сводка` dialog on tablets and phones, max-width constrained, with opaque `#1f1f1f`, 24dp corners, thin border, no shadow, close button, scrim click, and system Back.
- Steps use a bundled/system serif face; new steps fade in and rise 6dp over 220ms. Only the current marker pulses.
- Provider reasoning summaries may supply text. Tool/agent events create factual steps. Plain chat always has neutral states (“Анализирую запрос”, “Формирую ответ”), never invented private reasoning.
- The chat list shows `В работе · только что` and a small dot for the chat currently streaming; afterward normal relative time returns.

### Standalone HTML

- Root file: `thinking.html`.
- One file, no libraries, fonts loaded only through system fallbacks.
- Responsive two-column tablet demonstration and single-column phone layout.
- Exposes global `addStep(text)` and `finishThinking(seconds)`.
- Includes a restartable 4–5-step demo at 1.2-second intervals.
- Modal closes by X, scrim, and Escape and restores focus to the trigger.
- Respects `prefers-reduced-motion`.

## Architecture

### MCP

- `McpClient`: JSON-RPC request/response, session ID handling, Streamable HTTP and `text/event-stream` parsing.
- `McpConnectionStore`: connector endpoint/auth/config keyed by catalog ID.
- `McpRepository`: connect/check/list/call facade consumed by view models and agent loop.
- Connector metadata remains in `ConnectorCatalog`; runtime configuration stays separate from catalog presentation.

### Shared agent loop

- Extend provider protocol handling from text-only streaming to typed events: text delta, reasoning-summary delta, tool call, usage, completed, and failure.
- `AgentRunner` executes bounded tool rounds, routes MCP calls, records progress, and returns a stream of UI-safe events.
- Existing ordinary chat and Code each adapt their own context/tool set to `AgentRunner`; provider-specific JSON stays in the data layer.
- Limit tool rounds and payload sizes to avoid infinite loops and oversized SAF/GitHub reads.

### Persistence

- Add connector runtime configuration and last-check information without overloading catalog description fields.
- Add session events/diffs and optional completed thinking summaries.
- Use explicit Room migrations so existing chats are not destroyed during this upgrade; remove reliance on destructive migration for this schema change.
- Keep active-generation state in memory; persisted completed messages retain duration and summary steps.

## Error and safety behavior

- Connector handshake/tool errors identify the connector and method but redact configured secrets.
- MCP result text and repository file text are treated as untrusted tool output, not higher-priority instructions.
- Workspace path/URI resolution cannot escape the selected root.
- Writes show file and diff before approval in `Правки` mode.
- Tool loops have call-count, response-size, and timeout limits.
- Cancellation stops provider collection and pending tool execution and leaves a retryable session state.

## Verification

- Unit tests for MCP framing/session headers, SSE parsing, tool discovery, tool-call errors, and secret redaction.
- Unit tests for Code modes, approval queue, SAF path containment, actual streamed reply persistence, and non-fabricated PR state.
- Unit tests for thinking state, duration, summary persistence, and chat-list status.
- Compose/UI tests where feasible for opaque sheet surface and thinking dialog close paths.
- Browser-level manual checks for `thinking.html` on narrow and tablet widths, keyboard Escape, scrim close, animation, and reduced motion.
- Final `assembleDebug` and `testDebugUnitTest`; runtime OAuth/vendor validation is out of scope because no vendor credentials are available.

## Builder review notes

Self-review found two easy-to-miss constraints and made them explicit above: branded cards cannot silently imply vendor OAuth, and SAF access cannot provide a real repository shell. The design therefore makes connection success depend on MCP discovery and makes Code report only operations it actually performed.
