# CLAUDE.md

Guidance for Claude Code working in this repository. The README covers building, releasing
and translating; this file covers how the code is shaped and which decisions not to undo.

## Commands

- `./gradlew assembleProDebug` (also `assembleLiteDebug`, `assembleFossDebug`,
  `bundleProRelease`, `bundleLiteRelease`). `./build-test.sh` adds the test apk.
- `./gradlew testProDebugUnitTest` (jvm), `./gradlew connectedAndroidTest` (device).
- `./gradlew spotlessApply` / `spotlessCheck` (google-java-format AOSP, ktfmt kotlinlang) and
  `./gradlew lintProDebug`. Lint errors fail the build.
- `fastlane android deployPro version:v4.8.0` / `deployLite`. The version is required.
- `fastlane android screenshots` takes the store set off one emulator (README, Screenshots).

## Architecture

Source is `app/src/main/java/app/opendocument/droid/`: `background/` for the loaders and
stored state, `ui/` for the screens, `nonfree/` for analytics, billing and ads.

`DocumentLoader` is a `ViewModel` scoped to `MainActivity`. It opens a document on its own
thread and reports on the main one: `FileCache` stores the bytes, `FileIdentifier` names and
types the copy, `CoreLoader` renders it and serves the html from a local server, and
`DocumentSaver` writes edits back. A `DocumentRequest` is what the user asked for, an
`IdentifiedFile` the cached copy, and a `LoadedDocument` the two plus the parts to show.

There is one loader. Do not add a loader base class or a loader-type enum: a format the app
cannot open is a format odrcore should learn. Nothing after `CoreLoader` opens a file, and no
document leaves the device. The only answer to an unsupported file is `onUnsupported`, the
reopen bar and the contact dialog.

`MainActivity` owns the loader and the action modes (find, tts, edit). It swaps between
`LandingFragment` (recent documents and settings) and `DocumentFragment`, which shows the
page in `PageView`, a WebView, with `DocumentActions` over it. There is no options menu and
the action bar is hidden. An action on the open document is a `DocumentActions` button.
Anything else (ad removal, the consent form) is a row in the landing screen's settings.

### Tools

`tools/render-sweep` opens a corpus of documents and records a screenshot, the WebView text
and logcat for each. `tools/screen-tour` walks a build through six screens and lays two
builds side by side as a PDF. Both only look; nothing asserts. Use screen-tour before
`adb shell input tap`, and add to its lookup lists rather than forking it.

## Build

Minimum SDK 26, target 36, compile 37 on purpose. AGP 9 and Gradle 9, no kotlin plugin
applied. Versions in `gradle/libs.versions.toml`. R8, resource shrinking and the
configuration cache are on. The version comes from `-Podr.version` (README, Versioning); do
not put `versionCode` or `versionName` back into `AndroidManifest.xml`.

### Flavors

| | ads + consent sdk | play in-app review | goes to |
|---|---|---|---|
| lite | yes | yes | play, free |
| pro | no | yes | play, paid |
| foss | no | no | github release, f-droid |

The classes that call those libraries live outside `src/main`: `src/ads` and `src/review`,
with a no-op of the same shape in `src/noAds` and `src/noReview`. `app/build.gradle` names
two of the four per flavor. A method added to one copy has to be added to the other;
`assembleDebug` builds all three and catches it.

Code that has to ask reads `Features`, never the flavor name. `Features.withAds` comes from
`LINKS_ADS` and `Features.advancedEditing` from `ADVANCED_EDITING`, both in `Linked.kt` in
`src/ads` and `src/noAds`, next to the classes they stand for. Do not add a
`BuildConfig.FLAVOR` comparison: one made `BillingManager` miss foss. Do not name a flag
after a behaviour it only implies. `AnalyticsManager` and `CrashManager` write to logcat
only, so there is no tracking and no switch for it. `MainActivity.initializeManagers` gates
ads and billing on `Features.withAds` and on `PlayServices`, and can run twice.

foss carries `applicationIdSuffix .foss` so a sideload sits beside a play install. F-Droid
strips it, since its listing is `at.tomtasche.reader`.

### Native side

The app compiles no native code. Both halves of the JNI interface come from the one
`app.opendocument:odr-core-android` AAR on maven central: the `app.opendocument.core` java
classes and a prebuilt `libodr_jni.so` per ABI. Keep them in one artifact, because handles
cross as raw longs and enums as ordinals with no version negotiation. `CoreLoader` is the
only wrapper. Anything the bindings use must exist on API 26; a newer API fails only at
runtime. Nothing is unpacked at runtime; `initializeCore` only sets `TMPDIR`.

## Rules

### Package names differ on purpose

`namespace` is `app.opendocument.droid`, `applicationId` is `at.tomtasche.reader` (plus
`.pro` or `.foss`). Do not align them.

- `namespace` only names the kotlin package and `R`/`BuildConfig`.
- `applicationId` is the identity on Play and F-Droid and can never change.
- `MainActivity`, `CATCH_ALL` and `STRICT_CATCH` keep their `at.tomtasche.reader.*` component
  names as `activity-alias` entries, because the OS persists them for pinned icons and
  default-app choices. The `ComponentName` strings in `MainActivity` must match.
- The FileProvider authority in `FileCache` and the preferences file in `AppPreferences`
  follow `getPackageName()`, so upgrading users keep their settings.

### Supported file types come from odrcore

`SupportedDocumentTypes` derives two sets, never a list of mime prefixes:

- `CORE_FILE_TYPES`: `Odr.allFileTypes()` filtered by `capabilitiesByFileType(...).translateHtml`.
- `CLAIMED_FILE_TYPES`: that, narrowed to `fileCategoryByFileType(...) == DOCUMENT`, plus
  text, csv and zip. Keep it narrow: the app plays an mp3 handed to it but must not sit in
  the share sheet for one.

The `STRICT_CATCH` intent-filters are generated from the same table, every mime spelling and
extension written out. `SupportedFormatsTest` asserts that `SupportedDocumentTypes` and the
package manager agree, so a format added upstream and forgotten fails CI. A prefix list once
claimed `.xlsb` and failed to open it.

The tables live in `libodr_jni`, so `RenderedByCoreTest` and `SupportedDocumentTypesTest`
are instrumented. After caching, `Odr.mimetype` decides, canonicalized through
`canonicalMimeType`. `isDocument` reads the core's table and must not `lowercase()` first,
because the core spells some types with capitals (`macroEnabled`). Our own sets lowercase
what they store.

### `text/plain` from the core is a guess unless a charset came with it

Text is the core's fallback for bytes nothing else claims, and it throws only once a page is
rendered, on the server thread. So `FileIdentifier` drops a `text/plain` with no charset
(`hasKnownCharset`) and `CoreLoader.host()` refuses the same file up front. Both are needed:
the first keeps `isRenderedByCore` off a `.bin`, the second stops a success bar over a page
that cannot draw. `LandingTests.aDocumentThatFailsToOpenComesBackToTheList` holds this.

For the same reason the file name can outrank the content: a pdf with an http response in
front reads as text. `CoreLoader.openFile` opens it again as the filename's type, but only
where the core files that type as a `DOCUMENT`.

### Editability comes from the core, never from a mime type

`CoreLoader.editingOf` asks the opened file, and the answer rides on `LoadedDocument.editing`
as an `EditingKind`: `DOCUMENT`, `SHEET`, `TEXT`, `ANNOTATION`, `NONE`. It comes from
`Document.isEditable()`/`isSavable()`, `TextFile.isSavable()`, `PdfFile.isAnnotatable()`.
`DecodedFile.capabilities()` is asked first as an upper bound, to save a second parse. Do not
put a list of editable formats in the UI. Decryption is the same shape: `capabilities().decrypt`
says whether a password is worth asking for.

**The gate is on the tool, not on the mode.** Every edition opens every editable kind, so
`Features.offersEditing` is the core's answer alone. A locked `EditingTools` dims what is
pro and leaves the highlighter working, in documents and in pdfs. Do not put the whole-mode
gate back.

**The editor is in the page.** An editable document is rendered with `HtmlConfig.editable`,
and the edit button only calls `odr.editing.enable()`, with no second render. The page owns
the operation log, undo and the refusals; `editing-bridge.js`, injected by `PageView`,
forwards its callbacks. Lite narrows `HtmlConfig.editingScope` to `PARAGRAPH`, and the page
answers the rest with `outOfScope`, which `DocumentFragment` turns into the offer of pro.

**The bar holds what is done to the document, the strip what is done to the text.** Undo,
redo and save are `menu/edit.xml`, dimmed by `EditActionModeCallback`. `EditingTools` under
the bar is formatting only, one 48dp square per tool: a tap does the tool's job, a long press
opens its colours. The text colour opens on a tap too. No chevrons, no size menu.

**A pdf's tools are the page's to arm.** `odr.annotation.press` marks a standing selection
and arms where there is none; `markOnSelection` then marks each selection. Do not disarm on
the app's side.

**Nothing is held open between the render and the save.** `CoreLoader.writeEdits` opens the
cached copy again and applies the page's payload. An edit that throws halfway leaves that
copy half changed, so a retry must not start from it.

### The review sheet is asked for where the user is waiting for nothing

`ReviewInvitation` decides, `MainActivity.askForReviewIfEarned` asks, and `InAppReview`
hands the sheet to play. Two moments qualify: a document closed back to the list, and the
landing screen at app start. Not `onLoadSuccess`, and not `closeFailedDocument`. Only fresh
document opens count (`DocumentFragment.freshOpenPending` excludes reloads), and app opens do
not. Spacing is ours, because Play's quota is undocumented: after 5, 10, 20, 50, 100 more
documents, at least two weeks apart, five asks in total. The ask is recorded when handed to
play, not when it returns.

### How the document looks is answered over the document, not in the settings

Three `DocumentActions` buttons remember their last choice:

- **Night mode** is the app's own, through `AppCompatDelegate.setLocalNightMode`.
  `NightModeSetting` stores no override once the choice agrees with the system again.
- **Darkening** defaults to `capabilitiesByFileType(...).colorScheme` and is overridden per
  kind of document. `CoreLoader` translates with `HtmlColorScheme.SYSTEM`, and
  `PageView.setDarkeningAllowed` picks at display time, so the button renders nothing again.
  Do not put a list of formats back.
- **Margins** are odrcore's `textDocumentMargin`, so the button re-renders through
  `DocumentLoader.reload`. `PaginationSetting.affects` limits it to text documents.
  `DocumentFragment` carries the tab and the scroll fraction over.

Do not move these into a settings screen. `PaginationSetting` keeps its landing row only
because it already had one.

### Fitting the page to the screen is the WebView's job

`PageView` sets `useWideViewPort` and `loadWithOverviewMode`. Since core 7.2.0 the page's
meta states the zoom floor it needs, so an A0 pdf can zoom out to fit. Do not set
`HtmlConfig.viewportWidth`: it freezes the fit at the width the document was opened at.
`initialZoom`, `odr.setZoom` and `viewportContent` are for hosts with their own zoom control.

### Storage access

The app declares no storage permission, only `INTERNET`. Everything goes through SAF:
`ACTION_OPEN_DOCUMENT`, read only, one file at a time. `PersistedUriPermissions` persists the
grants and reclaims them against the recent list. Do not release a grant next to
`documentFragment.loadUri()`: that call only queues the load.

### Store screenshots

`ScreenshotTests` is the whole of it: an instrumented test lays out the samples, fills the
recent list and switches the language from inside the app's process. Do not add a screenshot
back door to the app. Details:

- It skips itself unless a run names a device, and refuses anything below API 35.
- It writes into gradle's `additionalTestOutputDir`, which is copied back before the apks
  are uninstalled. `getExternalFilesDir` goes with the uninstall.
- `scripts/make-screenshot-documents.py` writes the documents into the test apk's assets.
  `frame-screenshots.py` draws the frame, and the feature graphic from the first capture.
  `store_screenshots.py` says what a full set is and stages it, and holds the one table of
  which locale reads which language's documents. Do not copy that table into the test.
- The tablet set goes into both tablet slots, because Play falls back to the phone set only
  where a slot is empty.
- In `release.yml` the listing is not gated on the screenshots. Do not put them back into
  a plain `needs:`, or a wedged emulator takes the listing text down with it.

### Kotlin

The only java is `com/commonsware/android/print`, vendored to diff against upstream. No
java-to-kotlin call exists, so `@JvmStatic`, `@JvmField`, `@JvmOverloads` and `@Throws` are
only for runtimes that reflect: `@JvmField` on the parcelable `CREATOR`s, and `@JvmStatic` on
`@BeforeClass`/`@AfterClass` in instrumented tests.
