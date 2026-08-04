# It's Android's first OpenOffice Document Reader! ![](https://github.com/opendocument-app/OpenDocument.droid/actions/workflows/build_test.yml/badge.svg)

This is an Android frontend for our C++ OpenDocument.core library. Feel free to use it in your own project too, but please don't forget to tell us about it!

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/at.tomtasche.reader/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=at.tomtasche.reader)

More information at https://opendocument.app/ and in the app itself.

## Translations
Please help to translate on the https://crowdin.com/project/opendocument

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
gh workflow run release.yml -f version=v4.14.0 -f uploads=both
```

Nothing triggers it on a tag. Both APKs are archived on the run, and the Pro one -
the sideloadable copy every release up to v4.6 carried - goes onto the GitHub release
page later, when the `v*` tag is written; see [Tags](#tags). Both flavors always go
out together.

Internal is the only track it uploads to. Anything wider - closed, open, production -
is a promotion in the Play Console, which moves the same bundle and version code that
was tested onto the wider track instead of uploading a second one, and is where the
release notes get written. It is also where the review that a production release waits
on actually happens, so the workflow finishing is not the same as the release being out.

`uploads` defaults to `both`. `none` is a dry run: everything gets built, signed and
attached to the run, nothing leaves it. `pro` or `lite` finishes a half uploaded
release - if one of the two lanes fails on its own the run cannot simply be repeated,
since the Play Store refuses a version code it has already accepted, so dispatch it
again for the flavor that did not make it.

`version` is the only place a version comes from, and a run without one has to be a
dry run. `.github/scripts/resolve-version.py` decides what a run builds and refuses
the runs that cannot name a version; run it by hand to see what a dispatch would do.

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

Nothing that builds is triggered by a tag, and no tag is pushed before a build. A tag
written up front is a promise the run can fail to keep: it can die before the upload,
or get only one of the two flavors through, and what reaches the store is then built
from some other commit. `v4.9.0` is the case in point - its tag push run failed and the
upload came from a dispatched run. The same commit that time, which was luck.

Tags are written afterwards instead, in two kinds:

| tag | who writes it | what it means |
|---|---|---|
| `build/<flavor>/<version>` | the release workflow, after each upload | this commit went to the internal track |
| `<version>` | you, once the release is live | this is what shipped |

Per flavor, because the two halves of a half uploaded release get finished from
different commits. A lane run from a laptop leaves no tag, so an upload made by hand is
not recorded.

The version tag stays a human decision because internal is not released: the promotion
to production, and the review it waits on, happen in the Play Console days later. Tag
the build that made it rather than whatever is at the tip of `main`:

```sh
git tag v4.14.0 build/pro/v4.14.0^{} && git push origin v4.14.0
```

That runs `attach-apk`, which puts the Pro APK from that upload's own run onto the
GitHub release - which has to exist already, the way it always has. It refuses a
version tag that does not sit on the commit `build/pro/<version>` names, which is the
one thing that catches the two drifting apart. Run artifacts are kept 90 days, so a
version tag written much later has no APK left to attach.

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
