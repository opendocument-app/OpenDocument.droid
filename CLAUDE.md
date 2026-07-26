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
- CMake build system for native C++ core library (`odr-core`)
- NDK version 28.2.13676358 required
- C++20 standard
- `conan` is taken from PATH; override with `-Podr.conanExecutable=...` or `ODR_CONAN`.
  Note the conan gradle plugin does not track `app/conanprofile.txt` as a task input, so
  after editing it `conanInstall-*` stays UP-TO-DATE and the native libs keep their old
  settings - run the conan install by hand to pick the change up locally.

**Core Library Integration:**
- Native C++ wrapper (`CoreWrapper.cpp`) provides JNI interface
- Supports multiple architectures: armv8, armv7, x86, x86_64
- Assets deployed to `assets/core` directory via custom Conan deployer

### Key Directories

- `app/src/main/java/at/tomtasche/reader/background/` - Document processing services
- `app/src/main/java/at/tomtasche/reader/ui/` - UI components and activities
- `app/src/main/java/at/tomtasche/reader/nonfree/` - Analytics, billing, and ads
- `app/src/main/cpp/` - Native C++ JNI wrapper
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

### Language

Mixed Java and Kotlin; Kotlin support is built into AGP 9, no kotlin plugin is applied.
New code should be Kotlin. When converting an existing class, watch two things:

- `CoreWrapper` and its nested `CoreOptions`/`CoreResult`/`GlobalParams` are read from C++
  through `GetFieldID` in `app/src/main/cpp/core_wrapper.cpp`. Kotlin properties compile to
  private fields with accessors, which breaks those lookups - every field needs `@JvmField`.
  `proguard-rules.txt` keeps these classes for the same reason.
- Java callers spell static helpers as `Foo.bar()`, so converted utility classes need
  `object` plus `@JvmStatic`, and public final fields need `@JvmField`.
