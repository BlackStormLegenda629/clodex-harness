# Modern Clodex and HTML Viewer Implementation Plan

**Goal:** Ship the approved flat visual refresh and the Claude-style HTML viewing flow in an APK.

**Architecture:** Keep existing Compose navigation and tablet layout. Share the existing isolated WebView between one full-screen viewer used by replies, workspace files, and HTML artifacts. Apply the visual refresh through existing theme tokens and shared surfaces.

- [x] Add one in-app HTML card and viewer with close, a Preview/Code overflow menu, and copy/download actions. Keep copying/exporting the raw source. Preserve the existing read-only workspace browser and leave Gmail untouched.
- [ ] Replace decorative gradients and shimmer with flat surfaces, neutral colors, and visible separators; retain settings accents and tablet layout.
- [ ] Build and run offline unit tests, check APK signer, then export a separate APK. Check both viewer modes, returning to the file list, dark/light contrast, and wide layout on-device where available.

Device smoke test: open a saved HTML reply and switch both modes from the overflow menu; open an HTML file under Code → Files and close the viewer to return to the same folder; check the sidebar and composer in tablet landscape and the preview in light/dark themes. Do not request Gmail consent on the user's behalf.
