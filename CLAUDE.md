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
- `./gradlew spotlessCheck` - Verify formatting; CI runs this first

### Deployment
- `fastlane android deployPro` - Deploy Pro version to Google Play internal track
- `fastlane android deployLite` - Deploy Lite version to Google Play internal track

### Clean
- `./gradlew clean` - Clean build artifacts (includes custom .cxx directory cleanup)

## Architecture Overview

### Core Components

**Document Processing Pipeline:**
- `CoreLoader` - Primary document processor using the native C++ ODR core library
- `RawLoader` - Plain text and other raw file processor
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
- Uses Conan package manager for C++ dependencies
- The app compiles no native code itself; conan builds odrcore (including its JNI
  bindings) and the deployer drops the resulting `.so` files into `jniLibs`
- NDK version 28.2.13676358 required (for conan's cross build, and for the
  `libc++_shared.so` that gets shipped alongside `libodr_jni.so`)
- C++20 standard
- `conan` is taken from PATH; override with `-Podr.conanExecutable=...` or `ODR_CONAN`.
  Note the conan gradle plugin does not track `app/conanprofile.txt` as a task input, so
  after editing it `conanInstall-*` stays UP-TO-DATE and the native libs keep their old
  settings - run the conan install by hand to pick the change up locally.

**Core Library Integration:**
- The JNI interface comes from odrcore itself, both halves out of the one conan package
  built with the recipe's `with_jni` option: java classes under `app.opendocument.core`
  from `share/java/odr-core-java.jar`, and the matching `libodr_jni.so`. `CoreLoader` is
  the only thing wrapping it.
- Taking both from the same package is deliberate and should stay that way - handles
  cross as raw longs and enums as ordinals with no version negotiation, so a separately
  versioned java artifact could drift from the `.so`. It also keeps the build free of
  credentials, which f-droid and other clean source builders need. `conandeployer.py`
  puts the jar in `build/conan/<arch>/libs` and `app/build.gradle` depends on the armv8
  copy as a file, not through a repository.
- Anything the bindings use must exist on **android API 26**, which is far below what
  their `--release 17` compiler accepts. That is a runtime-only failure, on device
  (`java.lang.ref.Cleaner` and `List.of` both had to be fixed upstream for this reason)
- Supports multiple architectures: armv8, armv7, x86, x86_64
- Assets deployed to `assets/core` and native libraries to `jniLibs/<abi>` via the custom
  Conan deployer (`app/conandeployer.py`)

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
- Custom ODR core library via Conan

**Testing:**
- Espresso for UI testing
- JUnit for unit testing
- Test APKs require connected device/emulator

### Configuration Notes

- Minimum SDK: 26, Target SDK: 36, Compile SDK: 37 (ahead of target on purpose)
- AGP 9 / Gradle 9, versions live in `gradle/libs.versions.toml`
- R8/ProGuard enabled for release builds with resource shrinking
- Configuration cache enabled for parallel Conan installs
- Release signing credentials come from gradle properties or environment variables (see
  README); without them release variants build unsigned rather than failing
- The version is the git tag, not a number in the tree. `AndroidManifest.xml` carries no
  `versionCode`/`versionName`; `app/build.gradle` derives both from `-Podr.version`
  (`v4.8.0` -> name `4.8.0`, code `40800`, two digits per part, parts above 99 are an
  error), and a build handed no version is `0.0.0`. Do not put the attributes back in the
  manifest: gradle's values win in the merged manifest, so a second copy can only ever
  disagree with the tag. The release workflow passes the tag it ran on; a dispatched run
  passes its `version` input

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

### Supported file types are declared twice, and a test keeps them in step

`SupportedDocumentTypes` is the single kotlin table: mime prefixes for what the core
renders, mime prefixes for what `RawLoader` shows, plus an extension fallback for the
`application/octet-stream` a provider volunteers when it knows nothing better.
`CoreLoader.isSupported()` defers to it - it used to keep a second copy of the same
prefixes.

The `STRICT_CATCH` `activity-alias` in `AndroidManifest.xml` is the second declaration and
cannot be collapsed into the first, because XML cannot read a kotlin list. It is covered
instead: `SupportedFormatsTest` (instrumented) walks odrcore's own `FileType` values, asks
`Odr.mimetypeByFileType` for each, and asserts that `SupportedDocumentTypes` and the package
manager give the same answer - so a format added on one side and forgotten on the other
fails CI rather than shipping.

That walk has a blind spot, and a second test covers it. The table matches by *prefix*, so
it also claims the ooxml templates, slideshows and macro-enabled variants - spellings
odrcore never produces, because `mimetypeByFileType` names one canonical mime per format. An
intent-filter, on the other hand, matches a mime type exactly, so every one of those has to
be spelled out in the manifest. `theOoxmlVariantsReachUsToo` lists them and asserts both
sides say yes.

The set follows odrcore: whatever it keeps reference html output for in
`test/data/reference-output` is what the app claims - odf, ooxml, the legacy binary
doc/ppt/xls and pdf. Text, csv and images are the core's too but belong to `RawLoader`,
which only gets its turn when `CoreLoader.isSupported()` says no.

Why the app has a table at all when odrcore has `Odr.fileTypeByMimetype` /
`Odr.fileTypeByFileExtension`: both are native calls into `libodr_jni`, and both know exactly
one canonical mime type per format - no templates, no macro-enabled variants, no
`application/x-vnd.oasis...`, which are the spellings content providers actually hand out.
The guessing stage needs to be broader than the core's own lookup. What the core *does*
decide is everything after the file is in the cache: `MetadataLoader` runs libmagic over the
copy, and `CoreLoader.isDocumentEditable` asks the opened document.

### Editability comes from the core, never from a mime type

`Document.isEditable()`/`isSavable()` is what decides whether `DocumentFragment` puts the
Edit item up, carried across on `FileLoader.Result.isEditable`. `CoreLoader.host()` only
holds a document open when the core says yes, so "we have a document" *is* the answer.

Do not reintroduce a list of editable formats in the UI. The core already says no to the
legacy binary doc/ppt/xls, to ooxml spreadsheets and presentations, and to every spreadsheet
including odf (its own TODO, the same gap as issue #442) - all of which the app used to spell
out by mime prefix and got wrong the moment a new format started going through `CoreLoader`.

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
