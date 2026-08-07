# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Building
- `./gradlew assembleProDebug` - Build the Pro debug variant
- `./gradlew assembleProDebugAndroidTest` - Build the Pro debug test APK
- `./gradlew assembleLiteDebug` - Build the Lite debug variant
- `./gradlew bundleProRelease` - Build Pro release bundle for Play Store
- `./gradlew bundleLiteRelease` - Build Lite release bundle for Play Store
- `./build-test.sh` - Convenience script to build Pro debug and test APKs

### Testing
- `./gradlew testProDebugUnitTest` - Run JVM unit tests (no device needed)
- `./gradlew connectedAndroidTest` - Run instrumented tests on connected device
- `fastlane android tests` - Alternative way to run connected tests

### Linting and formatting
- `./gradlew lintProDebug` - Run Android lint checks (errors fail the build)
- `./gradlew spotlessApply` - Apply formatting (google-java-format AOSP for java,
  ktfmt kotlinlang style for kotlin)
- `./gradlew spotlessCheck` - Verify formatting; run by its own `format.yml` workflow

### Deployment
- `fastlane android deployPro version:v4.8.0` - Deploy Pro to the Play internal track. The
  version is required: no number is checked in, and a lane handed none errors out rather
  than building 0.0.0
- `fastlane android deployLite version:v4.8.0` - The same for Lite

### Clean
- `./gradlew clean` - Clean build artifacts

## Tools

Two of them, both under `tools/`, both for *looking* at the app rather than testing it -
nothing in either asserts anything or fails a build. Each has a README next to it.

- **`render-sweep`** opens a whole corpus of documents one at a time and records what the app
  made of each: a screenshot, the text the WebView showed, and logcat. For "does this format
  still render", across far more files than the instrumented tests touch.
- **`screen-tour`** walks one build through the six screens that carry its design, then lays
  two builds' screenshots side by side as a PDF. For "what does this change actually look
  like next to main". One tour walks both designs - the steps look for a toolbar item *or* a
  floating button - so it keeps working across a branch that moves things.

Reach for `screen-tour` before hand-driving an emulator with `adb shell input tap`. That is
what it replaced, and the second round of tapping is always the expensive one.

## Architecture Overview

### Core Components

**Document Processing Pipeline:**
- `CoreLoader` - Primary document processor using the native C++ ODR core library
- `RawLoader` - The three the core does not get: csv (which it would render, but line by
  line), plus svg and xml, which it has no file type for
- `OnlineLoader` - Remote document fetcher
- `MetadataLoader` - Document metadata extractor

**Service Architecture:**
- `LoaderService` - Background service managing all document loading operations
- `LoaderServiceQueue` - Queue management for multiple document loading requests
- Document loaders implement `FileLoaderListener` interface for async communication

**UI Architecture:**
- `MainActivity` - Main activity with service binding and menu management
- `DocumentFragment` - Primary document display fragment using WebView
- `PageView` - Custom WebView for document rendering
- Action mode callbacks for edit, find, and TTS functionality

### Build System

**Multi-flavor Android App:**
- **Lite flavor**: Free version with ads and tracking enabled
- **Pro flavor**: Paid version with ads disabled and tracking disabled

**Native Dependencies:**
- None to build. The app compiles no native code, and there is no NDK, no conan and no
  python in the build - `./gradlew assembleProDebug` needs a JDK and the android SDK.
- `libodr_jni.so` and the `libc++_shared.so` it links arrive prebuilt for armv8, armv7,
  x86 and x86_64 inside the `odr-core-android` AAR.

**Core Library Integration:**
- The JNI interface comes from odrcore itself, both halves out of one AAR
  (`app.opendocument:odr-core-android`, versioned in `gradle/libs.versions.toml`): java
  classes under `app.opendocument.core` and the matching `libodr_jni.so`. `CoreLoader`
  is the only thing wrapping it.
- Both halves shipping in one artifact is deliberate and should stay that way - handles
  cross as raw longs and enums as ordinals with no version negotiation, so separately
  versioned java and native artifacts could drift from each other.
- It resolves from **maven central**, not github packages: the latter demands
  authentication even for a public artifact, which f-droid and other clean source
  builders cannot supply.
- Anything the bindings use must exist on **android API 26**, which is far below what
  their `--release 17` compiler accepts. That is a runtime-only failure, on device
  (`java.lang.ref.Cleaner` and `List.of` both had to be fixed upstream for this reason)
- Nothing is unpacked at runtime. `CoreLoader.initializeCore` only sets `TMPDIR`; the
  renderer's css and js are written into the html odrcore produces, and `Odr.mimetype`
  is its own detection, so `GlobalParams.setOdrCoreDataPath` and
  `setLibmagicDatabasePath` are both inert and no longer called.

### Key Directories

- `app/src/main/java/app/opendocument/droid/background/` - Document processing services
- `app/src/main/java/app/opendocument/droid/ui/` - UI components and activities
- `app/src/main/java/app/opendocument/droid/nonfree/` - Analytics, billing, and ads
- `app/src/main/assets/` - HTML templates and fonts for document rendering

### Dependencies

**Core Android:**
- AndroidX libraries (AppCompat, Core, Material, WebKit)
- Google Play Services (Ads, Review, User Messaging Platform)

**Document Processing:**
- odrcore's JNI bindings, as the `app.opendocument:odr-core-android` AAR

**Testing:**
- Espresso for UI testing
- JUnit for unit testing
- Test APKs require connected device/emulator

### Configuration Notes

- Minimum SDK: 26, Target SDK: 36, Compile SDK: 37 (ahead of target on purpose)
- AGP 9 / Gradle 9, versions live in `gradle/libs.versions.toml`
- R8/ProGuard enabled for release builds with resource shrinking
- Configuration cache enabled
- Release signing credentials come from gradle properties or environment variables (see
  README); without them release variants build unsigned rather than failing
- The version is the release run's `version` input, not a number in the tree and not a
  tag. `AndroidManifest.xml` carries no `versionCode`/`versionName`; `app/build.gradle`
  derives both from `-Podr.version` (`v4.8.0` -> name `4.8.0`, code `40800`, two digits
  per part, parts above 99 are an error), and a build handed no version is `0.0.0`. All
  three parts are required: a two-part `v4.7` was once padded to `4.7.0`, which let one
  build carry two names and is why the tags before `v4.8.0` are in two formats. Do not
  put the attributes back in the manifest: gradle's values win in the merged manifest,
  so a second copy can only ever disagree
- Tags are written after a release, never before it, and nothing is triggered by one.
  `release.yml` is dispatch-only, builds both flavors once and tags what went out as
  `build/<version>`; the plain `v<version>` tag appears only when the release it drafted
  is published, which is what lets F-Droid ship it. See the README's "Tags" section

### Package names

`namespace` (`app.opendocument.droid`) and `applicationId` (`at.tomtasche.reader`, plus
the `.pro` suffix) differ on purpose - do not "fix" the mismatch:

- `namespace` is only the java/kotlin package plus `R`/`BuildConfig`, and is free to
  rename. Nothing native is bound to it anymore - the JNI symbols live in odrcore's own
  `app.opendocument.core` package, which the app does not rename - so the keeps in
  `proguard-rules.txt` are about that package, not this one.
- `applicationId` is the identity on Play and F-Droid and can never change: Play has no
  rename path, so a new one is a new listing that existing installs never update to.
  Store-facing renaming goes through the listing title in `fastlane/metadata/`.
- The component names in `AndroidManifest.xml` (`MainActivity` and the `CATCH_ALL` /
  `STRICT_CATCH` aliases) also still read `at.tomtasche.reader.*` on purpose. The OS
  persists those strings for pinned launcher icons and for "always open .odt with this
  app", so they survive as `activity-alias` entries pointing at the relocated activity.
  The `ComponentName` strings in `MainActivity` must keep matching them.
- Anything reading `getPackageName()` at runtime (the FileProvider authority in
  `AndroidFileCache`, the SharedPreferences name in `MainActivity`) follows
  `applicationId` and must stay that way, or existing users lose their saved prefs.

### Supported file types come from odrcore, and a test keeps the manifest in step

`SupportedDocumentTypes` used to be a hand written table of mime *prefixes*. Since odrcore
6.1 it is derived, and it answers two separate questions:

- **what `CoreLoader` renders** (`CORE_FILE_TYPES`): `Odr.allFileTypes()` filtered by
  `Odr.capabilitiesByFileType(...).translateHtml`, minus csv. Since 6.2 that covers text,
  images, zip and cfb, fonts, audio and video - which is why `RawLoader` lost its viewers.
- **what the app claims** (`CLAIMED_FILE_TYPES`): the same filter narrowed to
  `Odr.fileCategoryByFileType(...) == DOCUMENT`, plus text, csv and zip. The manifest mirrors
  this. Keep it narrow - the app plays an mp3 handed to it but does not want it in the share
  sheet.

`Odr.mimetypesByFileType` / `Odr.fileExtensionsByFileType` expand each of those
into every spelling the core accepts - the templates, the macro-enabled variants, the
`application/x-vnd.oasis...` family and the `-flat-xml` ones. Do not put a list of mime
prefixes back; the whole point is that the app cannot claim a format the core does not have,
or miss one it does. A prefix match is also what made the app claim `.xlsb`
(`application/vnd.ms-excel.sheet.binary.macroEnabled.12` starts like every other excel type)
and then fail to open it - the core gives it a file type of its own with an empty capability
row now.

Two declarations are left. The app's own choice of what to *claim*, named in
`SupportedDocumentTypes` as `CLAIMED_FILE_TYPES` because the core knows nothing about it. And
the `STRICT_CATCH` `activity-alias` in `AndroidManifest.xml`, which cannot be collapsed into
the first because XML cannot read any of this. Its three intent-filters are *generated* from
the same table (an intent-filter matches a mime type exactly, so all 49 spellings and 41
extensions are written out) and covered by a test rather than by discipline:
`SupportedFormatsTest` (instrumented) walks every mime type of every `FileType` and asserts
that `SupportedDocumentTypes` and the package manager give the same answer, so a format added
upstream and forgotten in the manifest fails CI rather than shipping.

The tables live in `libodr_jni`, so anything that reads them needs a device. That is why
`CoreLoaderTest`, `SupportedDocumentTypesTest`, `RawLoaderTest` and `OnlineLoaderTest` are
instrumented tests and not JVM ones - none of them opens a file, they just cannot ask the
table from a plain JVM. What the core decides *after* the file is in the cache is unchanged:
`MetadataLoader` runs `Odr.mimetype` over the copy, and `CoreLoader.isDocumentEditable` asks
the opened document.

One consequence of claiming every spelling: `MetadataLoader` puts whatever mime type it ended
up with through `SupportedDocumentTypes.canonicalMimeType`, so the loaders behind it see one
per format. Both loaders match whole sets rather than prefixes now, but a provider
volunteering `application/x-zip-compressed` or `application/csv` still has to reach one of
them. `SupportedFormatsTest.everythingTheAppClaimsIsLoadedBySomebody` is what holds that:
every mime type the app claims has to reach a loader that takes it.

One catch when reading the table directly, as `isDocument` does: the core matches mime types
*exactly* and spells some with capitals (`macroEnabled`), so do not `lowercase()` first. The
lookups against our own sets are the other way round - `mimeTypesOf` lowercases what it stores.

### `RawLoader` is asked before `CoreLoader`, not after it

It used to be the fallback at the end of the chain, reached only when the core threw. Once
odrcore learned to render text, images, zip and media the core stopped throwing, and the
PhotoSwipe, Plyr, JSZip and csv-to-html-table viewers in `assets/` became unreachable without
anyone noticing. The first four are gone; the csv one was worth keeping.

So `LoaderService.onSuccess` asks `rawLoader.isSupported` *first*.
`SupportedDocumentTypes.isRenderedByRaw` is the whole list: csv (the only case where the
order matters, since `CORE_FILE_TYPES` excludes it), plus svg and xml, which have no odrcore
file type at all. A `RawLoader` failure falls through to the core; everything the core cannot
open goes to the upload offer rather than being renamed and handed to the WebView on spec.

Routing by name is guarded - see `nameSays`. The core identifies by content, so a `report.csv`
holding an odt is an odt, and only a file it did not recognize (or called plain text) may be
routed by its extension.

### Editability comes from the core, never from a mime type

`Document.isEditable()`/`isSavable()` is what decides whether `DocumentFragment` puts the
Edit item up, carried across on `FileLoader.Result.isEditable`. `CoreLoader.host()` only
holds a document open when the core says yes, so "we have a document" *is* the answer.

Do not reintroduce a list of editable formats in the UI. The core already says no to the
legacy binary doc/ppt/xls, to ooxml spreadsheets and presentations, and to every spreadsheet
including odf (its own TODO, the same gap as issue #442) - all of which the app used to spell
out by mime prefix and got wrong the moment a new format started going through `CoreLoader`.

`DecodedFile.capabilities()` is asked first, and only as a shortcut: opening a document costs
a second parse of the whole file, so a format whose declared `edit`/`save` are already false
is never opened just to be told no. It is an upper bound by definition - the document is
still what answers.

### Language

Kotlin; support is built into AGP 9, no kotlin plugin is applied. The only java left is
`com/commonsware/android/print`, which is vendored third party code with its own copyright
header and stays java so it can still be diffed against upstream. It calls nothing of ours,
so there is no java-to-kotlin call anywhere in the project.

That means `@JvmStatic`, `@JvmField`, `@JvmOverloads` and `@Throws` are not needed for
interop and should not be added back for it. What is left of them is there for a runtime
that reflects over the bytecode, and each one says so:

- `@JvmField` on the `CREATOR`s in `FileLoader`, which the parcelable contract requires to
  be a static field.
- `@JvmOverloads` on `ProgressDialogFragment`'s constructor, so the no-arg constructor the
  fragment framework re-creates it with exists.
- `@JvmStatic` on the `@BeforeClass` / `@AfterClass` methods in the instrumented tests,
  which JUnit requires to be static.
