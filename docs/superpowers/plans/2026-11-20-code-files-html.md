# Code files and HTML preview Implementation Plan

**Goal:** Browse the real session workspace and preview HTML inside Code.

**Architecture:** Reuse the SAF/GitHub workspace gateway and the isolated artifact WebView. Keep the browser and preview in session-local dialogs.

- Add tests for directory-first listing, unsupported file selection, and unreadable HTML. Reuse the session’s base or applied branch, never pending edits.
- Add a read-only browser with parent navigation, retry, and individual HTML previews. No new filesystem permissions.
- Offer preview on HTML fences in both live and stored Code answers; keep normal Chat unchanged. The renderer must not run scripts or load remote or local resources.
- Run `sh gradlew assembleDebug testDebugUnitTest` and export the APK only when the build succeeds.
