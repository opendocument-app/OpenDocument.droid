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
- **odrcore's cmake needs a JDK on the conan build machine to produce the jar at all.**
  Since 6.1 the android branch of `jni/CMakeLists.txt` calls `find_package(Java 11
  COMPONENTS Development)` without `REQUIRED` - so a build with no `JAVA_HOME`/`javac`
  in the environment quietly builds `libodr_jni.so` and returns before `add_jar`. It is
  not silent for long, `conandeployer.py` then fails with `No such file or directory:
  .../share/java/odr-core-java.jar`, but the fix is to give conan a JDK and rebuild the
  package (`--build=odrcore/<version>`), not to look for the file. Reported upstream as
  opendocument-app/OpenDocument.core#637
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

### Supported file types come from odrcore, and a test keeps the manifest in step

`SupportedDocumentTypes` used to be a hand written table of mime *prefixes*. Since odrcore
6.1 it is derived: `Odr.allFileTypes()` filtered by `Odr.capabilitiesByFileType(...)
.translateHtml` and `Odr.fileCategoryByFileType(...) == DOCUMENT` is what `CoreLoader`
renders, and `Odr.mimetypesByFileType` / `Odr.fileExtensionsByFileType` expand each of those
into every spelling the core accepts - the templates, the macro-enabled variants, the
`application/x-vnd.oasis...` family and the `-flat-xml` ones. Do not put a list of mime
prefixes back; the whole point is that the app cannot claim a format the core does not have,
or miss one it does. A prefix match is also what made the app claim `.xlsb`
(`application/vnd.ms-excel.sheet.binary.macroEnabled.12` starts like every other excel type)
and then fail to open it - the core gives it a file type of its own with an empty capability
row now.

Two declarations are left. The app's own choice of which of the core's formats go to
`CoreLoader` and which to `RawLoader` - text, csv, zip and `image/` are named in
`SupportedDocumentTypes`, because that is an app decision the core knows nothing about. And
the `STRICT_CATCH` `activity-alias` in `AndroidManifest.xml`, which cannot be collapsed into
the first because XML cannot read any of this. Its three intent-filters are *generated* from
the same table (an intent-filter matches a mime type exactly, so all 40 spellings and 41
extensions are written out) and covered by a test rather than by discipline:
`SupportedFormatsTest` (instrumented) walks every mime type of every `FileType` and asserts
that `SupportedDocumentTypes` and the package manager give the same answer, so a format added
upstream and forgotten in the manifest fails CI rather than shipping.

The tables live in `libodr_jni`, so anything that reads them needs a device. That is why
`CoreLoaderTest`, `SupportedDocumentTypesTest` and `OnlineLoaderTest` are instrumented tests
and not JVM ones - none of them opens a file, they just cannot ask the table from a plain
JVM. What the core decides *after* the file is in the cache is unchanged: `MetadataLoader`
runs libmagic over the copy, and `CoreLoader.isDocumentEditable` asks the opened document.

One consequence of claiming every spelling: `MetadataLoader` puts whatever mime type it ended
up with through `SupportedDocumentTypes.canonicalMimeType`, so the loaders behind it see one
per format. `CoreLoader` matches the whole set and does not care, but `RawLoader` routes by
mime type *prefix* - a provider volunteering `application/x-zip-compressed` or
`application/csv` would be offered the app and then told the file is unsupported.
`SupportedFormatsTest.everythingTheAppClaimsIsLoadedBySomebody` is what holds that: every mime
type the app claims has to reach a loader that takes it.

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

### Storage access

The app declares **no storage permission at all** - only `INTERNET` - and it has to stay that way.
`READ_EXTERNAL_STORAGE` has not reached documents since scoped storage, `READ_MEDIA_*` only covers
images/video/audio, and Play restricts `MANAGE_EXTERNAL_STORAGE` to file managers and backup apps,
so a document viewer asking for it gets the listing rejected.

Everything therefore goes through SAF: `ACTION_OPEN_DOCUMENT` for a single file and
`ACTION_OPEN_DOCUMENT_TREE` (read only, never `FLAG_GRANT_WRITE_URI_PERMISSION`) for the folders
the landing screen browses. Those grants have to be persisted to survive a restart -
`PersistedUriPermissions` takes them and reclaims them by reconciling against the recent list and
the granted trees, rather than releasing on close. Do not add a release next to a
`documentFragment.loadUri()`: that call only queues the load onto `LoaderService`, so the stream is
opened long after it returns.

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
