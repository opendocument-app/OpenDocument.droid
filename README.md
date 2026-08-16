# It's Android's first OpenOffice Document Reader! ![](https://github.com/opendocument-app/OpenDocument.droid/actions/workflows/build_test.yml/badge.svg)

This is an Android frontend for our C++ OpenDocument.core library. Feel free to use it in your own project too, but please don't forget to tell us about it!

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/at.tomtasche.reader/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=at.tomtasche.reader)

More information at https://opendocument.app/ and in the app itself.

## Installing

F-Droid and Play are the two stores. Sideloaders take `app-foss-release.apk` from the
[latest release](https://github.com/opendocument-app/OpenDocument.droid/releases/latest),
which is what Obtainium tracks - point it at this repository and it needs no filter, one
apk is all a release carries.

That apk is `at.tomtasche.reader.foss`, and up to 4.13.0 it was `at.tomtasche.reader.pro`.
A different application id is a different app, so the old one neither updates nor complains:
install the new one, uninstall the old one. Nothing carries over and nothing is worth
carrying - a recent documents list whose uri permissions die with the old package anyway.

## Translations

The app speaks nineteen languages and the Play listing fifteen, and both are written
in this repository.

| | source | written by |
|---|---|---|
| the app | `app/src/main/res/values/strings.xml` | `scripts/translate-app.py` |
| the listing | `fastlane/metadata/android/en-US/` | by hand, then translated |
| the release notes | `CHANGELOG.md` | `scripts/store-copy.py` |

Both scripts run one `claude -p` per language and a second one of the same language
reading the draft back, and both refuse an answer that has lost something - a key, a
paragraph, an address the English kept. Neither uploads: `scripts/store-listing.py`
checks and stages what they write, and the release run sends it. See
`fastlane/metadata/README.md` for how the two apps share one listing.

```sh
scripts/translate-app.py                 # fill in whatever strings are missing
scripts/store-copy.py v4.15.0            # write the release notes of a version
scripts/store-listing.py --version v4.15.0   # check every locale has them
```

There is one directory per language and no region qualifier: `values-de` answers for
every German-speaking region and nothing here differs by one. A pull request adding a
language adds it to `LANGUAGES` in `scripts/translate-app.py` too, or the next run
refuses to guess what it should be written in.

This used to be on Crowdin. Nothing came back from it after September 2023 - two years
before the redesign that added most of the app's current strings - so the app shipped
with no language more than 58% translated. Corrections in a pull request are very
welcome; a translated string is a translated string wherever it comes from.

## Setup

A JDK and the android SDK, and that is the whole list - `./gradlew assembleProDebug`
works on a fresh checkout. There is no NDK to install, no python, and no conan: the
app compiles no native code of its own, and odrcore arrives as an ordinary maven
dependency (`app.opendocument:odr-core-android`) carrying both halves of its JNI
bindings, the java classes and a `libodr_jni.so` per ABI.

It resolves from maven central rather than github packages on purpose - github
packages demands authentication even for a public artifact, which no clean source
builder such as f-droid can supply. No credentials are involved anywhere in the
build.

## Release signing

Debug builds need no setup. Release variants are signed only if the credentials are
supplied from outside the repository, as gradle properties in `~/.gradle/gradle.properties`
or as environment variables:

| gradle property        | environment variable     | meaning                          |
|------------------------|--------------------------|----------------------------------|
| `odr.keystore`         | `ODR_KEYSTORE`           | path to the keystore             |
| `odr.keystorePassword` | `ODR_KEYSTORE_PASSWORD`  | store password                   |
| `odr.keyPasswordPro`   | `ODR_KEY_PASSWORD_PRO`   | key password, defaults to store  |
| `odr.keyPasswordLite`  | `ODR_KEY_PASSWORD_LITE`  | key password, defaults to store  |

Without them `bundleProRelease` and friends still build, just unsigned.

## Releasing

The `release` workflow builds both signed bundles and uploads them to the Play Store
internal track - the same thing the fastlane lanes did from a laptop. It is dispatched
by hand, with the version it should build:

```sh
gh workflow run release.yml -f version=v4.14.0
```

Nothing triggers it on a tag. It runs as three jobs:

| job | what it does |
|---|---|
| `build` | one gradle run producing all three signed flavors, archived on the run |
| `upload` | one job per play flavor, handing its bundle and listing to fastlane |
| `record` | once both landed: tag the commit, draft the GitHub release |

Lite and Pro always go out together, and nothing chooses one: they are the same app with
ads and tracking switched off. Foss is built in the same run but uploaded nowhere - it is
the APK on the GitHub release. That is what keeps a version on a single commit - the one
the `v*` tag names and F-Droid builds.

Internal is the only track it uploads to. Anything wider - closed, open, production -
is a promotion in the Play Console, which moves the same bundle and version code that
was tested onto the wider track instead of uploading a second one. It is also where the
review that a production release waits on actually happens, so the workflow finishing is
not the same as the release being out.

The listing goes up with the bundle: the title, both descriptions and the release notes
of that version, in all fifteen locales, for each app. **This overwrites what the Play
Console says**, which is the point - the copy is written here now, not there. The release
notes are no longer typed into the promotion box. Graphics are not uploaded; see
`fastlane/metadata/README.md`.

`fastlane android listingPro` and `listingLite` send the listing without a bundle, which
is how a typo is fixed: Play refuses a version code twice, so repairing the words should
not need a version to carry them.

**If one flavor's upload fails, press "Re-run failed jobs".** Only that upload runs again,
against the bundle already built and signed, and `record` runs behind it. Re-running *all*
jobs is the wrong button: Play refuses a version code it has already accepted, so the half
that made it cannot go up twice. Past the roughly 30 days GitHub offers re-runs for, the
way out is a new patch version for both flavors.

`dry_run` builds and signs both flavors without uploading either. It is the only run
allowed to go without a version, and the only one leaving neither tag nor draft.

In its first seconds the run also refuses a version that has already gone out, one with
no `CHANGELOG.md` section - which is what the release body is made of - and one whose
release notes have not been written in every locale, which `scripts/store-copy.py`
writes. All three are checked before the build, so they cost seconds rather than a
version. `.github/scripts/resolve-version.py`, `changelog-section.py` and
`scripts/store-listing.py` decide them; run any of the three by hand to see what a
dispatch would do.

It needs these repository secrets:

| secret | contents |
|---|---|
| `ODR_KEYSTORE_BASE64` | `base64 -i google_play.keystore` |
| `ODR_KEYSTORE_PASSWORD` | store password |
| `ODR_KEY_PASSWORD_PRO` | key password for the `reader-pro` alias |
| `ODR_KEY_PASSWORD_LITE` | key password for the `reader` alias |
| `GOOGLE_PLAY_SERVICE_ACCOUNT` | play console service account json key |

The service account key goes in as the json file the Play Console hands out, whole and
unedited - base64 of it is accepted too, but nothing else is: the workflow checks it is
a `service_account` key before the build starts rather than letting fastlane trip over
it once the build is done.

Releasing from a laptop still works: `fastlane android deployPro version:v4.8.0` builds
and uploads, and takes an optional `track:` (`... track:beta`). The version can come from
`ODR_VERSION` instead, but it cannot be left out - see below. That reads the key from
`fastlane_google_play.json` in the repository root, as the `Appfile` says.

### Tags

No tag triggers a build, and none is pushed before one. A tag written up front is a
promise the run can fail to keep: `v4.9.0`'s tag push run failed and the upload came
from a dispatched run - the same commit that time, which was luck.

Tags are written afterwards instead, in two kinds:

| tag | who writes it | what it means |
|---|---|---|
| `build/<version>` | the release workflow, once both flavors are up | this commit went to the internal track |
| `v<version>` | publishing the drafted release | this is what shipped |

One build tag, not one per flavor: a single run builds both from a single checkout. A
half uploaded release gets no tag at all, which is the honest answer - nothing yet could
be published from it. A lane run from a laptop leaves none either.

**The `v*` tag is written neither by hand nor by the workflow.** `record` drafts a GitHub
release named `v<version>` at the built commit, carrying the Foss APK - the sideloadable
copy every release up to v4.6 has had, now the flavor that links nothing proprietary -
a `version.json` naming the version and its code, and the version's `CHANGELOG.md`
section above GitHub's generated list of pull requests. A draft creates no tag; publishing it does, at
exactly that commit:

```sh
gh release edit v4.14.0 --draft=false
```

That is the whole manual step, and it waits because internal is not released: promotion to
production, and the review it needs, happen in the Play Console days later. F-Droid tracks
this repository through that release, so publishing any earlier would push a version to
F-Droid users that Google may never release. `version.json` is what it reads: the version
is nowhere in the tree, so `releases/latest/download/version.json` is the only place
F-Droid can learn a version code from.

## Versioning

The version is the release run's `version` input, and no version number is checked in
anywhere. The workflow hands it to gradle as `-Podr.version`, and `app/build.gradle`
derives both halves of it: `v4.8.0` becomes version name `4.8.0` and version code
`40800`, two digits per part. Every part therefore has to stay below 100, which the
build refuses rather than folding `4.100.0` onto the same code as `5.0.0`. Nobody bumps
it anywhere: a commit on `main` is not a release, and no number on `main` can describe
one that already went out.

All three parts have to be spelled out. A two-part `v4.7` used to be padded to `4.7.0`,
which meant one build could be tagged under two names, and the tags older than `v4.8.0`
are in both formats because of it. They are left as they are - a release asset is served
from a URL carrying its tag name, and F-Droid rebuilds old versions from those names -
so the rule only holds for what is tagged from here on.

Builds handed no version - local ones, PR builds, `assembleProDebug` - are `0.0.0`.
Nothing reads it: no code in the app looks at its own version, and only what the release
workflow builds ever leaves the machine. Any build can be given a real one anyway, with
`./gradlew assembleProRelease -Podr.version=v4.8.0`.

Version codes up to 204 were counted by hand in `AndroidManifest.xml`, which is why the
first derived one is a five digit jump. That is one way: the Play Store only ever accepts
a code above the last one it saw.

## License

Mozilla Public License 2.0, in `LICENSE`, replacing the GPL-3.0-or-later this carried
before. MPL is copyleft per *file*: a changed file goes back under MPL, and a larger work
that merely links this can stay under whatever license it likes.

The notice sits in `LICENSE` rather than atop every source file, which Exhibit A of the
license itself allows. Two things keep their own headers because they came from elsewhere
under Apache-2.0 and stay that way: `com/commonsware/android/print` and
`FindActionModeCallback` with the two `webview_find` resources.
