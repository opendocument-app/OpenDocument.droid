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
- `./gradlew connectedAndroidTest` - Run instrumented tests on connected device
- `fastlane android tests` - Alternative way to run connected tests

### Linting
- `./gradlew lint` - Run Android lint checks (configured to not abort on errors)

### Deployment
- `fastlane android deployPro` - Deploy Pro version to Google Play internal track
- `fastlane android deployLite` - Deploy Lite version to Google Play internal track

### Clean
- `./gradlew clean` - Clean build artifacts (includes custom .cxx directory cleanup)

## Architecture Overview

### Core Components

**Document Processing Pipeline:**
- `CoreLoader` - Primary document processor using the native C++ ODR core library
- `WvwareDocLoader` - MS Word document processor using wvware library
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
- NDK version 26.3.11579264 required
- C++20 standard

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
- Firebase (Analytics, Crashlytics, Storage, Auth, Remote Config)
- Google Play Services (Ads, Review, User Messaging Platform)

**Document Processing:**
- `app.opendocument:wvware-android` - MS Word document support
- Custom ODR core library via Conan

**Testing:**
- Espresso for UI testing
- JUnit for unit testing
- Test APKs require connected device/emulator

### Configuration Notes

- Minimum SDK: 23, Target SDK: 34
- MultiDex enabled for large dependency set
- R8/ProGuard enabled for release builds with resource shrinking
- Configuration cache enabled for parallel Conan installs
- Custom lint configuration allows non-fatal errors