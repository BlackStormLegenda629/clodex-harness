# Claude-style viewer polish

- Match the supplied screenshots in the existing viewer: side panel on wide screens, full viewport on narrow screens and on expand, centered filename, anchored icon menu, and syntax colors on dark code.
- Limit Code Files to a centered 520 dp × 65%-height window; keep the list composed underneath the viewer so closing HTML returns to the same folder and scroll position.
- Preserve HTML isolation, copy/export behavior, and the rest of the app theme. Run offline unit tests and export a signed debug APK. Device comparison is a separate smoke check.
