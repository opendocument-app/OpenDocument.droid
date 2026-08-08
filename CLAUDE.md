# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

## Commands

- `./gradlew assembleProDebug` (also `assembleLiteDebug`, `bundleProRelease`,
  `bundleLiteRelease`); `./build-test.sh` adds the test apk.
- `./gradlew testProDebugUnitTest` (jvm), `./gradlew connectedAndroidTest` (device).
- `./gradlew spotlessApply` / `spotlessCheck` (google-java-format AOSP, ktfmt kotlinlang)
  and `./gradlew lintProDebug`. Lint errors fail the build; spotless has its own workflow.
- `fastlane android deployPro version:v4.8.0` / `deployLite version:v4.8.0`. The version is
  required; a lane handed none errors out.

## Architecture

A `FileLoader` loads on `LoaderService`'s background thread and reports through
`FileLoaderListener`; `LoaderServiceQueue` holds requests until the service is bound.
`MetadataLoader` caches and identifies the file, `RawLoader` takes csv, svg and xml,
`CoreLoader` renders the rest odrcore handles and publishes the html on a local server, and
`OnlineLoader` uploads to a web viewer what neither can open.

`MainActivity` owns the service binding and the action modes (find, tts, edit), and swaps
between `LandingFragment` (recent documents and settings) and `DocumentFragment`, which
shows the result in `PageView` - a WebView - with `DocumentActions` over it.

There is **no options menu**: `menu_main.xml` is gone and the action bar is hidden. An
action on the open document is a `DocumentActions` button; anything else - the ad removal,
the consent form - is a row in the landing screen's settings section.

Source is `app/src/main/java/app/opendocument/droid/`: `background/` for the loaders and
stored state, `ui/` for the screen, `nonfree/` for analytics, billing and ads.

## Tools

Under `tools/`, both for *looking* at the app rather than testing it - nothing asserts.
**`render-sweep`** opens a corpus of documents and records a screenshot, the WebView's text
and logcat for each. **`screen-tour`** walks a build through six screens and lays two
builds' screenshots side by side as a PDF; one tour walks both designs, so add to its lookup
lists rather than forking it. Reach for it before `adb shell input tap`.

## Build

Two flavors: **lite** is free with ads and tracking, **pro** is paid with neither. The
switch is the `DISABLE_TRACKING` resource bool, set per flavor in `app/build.gradle` and
read by the `nonfree/` managers.

Minimum SDK 26, target 36, compile 37 (ahead on purpose). AGP 9 / Gradle 9, with no kotlin
plugin applied - AGP brings kotlin itself. Versions in `gradle/libs.versions.toml`. R8,
resource shrinking and the configuration cache are on. Without release signing (see README)
the release variants build unsigned rather than failing.

**The version is the release run's `version` input**, not a number in the tree and not a
tag. `app/build.gradle` derives `versionName` and `versionCode` from `-Podr.version`
(`v4.8.0` -> `4.8.0` / `40800`; two digits per part, all three required, a part above 99 is
an error), and defaults to `0.0.0`. Do not put the attributes back into
`AndroidManifest.xml`: gradle's values win in the merge, so a second copy can only disagree.

Tags are written after a release, never before, and nothing is triggered by one.
`release.yml` is dispatch-only and tags what shipped as `build/<version>`; the plain
`v<version>` tag appears when the drafted release is published, which is what lets F-Droid
ship it.

### Native side

The app compiles no native code - no NDK, no python, no conan. Both halves of the JNI
interface come out of the one `app.opendocument:odr-core-android` AAR: the
`app.opendocument.core` java classes and a prebuilt `libodr_jni.so` per ABI. Keep it that
way - handles cross as raw longs and enums as ordinals with no version negotiation, so
separately versioned artifacts could drift. `CoreLoader` is the only thing wrapping it.

- It resolves from **maven central**, not github packages, which demands authentication even
  for a public artifact - f-droid and other clean source builders cannot supply it.
- Anything the bindings use must exist on **API 26**, far below what their `--release 17`
  compiler accepts. It fails only at runtime, on device.
- Nothing is unpacked at runtime. `initializeCore` only sets `TMPDIR`;
  `setOdrCoreDataPath` and `setLibmagicDatabasePath` are inert and not called.

## Rules that are easy to break

### The package names differ on purpose

`namespace` is `app.opendocument.droid`, `applicationId` is `at.tomtasche.reader` (plus
`.pro`). Do not "fix" the mismatch.

- `namespace` is only the java/kotlin package plus `R`/`BuildConfig` and is free to rename.
  The keeps in `proguard-rules.txt` are about odrcore's `app.opendocument.core`, not this.
- `applicationId` is the identity on Play and F-Droid and can never change - a new one is a
  new listing existing installs never update to. Rebranding goes through the listing title
  in `fastlane/metadata/`.
- The `MainActivity` and `CATCH_ALL` / `STRICT_CATCH` component names keep their
  `at.tomtasche.reader.*` spelling, as `activity-alias` entries: the OS persists them for
  pinned icons and "always open .odt with this app". The `ComponentName` strings in
  `MainActivity` must keep matching.
- What reads `getPackageName()` at runtime - the FileProvider authority in
  `AndroidFileCache`, the preferences file in `AppPreferences` - follows `applicationId`, or
  upgrading users lose their settings.

### Supported file types come from odrcore, and a test keeps the manifest in step

`SupportedDocumentTypes` derives two sets rather than listing mime *prefixes*:

- **what `CoreLoader` renders** (`CORE_FILE_TYPES`): `Odr.allFileTypes()` filtered by
  `capabilitiesByFileType(...).translateHtml`, minus csv. Text, images, zip and cfb, fonts,
  audio and video included.
- **what the app claims** (`CLAIMED_FILE_TYPES`): that, narrowed to
  `fileCategoryByFileType(...) == DOCUMENT`, plus text, csv and zip. Keep it narrow - the
  app plays an mp3 handed to it but does not want it in the share sheet.

`mimetypesByFileType` / `fileExtensionsByFileType` expand those into every spelling the core
accepts. Do not put a prefix list back: the app must not be able to claim a format the core
does not have, or miss one it does. A prefix match is what made it claim `.xlsb` and then
fail to open it.

XML cannot read any of that, so the `STRICT_CATCH` alias' three intent-filters are
*generated* from the same table - a filter matches a mime type exactly, so all 49 spellings
and 41 extensions are written out. `SupportedFormatsTest` asserts that
`SupportedDocumentTypes` and the package manager agree, and that every claimed mime type
reaches a loader, so a format added upstream and forgotten fails CI.

The tables live in `libodr_jni`, which is why `CoreLoaderTest`,
`SupportedDocumentTypesTest`, `RawLoaderTest` and `OnlineLoaderTest` are instrumented though
none opens a file. After caching it is `Odr.mimetype` that decides, canonicalized through
`canonicalMimeType` so the loaders see one spelling per format.

Reading the core's table directly, as `isDocument` does, must not `lowercase()` first: it
matches exactly and spells some types with capitals (`macroEnabled`). Our own sets are the
other way round - `mimeTypesOf` lowercases what it stores.

### `RawLoader` is asked before `CoreLoader`, not after it

`LoaderService.onSuccess` asks `rawLoader.isSupported` *first*, because the core would
render csv itself, line by line. `isRenderedByRaw` is the whole list: csv, plus svg and xml,
which have no odrcore file type at all. A `RawLoader` failure falls through to the core;
what the core cannot open goes to the upload offer rather than to the WebView on spec.

Routing by name is guarded - see `nameSays`. The core identifies by content, so a
`report.csv` holding an odt is an odt.

### Editability comes from the core, never from a mime type

`Document.isEditable()`/`isSavable()` decides whether `DocumentFragment` offers the Edit
button, carried on `FileLoader.Result.isEditable`. `CoreLoader.host()` only holds a document
open when the core says yes, so having one *is* the answer. Do not reintroduce a list of
editable formats in the UI.

`DecodedFile.capabilities()` is asked first, as a shortcut: opening a document costs a
second parse, so a format declaring no `edit`/`save` is never opened to be told no. It is an
upper bound - the document still answers.

### Storage access

The app declares **no storage permission**, only `INTERNET`, and has to stay that way:
`READ_EXTERNAL_STORAGE` has not reached documents since scoped storage, `READ_MEDIA_*`
covers only media, and Play restricts `MANAGE_EXTERNAL_STORAGE` to file managers.

Everything goes through SAF: `ACTION_OPEN_DOCUMENT`, read only, one file at a time.
`PersistedUriPermissions` persists the grants and reclaims them by reconciling against the
recent list rather than releasing on close. Do not add a release next to
`documentFragment.loadUri()`: that call only queues the load, so the stream is opened long
after it returns.

### Kotlin, and the three `@Jvm` annotations left

The only java is `com/commonsware/android/print`, vendored so it can be diffed against
upstream. It calls nothing of ours, so no java-to-kotlin call exists and `@JvmStatic`,
`@JvmField`, `@JvmOverloads` and `@Throws` are not needed for interop.

What remains is for runtimes that reflect over the bytecode: `@JvmField` on `FileLoader`'s
`CREATOR`s (parcelable needs a static field), `@JvmOverloads` on `ProgressDialogFragment`'s
constructor (the framework re-creates it with no arguments), and `@JvmStatic` on
`@BeforeClass` / `@AfterClass` in the instrumented tests.
