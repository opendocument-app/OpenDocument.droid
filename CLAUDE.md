# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

## Commands

- Build: `./gradlew assembleProDebug` (also `assembleLiteDebug`, `bundleProRelease`,
  `bundleLiteRelease`). `./build-test.sh` builds the pro debug app plus its test apk.
- Test: `./gradlew testProDebugUnitTest` (jvm, no device), `./gradlew connectedAndroidTest`
  (needs a device or emulator).
- Format and lint: `./gradlew spotlessApply` / `spotlessCheck` (google-java-format AOSP for
  java, ktfmt kotlinlang for kotlin) and `./gradlew lintProDebug`. Lint errors fail the
  build; CI runs spotless first.
- Deploy: `fastlane android deployPro` / `deployLite` to the Play internal track.
- `./gradlew clean` also clears the `.cxx` directory.

## Architecture

A document is loaded by a `FileLoader` on `LoaderService`'s background thread and reported
back through `FileLoaderListener`; `LoaderServiceQueue` holds requests until the service is
bound. `MetadataLoader` caches the file and identifies it, `CoreLoader` renders what odrcore
handles and publishes the html on a local http server, `RawLoader` covers text, csv and
images, and `OnlineLoader` uploads to a web viewer what neither can open.

`MainActivity` owns the service binding and the action modes (find, tts, edit), and swaps
between two fragments: `LandingFragment` (recent documents, granted folders, settings) and
`DocumentFragment`, which shows the result in `PageView` - a WebView - with `DocumentActions`
over it.

Source sits under `app/src/main/java/app/opendocument/droid/`: `background/` for the loaders
and stored state, `ui/` for everything on screen, `nonfree/` for analytics, billing and ads.

## Build

Two flavors: **lite** is free with ads and tracking, **pro** is paid with neither. The
switch is a `DISABLE_TRACKING` resource bool set per flavor in `app/build.gradle`, which the
`nonfree/` managers read to disable themselves; `app/src/pro/` holds nothing but a manifest.

Minimum SDK 26, target 36, compile 37 (ahead of target on purpose). AGP 9 / Gradle 9, with
no kotlin plugin applied - AGP brings kotlin itself. Versions live in
`gradle/libs.versions.toml`. R8 and resource shrinking are on for release, the configuration
cache is on, and release signing comes from gradle properties or the environment (see
README) - without them release variants build unsigned rather than failing.

**The version is the git tag.** `app/build.gradle` derives both `versionName` and
`versionCode` from `-Podr.version` (`v4.8.0` -> `4.8.0` / `40800`, two digits per part, a
part above 99 is an error), and defaults to `0.0.0`. Do not put the attributes back into
`AndroidManifest.xml`: gradle's values win in the merge, so a second copy can only ever
disagree with the tag.

### Native side

The app compiles no native code. Conan builds odrcore for armv8, armv7, x86 and x86_64, and
`app/conandeployer.py` drops the `.so` files into `jniLibs/<abi>` and the core's assets into
`assets/core`. Needs NDK 28.2.13676358 and C++20. `conan` comes from PATH, overridable with
`-Podr.conanExecutable=...` or `ODR_CONAN`.

- The conan gradle plugin does not treat `app/conanprofile.txt` as a task input, so after
  editing it `conanInstall-*` stays UP-TO-DATE and the native libs silently keep their old
  settings. Run the conan install by hand locally.
- **Both halves of the JNI interface come out of the one odrcore package**, built with the
  recipe's `with_jni` option: the `app.opendocument.core` classes from
  `share/java/odr-core-java.jar` and the matching `libodr_jni.so`. Keep it that way. Handles
  cross as raw longs and enums as ordinals with no version negotiation, so a separately
  versioned java artifact could drift from the `.so` - and depending on the jar as a file
  rather than through a repository keeps the build credential free, which f-droid and other
  clean source builders need. `CoreLoader` is the only thing wrapping any of it.
- Anything the bindings use must exist on **API 26**, far below what their `--release 17`
  compiler accepts. It fails only at runtime, on device: `java.lang.ref.Cleaner` and
  `List.of` both had to be fixed upstream for this.
- **odrcore's cmake needs a JDK on the conan build machine to produce the jar at all.** Since
  6.1 its `jni/CMakeLists.txt` calls `find_package(Java 11 COMPONENTS Development)` without
  `REQUIRED`, so a build with no `JAVA_HOME`/`javac` quietly returns before `add_jar` and
  `conandeployer.py` then fails on the missing `share/java/odr-core-java.jar`. Give conan a
  JDK and rebuild the package (`--build=odrcore/<version>`) rather than hunting for the file.
  Reported upstream as opendocument-app/OpenDocument.core#637

## Rules that are easy to break

### The package names differ on purpose

`namespace` is `app.opendocument.droid`, `applicationId` is `at.tomtasche.reader` (plus a
`.pro` suffix). Do not "fix" the mismatch.

- `namespace` is only the java/kotlin package plus `R`/`BuildConfig` and is free to rename.
  The keeps in `proguard-rules.txt` are about odrcore's own `app.opendocument.core`, not
  this.
- `applicationId` is the identity on Play and F-Droid and can never change - a new one is a
  new listing that existing installs never update to. Store-facing renaming goes through the
  listing title in `fastlane/metadata/`.
- The `MainActivity` and `CATCH_ALL` / `STRICT_CATCH` component names in the manifest keep
  their `at.tomtasche.reader.*` spelling, as `activity-alias` entries pointing at the
  relocated activity: the OS persists those strings for pinned launcher icons and for
  "always open .odt with this app". The `ComponentName` strings in `MainActivity` must keep
  matching them.
- Whatever reads `getPackageName()` at runtime - the FileProvider authority in
  `AndroidFileCache`, the preferences name in `MainActivity` - follows `applicationId` and
  must stay that way, or upgrading users lose their saved settings.

### Supported file types come from odrcore, and a test keeps the manifest in step

`SupportedDocumentTypes` used to be a hand written table of mime *prefixes*. Since odrcore 6.1
it is derived: `Odr.allFileTypes()` filtered by `Odr.capabilitiesByFileType(...).translateHtml`
and `Odr.fileCategoryByFileType(...) == DOCUMENT` is what `CoreLoader` renders, and
`Odr.mimetypesByFileType` / `Odr.fileExtensionsByFileType` expand each of those into every
spelling the core accepts - the templates, the macro-enabled variants, the
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

The tables live in `libodr_jni`, so anything that reads them needs a device - that is why
`CoreLoaderTest`, `SupportedDocumentTypesTest` and `OnlineLoaderTest` are instrumented tests
and not JVM ones. None of them opens a file, they just cannot ask the table from a plain JVM.

One consequence of claiming every spelling: `MetadataLoader` puts whatever mime type it ended
up with through `SupportedDocumentTypes.canonicalMimeType`, so the loaders behind it see one
per format. `CoreLoader` matches the whole set and does not care, but `RawLoader` routes by
mime type *prefix* - a provider volunteering `application/x-zip-compressed` or
`application/csv` would be offered the app and then told the file is unsupported.
`SupportedFormatsTest.everythingTheAppClaimsIsLoadedBySomebody` holds that: every mime type
the app claims has to reach a loader that takes it.

### Editability comes from the core, never from a mime type

`Document.isEditable()`/`isSavable()` decides whether `DocumentFragment` offers the Edit
button, carried across on `FileLoader.Result.isEditable`. `CoreLoader.host()` only holds a
document open when the core says yes, so having one *is* the answer.

Do not reintroduce a list of editable formats in the UI. The core says no to the legacy
binary formats, to ooxml spreadsheets and presentations, and to every spreadsheet including
odf (its own TODO, the same gap as issue #442) - all of which the app used to spell out by
mime prefix and got wrong the moment a new format started going through `CoreLoader`.

`DecodedFile.capabilities()` is asked first, and only as a shortcut: opening a document costs
a second parse of the whole file, so a format whose declared `edit`/`save` are already false
is never opened just to be told no. It is an upper bound by definition - the document is
still what answers.

### Storage access

The app declares **no storage permission at all**, only `INTERNET`, and it has to stay that
way: `READ_EXTERNAL_STORAGE` has not reached documents since scoped storage, `READ_MEDIA_*`
covers only images, video and audio, and Play restricts `MANAGE_EXTERNAL_STORAGE` to file
managers and backup apps.

Everything goes through SAF instead - `ACTION_OPEN_DOCUMENT` for a single file and
`ACTION_OPEN_DOCUMENT_TREE` (read only, never `FLAG_GRANT_WRITE_URI_PERMISSION`) for the
folders the landing screen browses. `PersistedUriPermissions` persists those grants and
reclaims them by reconciling against the recent list and the granted trees, rather than
releasing on close. Do not add a release next to a `documentFragment.loadUri()`: that call
only queues the load, so the stream is opened long after it returns.

### Kotlin, and the three `@Jvm` annotations left

The only java is `com/commonsware/android/print`, vendored third party code kept in java so
it can still be diffed against upstream. It calls nothing of ours, so no java-to-kotlin call
exists anywhere in the project - `@JvmStatic`, `@JvmField`, `@JvmOverloads` and `@Throws`
are not needed for interop and should not be added back for it.

What remains is there for runtimes that reflect over the bytecode: `@JvmField` on
`FileLoader`'s `CREATOR`s (parcelable requires a static field), `@JvmOverloads` on
`ProgressDialogFragment`'s constructor (the fragment framework re-creates it with no
arguments), and `@JvmStatic` on `@BeforeClass` / `@AfterClass` in the instrumented tests
(junit requires them static).
