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

- install conan using pip in a venv
- `conan profile detect --force`
- make sure `conan` is in your $PATH, or point gradle at it with
  `-Podr.conanExecutable=/path/to/venv/bin/conan` (the `ODR_CONAN` environment
  variable works too). The gradle daemon captures its environment at startup, so
  a venv activated afterwards is not visible to it - run `./gradlew --stop` after
  changing $PATH.
- `git submodule update --init --depth 1 conan-odr-index`
- `python conan-odr-index/scripts/conan_export_all_packages.py`
- the java half of odrcore's JNI bindings (`odr-core-java.jar`) needs no setup: it
  ships inside the odrcore conan package that also builds `libodr_jni.so`, and the
  conan deployer puts it in `app/build/conan/armv8/libs`. No credentials are
  involved anywhere in the build.

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

Pushing a `v*` tag runs the `release` workflow, which builds both signed bundles and
uploads them to the Play Store internal track - the same thing the fastlane lanes did
from a laptop. It also builds the signed Pro APK and attaches it to the GitHub release
of that tag, which has to exist already - the workflow does not create one, it fails
instead. That APK is the sideloadable copy every release up to v4.6 carried, and both APKs are
archived on the run itself. Both flavors always go out together. Running the workflow
manually additionally allows picking the track and what to publish.

That last one, `uploads`, defaults to `both`. `none` is a dry run: everything gets
built, signed and attached to the run, nothing leaves it. `pro` or `lite` finishes a
half uploaded release - if one of the two lanes fails on its own the run cannot simply
be repeated, since the Play Store refuses a version code it has already accepted, so
dispatch it again for the flavor that did not make it.

A dispatched run has no tag to take the version from, so it either gets one in the
`version` input or is a dry run; see below. Dispatched on a tag it is the tag that
counts, and the input may only repeat it: the APK a run produces is attached to the
release of the tag it ran on, so a run that built some other version would file it
there under the wrong one.

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

## Versioning

The version is the git tag, and no version number is checked in anywhere. The release
workflow hands the tag it was triggered by to gradle as `-Podr.version`, and
`app/build.gradle` derives both halves of it: `v4.8.0` becomes version name `4.8.0` and
version code `40800`, two digits per part. Every part therefore has to stay below 100,
which the build refuses rather than folding `4.100.0` onto the same code as `5.0.0`.
Nothing has to be raised by hand before tagging, and no number on `main` can describe a
release that already went out.

Builds handed no version - local ones, PR builds, `assembleProDebug` - are `0.0.0`.
Nothing reads it: no code in the app looks at its own version, and only what the release
workflow builds ever leaves the machine. Any build can be given a real one anyway, with
`./gradlew assembleProRelease -Podr.version=v4.8.0`.

Version codes up to 204 were counted by hand in `AndroidManifest.xml`, which is why the
first derived one is a five digit jump. That is one way: the Play Store only ever accepts
a code above the last one it saw.
