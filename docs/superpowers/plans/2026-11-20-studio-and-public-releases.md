# Studio and public releases implementation plan

**Goal:** Add the reversible Studio style and prepare Clodex for source distribution and opt-in APK installation from GitHub Releases.

- Remove the duplicate tablet sidebar model switcher, preserving model selection in the composer. Add Classic/Studio in Appearance, keeping Classic the default and the existing preview and file list intact.
- Document build, contribution, release versioning, Android signing and update behavior. Exclude local OAuth data, keys, personal screenshots and generated outputs from the published source. Confirm the repository owner/name, license and signing channel before publication.
- Use one build-time `owner/repo` value for public Releases. Check stable releases and compare monotonic version codes. Display release notes as inert text. Download only after an explicit tap, verify APK identity, signer and version, then hand off to the Android installer. No repository configuration means no update requests.
- Verify update filter and fail-closed behavior in unit tests, build/install a local APK as appropriate, and preserve the previous APK. Test actual update installation only when a same-signer release is available.
