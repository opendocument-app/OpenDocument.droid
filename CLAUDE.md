# Repository guidance

See README.md for build, release, signing, and translation commands.

## Checks

- `./gradlew assembleDebug` builds Lite, Pro, and Foss.
- `./gradlew testProDebugUnitTest connectedAndroidTest` runs JVM and device tests.
- `./gradlew spotlessApply spotlessCheck lintProDebug lintLiteDebug` formats and checks code.
- Keep vendored Java under `com/commonsware/android/print` and its license headers.

## Architecture

Source: `app/src/main/java/app/opendocument/droid/`.

- `background/`: document loading, saving, cache, preferences, and recents.
- `ui/`: activities, fragments, action modes, and widgets.
- `nonfree/`: logging, billing, ads, and edition capabilities.

`MainActivity` owns an activity-scoped `DocumentLoader`. Its worker thread runs
`FileCache` → `FileIdentifier` → `CoreLoader`; results return on the main thread.
`DocumentSaver` writes edits. Keep one loader and one rendering backend. Unsupported
formats belong in OpenDocument.core; documents stay on the device.

`DocumentRequest` describes the open request; `IdentifiedFile` describes the cached
copy; `LoadedDocument` adds rendered parts and core capabilities.

`LandingFragment` shows recents and settings. `DocumentFragment` displays documents
in `PageView`, with `DocumentActions` above the page. Document actions belong there;
app settings, ad removal, and consent belong on the landing screen.

## Build and editions

Minimum SDK 26, target 36, compile 37. Compile and target differ intentionally.
AGP supplies Kotlin support; do not add a Kotlin plugin. Versions live in
`gradle/libs.versions.toml`. Keep R8, resource shrinking, and configuration caching.
The release version comes from `-Podr.version`; do not duplicate it in the manifest.

| Edition | Ads and consent | Play review | Advanced editing |
|---|---|---|---|
| Lite | yes | yes | no |
| Pro | no | yes | yes |
| Foss | no | no | yes |

`src/ads` and `src/noAds`, and `src/review` and `src/noReview`, provide matching APIs.
Add methods to both implementations. Use `Features`, never `BuildConfig.FLAVOR`.
`MainActivity.initializeManagers` may run again after Play Services resolution.
Analytics and crash reporting only log locally.

The app compiles no native code. Use the single `odr-core-android` AAR for matching
Java bindings and JNI libraries. Keep bindings compatible with API 26.
`CoreLoader.initializeCore` sets `TMPDIR` before the first native call.
The core AAR keeps debug info in its libraries. AGP strips them with the NDK that
`libs.versions.toml` pins; CI installs that NDK and `verify-stripped.sh` checks releases.

## Stable identities

- Namespace: `app.opendocument.droid`.
- Application IDs: `at.tomtasche.reader`, `.pro`, and `.foss`. Never rename them.
- Keep historical `at.tomtasche.reader.*` activity aliases for launcher pins and defaults.
- FileProvider authorities and default preference filenames follow the application ID.
- Foss installs beside Play editions; F-Droid strips its suffix for its existing listing.

## Formats and rendering

`SupportedDocumentTypes` derives renderable formats from the core's `translateHtml`
capability. Claimed formats are documents plus text, CSV, Markdown, ZIP, and images.
The manifest and `SupportedFormatsTest` must match these claims.

Use `Odr.mimetype` after caching and canonicalize aliases. Do not lowercase MIME types
before core lookups: some core spellings contain capitals such as `macroEnabled`.

Core text detection without a known charset is a fallback, not identification.
Both `FileIdentifier` and `CoreLoader.host` reject that fallback. Filename hints may
override text detection for documents and types without content detection.

Use core capabilities to determine editability and decryption. Carry the result as
`EditingKind`; do not duplicate editable-format lists in the UI. Unreadable formats
use the unsupported callback, reopen offer, and contact dialog.

## Editing

- Render with editor support once. Entering edit mode calls `odr.editing.enable()`.
- Use `HtmlConfig.hostMessageHandler` for page callbacks; do not inject replacements.
- Lite uses paragraph scope. `outOfScope` offers Pro.
- Gate individual tools, not the entire edit mode. Highlighting and sheet fill remain free.
- Undo, redo, and save belong to the action bar; formatting belongs to `EditingTools`.
- Let the page arm PDF tools through `odr.annotation.press` and `markOnSelection`.
- Reopen the cached original for each save attempt; partially applied edits cannot be retried.

## Display and storage

Night mode uses AppCompat's local mode. Clear the override when it matches the system.
Document darkening defaults to the core's color-scheme capability, with overrides per kind.
Translate both schemes with `HtmlColorScheme.SYSTEM`; toggle darkening without re-rendering.

Margins use `textDocumentMargin` and affect text documents only. Re-render while retaining
the selected tab and scroll fraction. Keep the existing landing-screen margins setting.

WebView owns fit and zoom through `useWideViewPort` and `loadWithOverviewMode`.
Do not fix `HtmlConfig.viewportWidth` to the opening screen width.

Declare no storage permission. Open one read-only file through SAF.
`PersistedUriPermissions` retains grants for recents and pending loads. Do not release
one immediately after `loadUri`, which only queues the read.

## Review invitations

Count fresh document opens, excluding reloads and app launches. Ask on the landing screen
or when a document is closed, never while opening it or after a failed load. Space asks by
5, 10, 20, 50, and 100 additional documents, at least two weeks apart, with five asks total.
Record an ask when handed to Play, even if Play's quota prevents display.

## Tests and screenshots

`tools/render-sweep` records corpus renders, text, and logs. `tools/screen-tour` walks the
UI and compares builds. These are inspection tools, not assertions. Extend screen-tour's
lookup lists before using raw coordinate taps.

`ScreenshotTests` runs only with a named device and requires API 35+. It writes to
`additionalTestOutputDir`, which survives test APK cleanup. Generate fixtures with
`scripts/make-screenshot-documents.py`; keep the locale mapping in `store_screenshots.py`.

Capture phone and tablet sets, with the tablet filling both Play tablet slots. Capture
both `04-edit` and `04-edit-lite`; staging selects the edition's image. Screenshot failure
must not block listing text uploads. Do not add screenshot hooks to production code.

Use JVM annotations only where reflection requires them: Parcelable `CREATOR` fields
and instrumented JUnit `@BeforeClass`/`@AfterClass` methods.
