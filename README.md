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
from a laptop. Running the workflow manually additionally allows picking the flavor,
the track, and a dry run that builds and attaches the bundles without uploading.

It needs these repository secrets:

| secret | contents |
|---|---|
| `ODR_KEYSTORE_BASE64` | `base64 -i google_play.keystore` |
| `ODR_KEYSTORE_PASSWORD` | store password |
| `ODR_KEY_PASSWORD_PRO` | key password for the `reader-pro` alias |
| `ODR_KEY_PASSWORD_LITE` | key password for the `reader` alias |
| `GOOGLE_PLAY_SERVICE_ACCOUNT` | play console service account json key |

Releasing from a laptop still works: `fastlane android deployPro` builds and uploads,
and takes an optional `track:` (`fastlane android deployPro track:beta`).

Remember to raise `versionCode` in `app/src/main/AndroidManifest.xml` before tagging.
