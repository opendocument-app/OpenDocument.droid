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
- `fastlane android screenshots` photographs the store set off one emulator - see
  **Store screenshots** below.

## Architecture

`DocumentLoader` opens a document on its own background thread and reports back on the main
one, straight through: `FileCache` stores the bytes, `FileIdentifier` names and types the
copy, `CoreLoader` renders it and publishes the html on a local server, and `DocumentSaver`
writes it back. There is nothing after the core - what it cannot open is reported as an
unsupported format.

It is a `ViewModel` scoped to `MainActivity`, so it survives a configuration change and is
there before anything asks it for a document. A `DocumentRequest` is what the user asked for,
an `IdentifiedFile` the cached copy it turned out to be, and a `LoadedDocument` the two plus
the parts to show. Do not add a loader base class or a loader-type enum: there is one loader,
and a second one is a format odrcore should learn instead.

`MainActivity` owns the loader and the action modes (find, tts, edit), and swaps between
`LandingFragment` (recent documents and settings) and `DocumentFragment`, which shows the
result in `PageView` - a WebView - with `DocumentActions` over it.

There is **no options menu**: `menu_main.xml` is gone and the action bar is hidden. An
action on the open document is a `DocumentActions` button; anything else - the ad removal,
the consent form - is a row in the landing screen's settings section.

Source is `app/src/main/java/app/opendocument/droid/`: `background/` for the loaders and
stored state, `ui/` for the screen, `nonfree/` for analytics, billing and ads - the last
of those split across flavor source sets, see Build.

## Tools

Under `tools/`, both for *looking* at the app rather than testing it - nothing asserts.
**`render-sweep`** opens a corpus of documents and records a screenshot, the WebView's text
and logcat for each. **`screen-tour`** walks a build through six screens and lays two
builds' screenshots side by side as a PDF; one tour walks both designs, so add to its lookup
lists rather than forking it. Reach for it before `adb shell input tap`.

## Store screenshots

The store *copy* is written down here; the screenshots are not. A picture of the app is
worth what the build it came off is worth, so the release run takes its own - six screens
on a phone and a tablet in all fifteen locales - frames them and hands them to supply.
Nothing is committed, and `.gitignore` says so. `OpenDocument.ios` does the same thing
against App Store Connect, and the python is deliberately close enough to lift out later.

**`ScreenshotTests` is the whole of it.** An instrumented test runs in the app's own
process, so laying the samples out, filling the recent list and switching the app's
language need no code in a build that ships - there is no `ScreenshotMode` in the apk and
no line of this in `MainActivity`. Everything past that goes through the app the way a
user does. Do not add a back door to the app for a screenshot: whatever it would need,
the test can already reach.

- It **skips itself unless a run names a device**, so `connectedCheck` on five API levels
  does not photograph a store listing nobody asked for, and it **refuses anything below
  API 35**, where the app does not tell the system bars to follow a light theme and every
  picture gets a white clock on a white bar.
- It writes into gradle's `additionalTestOutputDir`, which gradle copies back *before* it
  uninstalls the apks. `getExternalFilesDir` is the obvious answer and the wrong one: an
  app's own storage goes with it when it is uninstalled, so the pictures were written, the
  test passed, and there was nothing left to fetch.
- `scripts/make-screenshot-documents.py` writes the documents in them, into the *test*
  apk's assets, reproducibly. `frame-screenshots.py` draws the frame - a Pixel, from its
  published dimensions - onto a canvas of its own, because play refuses a picture more
  than twice as long as it is wide and a Pixel 9 Pro XL is 2.23:1 before anything is drawn
  around it. `store_screenshots.py` says what a full set is and stages it.
- The underscore in `store_screenshots.py` is not a slip: `frame-screenshots.py` imports
  it, and a dash cannot be imported.

The release runs the two devices on a runner each, checks the halves together, and only
then writes the listing - which is a job behind the bundle upload, so a wedged emulator
costs the release its pictures and not its binary.

## Build

Three flavors, and what separates them is what they *link*:

| | ads + consent sdk | play in-app review | goes to |
|---|---|---|---|
| lite | yes | yes | play, free |
| pro | no | yes | play, paid |
| foss | no | no | the github release, and f-droid |

The three `nonfree/` classes calling those libraries live outside `src/main`: `src/ads`
and `src/review`, with a no-op of the same shape in `src/noAds` and `src/noReview`, and
`app/build.gradle` names two of the four per flavor. The rest of `nonfree/` imports
nothing proprietary and stays in `src/main`. A method added to one copy has to be added
to the other, which `assembleDebug` catches - it builds all three.

Code that has to *ask* reads `Features`, never the flavor name: `Features.withAds` is the
one question anything asks today, and `LINKS_ADS` behind it sits in `src/ads` and
`src/noAds` next to the classes it stands for, so the flag cannot end up in a build whose
code says otherwise. Do not add a `BuildConfig.FLAVOR` comparison back - it was what made
`BillingManager` miss foss - and do not name a flag after a behaviour it only implies. The
resource bool `DISABLE_TRACKING` was both mistakes at once: there is no tracking to
disable, `AnalyticsManager` and `CrashManager` write to logcat and nowhere else.

Those two take no switch at all, which is why `DocumentLoader` just constructs them. Ads
and billing are what `MainActivity.initializeManagers` gates, on `Features.withAds` *and*
`PlayServices` - the device half of the answer, and the reason that method can run twice,
once more after google's own dialog comes back.

foss carries `applicationIdSuffix .foss` so a sideload sits beside a play install;
f-droid strips it, since its listing is `at.tomtasche.reader` and that can never change.

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
  `capabilitiesByFileType(...).translateHtml`, with nothing taken back out. Text, csv,
  images, zip and cfb, fonts, audio and video included.
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
`SupportedDocumentTypes` and the package manager agree, and that every claimed mime type is
one `isRenderedByCore` takes, so a format added upstream and forgotten fails CI.

The tables live in `libodr_jni`, which is why `RenderedByCoreTest` and
`SupportedDocumentTypesTest` are instrumented though neither opens a file. After caching it
is `Odr.mimetype` that decides, canonicalized through `canonicalMimeType` so one spelling per
format reaches the core.

Reading the core's table directly, as `isDocument` does, must not `lowercase()` first: it
matches exactly and spells some types with capitals (`macroEnabled`). Our own sets are the
other way round - `mimeTypesOf` lowercases what it stores.

### There is nothing after `CoreLoader`

The only answer to a file the core cannot open is to say so - `onUnsupported`, the reopen bar
and the contact dialog. Do not add a route around it: a format the app should open is a format
odrcore should learn, and rtf and WordPerfect are on that list. No document leaves the device.

### `text/plain` from the core is a guess unless a charset came with it

Text is the core's fallback for bytes nothing else claims, and it does not refuse the ones
it cannot name a charset for - it answers `text/plain` and throws only once a page is
rendered, on the server thread, long after `CoreLoader` reported success.

So `FileIdentifier` drops a `text/plain` whose file has no charset (`hasKnownCharset`) and
lets the guesses below it decide, and `CoreLoader.host()` refuses the same file up front.
Both are needed: the first keeps `isRenderedByCore` off a `.bin`, the second stops a success
bar appearing over a page that cannot draw.
`LandingTests.aDocumentThatFailsToOpenComesBackToTheList` holds this.

### How the document is displayed is answered over the document, not in the settings

Three of the buttons in `DocumentActions` are about what the page looks like rather than what can
be done to it, and each remembers what it was last told:

- **Night mode** is the app's, through `AppCompatDelegate.setLocalNightMode` rather than the
  default one, so a phone that stays light all day can still be read at night. `NightModeSetting`
  stores no override at all once the choice agrees with the system again, or the app would sit in
  night mode through a morning the phone had long left.
- **Darkening** defaults to `capabilitiesByFileType(...).colorScheme` - whether the format has a
  dark of its own - and is overridden per kind of document, not per file. `CoreLoader` translates
  every page with `HtmlColorScheme.SYSTEM` so both schemes ride behind `prefers-color-scheme`, and
  `PageView.setDarkeningAllowed` picks between them at display time, which is why the button
  renders nothing again. Do not put a list of formats back: it was a guess that presentations and
  images invert badly, and the core answers both.
- **The margins** are odrcore's `textDocumentMargin`, decided while translating, so the button
  renders the document again through `DocumentLoader.reload` - the copy in the cache, not the file.
  `PaginationSetting.affects` gates it on a *text* document: everything else would be translated
  again to look the same. `DocumentFragment` carries the tab and how far down it the reader was
  over to the document that comes back - as a fraction, the margins having changed the height.

Do not move these into a settings screen. `PaginationSetting` keeps its landing row because it
already had one and both write the same preference; the other two never get one.

### Editability comes from the core, never from a mime type

`Document.isEditable()`/`isSavable()` decides whether `DocumentFragment` offers the Edit
button, carried on `LoadedDocument.isEditable`. `CoreLoader.host()` only holds a document
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

### Kotlin, and the two `@Jvm` annotations left

The only java is `com/commonsware/android/print`, vendored so it can be diffed against
upstream. It calls nothing of ours, so no java-to-kotlin call exists and `@JvmStatic`,
`@JvmField`, `@JvmOverloads` and `@Throws` are not needed for interop.

What remains is for runtimes that reflect over the bytecode: `@JvmField` on the `CREATOR`s of
`DocumentRequest`, `IdentifiedFile` and `LoadedDocument` (parcelable needs a static field),
and `@JvmStatic` on `@BeforeClass` / `@AfterClass` in the instrumented tests.
`ProgressDialogFragment` needed `@JvmOverloads` too while it took an argument - a fragment
the framework re-creates has to have a no-arg constructor.
