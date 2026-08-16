fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android deployPro

```sh
[bundle exec] fastlane android deployPro
```

Build and upload the Pro version to Google Play

### android deployLite

```sh
[bundle exec] fastlane android deployLite
```

Build and upload the Lite version to Google Play

### android uploadPro

```sh
[bundle exec] fastlane android uploadPro
```

Upload an already built Pro bundle. Used by the release workflow, which builds both flavors with gradle directly, in one job.

### android uploadLite

```sh
[bundle exec] fastlane android uploadLite
```

Upload an already built Lite bundle

### android listingPro

```sh
[bundle exec] fastlane android listingPro
```

Upload the Pro listing on its own, without a bundle - to repair a typo, or a locale that came out wrong, without needing a version to carry it

### android listingLite

```sh
[bundle exec] fastlane android listingLite
```

Upload the Lite listing on its own, without a bundle

### android tests

```sh
[bundle exec] fastlane android tests
```



----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
