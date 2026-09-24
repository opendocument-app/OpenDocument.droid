# OpenDocument Reader for Android ![](https://github.com/opendocument-app/OpenDocument.droid/actions/workflows/build_test.yml/badge.svg)

The Android app of [OpenDocument.core](https://github.com/opendocument-app/OpenDocument.core).
More at https://opendocument.app/.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/at.tomtasche.reader/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=at.tomtasche.reader)
[<img src="assets/badge_obtainium.png"
     alt="Get it on Obtainium"
     height="80">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22at.tomtasche.reader.foss%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fopendocument-app%2FOpenDocument.droid%22%2C%22author%22%3A%22opendocument-app%22%2C%22name%22%3A%22OpenDocument%20Reader%22%7D)

## Editions

| edition | where | ads | edits |
|---|---|---|---|
| Lite | Play, free | yes | inside one paragraph, sheet cells, plain text, highlighter |
| Pro | Play, paid | no | all of Lite, plus new and joined paragraphs, formatting, PDF marks |
| Foss | F-Droid, GitHub release | no | the same as Pro |

Every edition opens every format. Foss is `at.tomtasche.reader.foss`, a different app id, so it
installs beside a Play install and does not update one. Each GitHub release carries one apk,
`app-foss-release.apk`, which Obtainium tracks with no settings changed.

## Building

You need a JDK and the Android SDK. Nothing else: no NDK, no python, no conan. The core arrives
as the maven central artifact `app.opendocument:odr-core-android`, java classes and
`libodr_jni.so` per ABI.

```sh
./gradlew assembleProDebug          # also assembleLiteDebug, assembleFossDebug
./gradlew testProDebugUnitTest      # jvm tests
./gradlew connectedAndroidTest      # device tests
./gradlew spotlessApply lintProDebug
```

### Release signing

Release variants build unsigned unless these are set, as gradle properties in
`~/.gradle/gradle.properties` or as environment variables:

| gradle property        | environment variable     | meaning                         |
|------------------------|--------------------------|---------------------------------|
| `odr.keystore`         | `ODR_KEYSTORE`           | path to the keystore            |
| `odr.keystorePassword` | `ODR_KEYSTORE_PASSWORD`  | store password                  |
| `odr.keyPasswordPro`   | `ODR_KEY_PASSWORD_PRO`   | key password, defaults to store |
| `odr.keyPasswordLite`  | `ODR_KEY_PASSWORD_LITE`  | key password, defaults to store |

### Versioning

No version number is checked in. The release run passes its `version` input to gradle as
`-Podr.version`, and `app/build.gradle` derives `versionName` and `versionCode` from it:
`v4.8.0` becomes `4.8.0` and `40800`. All three parts are required, and each must be below
100. Builds with no version are `0.0.0`. To build a real one locally:

```sh
./gradlew assembleProRelease -Podr.version=v4.8.0
```

## Translations

English plus 19 translations of the app, and 15 locales of the Play listing, all in this
repository.

| | source | translated by |
|---|---|---|
| the app | `app/src/main/res/values/strings.xml` | `scripts/translate-app.py` |
| the listing | `fastlane/metadata/android/en-US/` | by hand |
| the release notes | `CHANGELOG.md` | `scripts/store-copy.py` |

Both scripts run `claude -p` once per language, then a second pass reads the draft back and
rejects an answer that lost a key, a paragraph or an address. Neither script uploads.

```sh
scripts/translate-app.py                     # fill in the missing strings
scripts/store-copy.py v4.15.0                # write the release notes of a version
scripts/store-listing.py --version v4.15.0   # check every locale has them
```

Resource directories carry no region: `values-de` serves every German region. A new app
language is added to `LANGUAGES` in `scripts/translate-app.py`. A new store locale is added
to `LOCALES` in `scripts/store-listing.py`, and the release checks the directories against
that list. See `fastlane/metadata/README.md` for the listing layout.

Corrections in a pull request are welcome.

## Releasing

The `release` workflow is dispatched by hand with the version to build. No tag triggers it.

```sh
gh workflow run release.yml -f version=v4.14.0
```

Before the build it refuses a version that already shipped, a version with no `CHANGELOG.md`
section, and a version with release notes missing in any locale. Then it runs these jobs:

| job | what it does |
|---|---|
| `build` | one gradle run, all three signed flavors |
| `screenshots` | one emulator per device, six screens in fifteen locales |
| `screenshot-set` | joins the two devices' pictures and checks the set |
| `upload` | one job per Play flavor, bundle to the internal track |
| `listing` | after the upload: title, descriptions, release notes, screenshots, per flavor |
| `record` | tags `build/<version>` and drafts the GitHub release |

Lite and Pro always ship together. Foss is built in the same run and attached to the GitHub
release. The `listing` job overwrites the Play Console listing; the console is not the source.
A failed screenshot run costs the release its pictures and nothing else: the listing still
uploads with the text alone. `dry_run` builds and signs without a version and uploads nothing.

**If one flavor's upload fails, press "Re-run failed jobs".** Play refuses a version code it
has already accepted, so re-running all jobs fails on the flavor that made it. After GitHub
stops offering re-runs, ship a new patch version.

Secrets the workflow needs:

| secret | contents |
|---|---|
| `ODR_KEYSTORE_BASE64` | `base64 -i google_play.keystore` |
| `ODR_KEYSTORE_PASSWORD` | store password |
| `ODR_KEY_PASSWORD_PRO` | key password for the `reader-pro` alias |
| `ODR_KEY_PASSWORD_LITE` | key password for the `reader` alias |
| `GOOGLE_PLAY_SERVICE_ACCOUNT` | the Play Console service account json, whole; base64 is accepted too |

### After the upload

The workflow uploads to the internal track only. Wider tracks are promotions of the same
bundle, because a version code can only go up once:

```sh
fastlane android openTestingPro version:v4.17.0    # or openTestingLite
```

Closed testing and production are promoted in the Play Console, and Play reviews them.

### Tags

No tag is written before a run, and none triggers one. `record` writes `build/<version>`
once both flavors are up; a half-uploaded release gets no tag.

Publishing the drafted GitHub release creates the `v<version>` tag, which F-Droid builds
from. Publish it only after Play released the version to production:

```sh
gh release edit v4.14.0 --draft=false
```

The draft carries the Foss apk, the version's `CHANGELOG.md` section, and `version.json`,
which is where F-Droid reads the version code from.

### Fixing the listing

`fastlane android listingPro` and `listingLite` upload the listing without a bundle. With
nothing under `fastlane/framed` they send the text alone, so a typo needs no new version and
no emulator.

### Lanes from a laptop

`fastlane android deployPro version:v4.8.0` (or `deployLite`) builds and uploads the bundle
and its listing, and takes an optional `track:`. It reads the service account key from
`fastlane_google_play.json` in the repository root. The version can also come from
`ODR_VERSION`, but it cannot be left out. A lane writes no tag.

### Screenshots

The release run takes the store screenshots from the build it ships. Nothing is committed.
Six screens on a phone and a tablet in fifteen locales, plus a feature graphic per locale
drawn from the first screenshot. The tablet set fills both tablet slots.

To take them by hand you need one emulator on adb running **Android 15 or newer** and Pillow:

```sh
python3 -m pip install Pillow
bundle exec fastlane android screenshots                         # every locale, phone
ODR_SCREENSHOT_DEVICE=tablet bundle exec fastlane android screenshots
ODR_SCREENSHOT_LANGUAGES=en-US,de-DE bundle exec fastlane android screenshots
```

`ANDROID_SERIAL` picks the device when several are attached. Raw captures land in
`fastlane/screenshots/`, the framed set and feature graphics in `fastlane/framed/`. Running
`scripts/frame-screenshots.py` alone re-frames the captures, so a changed headline in
`fastlane/frames/frames.json` costs seconds. Below API 35 the status bar has a white clock on
a white bar, so `ScreenshotTests` refuses to run there.

`hi-IN`, `ja-JP` and `zh-CN` are set in a system font. On Debian install `fonts-noto-core`
and `fonts-noto-cjk`, or the framing stops and says so.

## License

Mozilla Public License 2.0, in `LICENSE`. MPL is copyleft per file. Two parts came from
elsewhere under Apache-2.0 and keep their own headers: `com/commonsware/android/print`, and
`FindActionModeCallback` with the two `webview_find` resources.
