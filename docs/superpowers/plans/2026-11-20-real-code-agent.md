# Real Android Code Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace scripted Code sessions with provider-driven workspace reads, approved writes, real diffs, and real GitHub commits/PRs where credentials permit.

**Architecture:** A workspace abstraction hides SAF and GitHub differences. Code-specific tools adapt that abstraction to the shared `AgentRunner`; persisted session events and changes drive the timeline/diff UI, so every displayed action corresponds to a completed operation.

**Tech Stack:** Kotlin coroutines, Android DocumentsContract/ContentResolver, Ktor GitHub API, Room, Compose, shared agent module.

**Spec:** `docs/superpowers/specs/2026-11-20-real-connectors-code-thinking-design.md`

## Global Constraints

- SAF workspaces support document operations, not repository shell/build claims.
- `План` never writes; `Правки` requires approval; `Авто` writes only inside the selected root.
- GitHub commit/PR UI appears only after successful GitHub API responses.
- Every path is relative, normalized, and contained below the selected workspace.
- No fabricated test, diff, branch, commit, or PR result.
- Do not initialize Git in this workspace.

## Review Focus

- Paths containing `..`, absolute paths, encoded separators, or control characters are rejected — Task 1.
- Revoked SAF grants produce a recoverable session error without data loss — Task 2.
- GitHub 409/422 branch or PR conflicts surface clearly and do not invent a number — Task 3.
- Process death with an in-memory pending approval leaves no write applied — Task 5.
- Binary/oversized files are listed but never decoded/written as text — Tasks 2 and 4.

---

### Task 1: Workspace contracts and safe paths

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/code/WorkspaceModels.kt`
- Create: `app/src/main/java/app/clodex/harness/data/code/WorkspacePath.kt`
- Test: `app/src/test/java/app/clodex/harness/WorkspacePathTest.kt`

**Interfaces:**
- Produces: `WorkspaceRef`, `WorkspaceEntry`, `WorkspaceRead`, `WorkspaceChange`, `WorkspaceApplyResult`, `WorkspaceException`, and enum `CodeMode(PLAN, EDITS, AUTO)`
- Produces: `WorkspacePath.parse(input: String): Result<WorkspacePath>`
- Produces: `interface WorkspaceGateway { suspend fun list(path: WorkspacePath): Result<List<WorkspaceEntry>>; suspend fun read(path: WorkspacePath): Result<WorkspaceRead>; suspend fun apply(changes: List<WorkspaceChange>, message: String): Result<WorkspaceApplyResult> }`

- [ ] **Step 1: Write failing path tests** for normal nested paths, slash normalization, `.`, `..`, leading slash, encoded slash/backslash, NUL/control characters, blank path rules, and max length.
- [ ] **Step 2: Run** focused tests; expect unresolved path type.
- [ ] **Step 3: Implement normalized relative paths and immutable workspace result contracts.**
- [ ] **Step 4: Run** focused tests; expect pass.

### Task 2: SAF workspace gateway

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/code/SafWorkspaceGateway.kt`
- Modify: `app/src/main/java/app/clodex/harness/util/Saf.kt`
- Test: `app/src/test/java/app/clodex/harness/SafWorkspacePolicyTest.kt`

**Interfaces:**
- Produces: `class SafWorkspaceGateway(context: Context, treeUri: Uri) : WorkspaceGateway`
- Produces: pure policy helpers `canReadText(name, mime, size)` and `canWriteText(name, byteCount)`

- [ ] **Step 1: Write failing policy tests** for text MIME/extensions, binary files, 256KB read cap, 512KB write cap, and revoked-grant error mapping.
- [ ] **Step 2: Run** focused tests; expect unresolved policy.
- [ ] **Step 3: Implement tree traversal by document IDs/display-name segments, list/read, and batch create-or-truncate apply through `DocumentsContract`.** Never convert a SAF URI into an assumed filesystem path.
- [ ] **Step 4: Return the files actually applied and any partial failure; do not claim command execution.**
- [ ] **Step 5: Run** tests/build.

### Task 3: GitHub workspace gateway

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/code/GitHubWorkspaceGateway.kt`
- Test: `app/src/test/java/app/clodex/harness/GitHubWorkspaceGatewayTest.kt`

**Interfaces:**
- Produces: `class GitHubWorkspaceGateway(http: HttpClient, repo: String, token: String, branch: String) : WorkspaceGateway`
- Produces: `suspend fun createBranch(base: String, head: String): Result<String>`
- Implements: `WorkspaceGateway.apply(changes, message)` as one real Git data commit on the session branch
- Produces: `suspend fun openPullRequest(base: String, head: String, title: String, body: String): Result<GitHubPullRequest>`

- [ ] **Step 1: Add MockEngine tests** for tree/file reads, Base64 decode, create ref, blob/tree/commit/ref sequence, PR creation, 401/403/404/409/422, rate limits, token redaction, and real response number/SHA propagation.
- [ ] **Step 2: Run** focused tests; expect unresolved gateway.
- [ ] **Step 3: Implement GitHub API calls with existing API version/user-agent conventions and bounded response parsing.**
- [ ] **Step 4: Run** focused tests; expect pass.

### Task 4: Code tools and factual diffs

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/code/CodeAgentTools.kt`
- Create: `app/src/main/java/app/clodex/harness/data/code/TextDiff.kt`
- Test: `app/src/test/java/app/clodex/harness/CodeAgentToolsTest.kt`

**Interfaces:**
- Produces: `class WorkspaceOverlay(gateway: WorkspaceGateway)` whose reads include staged text and whose `changes()` returns before/after records
- Produces: `fun codeTools(overlay: WorkspaceOverlay, mode: CodeMode): List<AgentTool>`
- Produces tools: `workspace_list`, `workspace_read`, `workspace_write`
- Produces: `TextDiff.summarize(before: String, after: String): DiffSummary`

- [ ] **Step 1: Write failing tests** for tool schemas, Plan write rejection, staged Edits/Auto writes, read-after-stage behavior, before/after diff counts, binary/oversize errors, and all factual event labels.
- [ ] **Step 2: Run** focused tests; expect unresolved adapters.
- [ ] **Step 3: Implement list/read/stage adapters, overlay reads, and a deterministic line diff summary.** Return errors as tool results; never fabricate success or mutate the workspace before the run's apply gate.
- [ ] **Step 4: Run** focused tests; expect pass.

### Task 5: Persisted session events and migration

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/data/Entities.kt`
- Modify: `app/src/main/java/app/clodex/harness/data/HarnessDb.kt`
- Modify: `app/src/main/java/app/clodex/harness/data/Repos.kt`
- Test: `app/src/test/java/app/clodex/harness/SessionEventCodecTest.kt`

**Interfaces:**
- Produces: `SessionEventEntity(id, sessionId, kind, title, detail, status, createdAt)`
- Produces: `SessionChangeEntity(id, sessionId, path, beforeText, afterText, createdAt)`
- Extends: `SessionRepository.events(id)`, `changes(id)`, `addEvent`, `addChange`
- Produces: `MIGRATION_3_4`

- [ ] **Step 1: Write failing bounded-codec tests** for event/change text limits and malformed values.
- [ ] **Step 2: Add Room entities/DAOs and explicit migration 3→4; register it alongside 2→3.** Existing sessions remain readable, but no synthetic event rows are backfilled.
- [ ] **Step 3: Extend Room and fake repositories and run all tests/build.**

### Task 6: Real Code session orchestration

**Files:**
- Create: `app/src/main/java/app/clodex/harness/data/code/CodeAgent.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/vm/CodeViewModel.kt`
- Modify: `app/src/main/java/app/clodex/harness/AppContainer.kt`
- Modify: `app/src/test/java/app/clodex/harness/ViewModelTests.kt`

**Interfaces:**
- Produces: `interface CodeAgent { fun run(session: SessionEntity, task: String, workspace: WorkspaceRef, mode: CodeMode): Flow<AgentEvent>; fun continueRun(...): Flow<AgentEvent> }`
- Produces: `CodeViewModel.pendingApproval: StateFlow<CodeApproval?>`
- Consumes: `AgentRunner`, workspace gateways, session events/changes.

- [ ] **Step 1: Replace scripted Code tests** with fake-agent tests proving launch streams a real reply, tool events persist, write approval gates execution, deny records denial, failure remains retryable, sendMessage continues provider context, and cancellation clears RUN state.
- [ ] **Step 2: Run** focused tests; expect current scripted methods to fail.
- [ ] **Step 3: Implement workspace resolution, overlay creation, and CodeAgent request context.** A missing/revoked workspace must fail before provider invocation.
- [ ] **Step 4: Rewrite launch/send/allow/deny to consume actual `AgentEvent`s, messages, events, and staged changes.** `План` never stages; `Правки` waits for final diff approval; `Авто` applies the bounded change set at completion. Remove hard-coded assistant text and automatic fake completion.
- [ ] **Step 5: Apply the approved change batch through the workspace gateway. For GitHub, use the real commit result and permit a subsequent real PR; for local SAF, explain that PR is unavailable.**
- [ ] **Step 6: Run** focused tests; expect pass.

### Task 7: Dynamic Code UI

**Files:**
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/CodeScreens.kt`
- Modify: `app/src/main/java/app/clodex/harness/ui/screens/Sheets.kt`

**Interfaces:**
- Consumes: persisted session events/changes and `CodeViewModel.pendingApproval`

- [ ] **Step 1: Replace static timeline nodes with DAO-backed event rows and live thinking/progress.**
- [ ] **Step 2: Replace static diff screen data with actual `SessionChangeEntity` before/after content and summaries.**
- [ ] **Step 3: Show approval card with operation/path/diff; only show PR buttons for GitHub sessions with completed changes.**
- [ ] **Step 4: Remove every hard-coded “2 files”, “tests passed”, command, diff, and PR number from Code screens.**
- [ ] **Step 5: Build and inspect empty, running, approval, failure, done-with-changes, local, and GitHub states.**

### Task 8: Code verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Run** `sh gradlew testDebugUnitTest`; expect pass.
- [ ] **Step 2: Run** `sh gradlew assembleDebug`; expect success.
- [ ] **Step 3: Update README** with real Code capabilities and explicit SAF/no-shell limitations; remove all claims that Code is a demo.
