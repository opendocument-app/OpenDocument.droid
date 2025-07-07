# It's Android's first OpenOffice Document Reader! ![](https://github.com/opendocument-app/OpenDocument.droid/actions/workflows/android_main.yml/badge.svg)

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
- `conan remote add odr https://artifactory.opendocument.app/artifactory/api/conan/conan`
- move odr-repository to first index in .conan2/remotes.json
- make sure `conan` is in your $PATH or replace conan-call in `app/build.gradle`
