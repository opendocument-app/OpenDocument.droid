#!/bin/bash
set -euo pipefail

./gradlew assembleProDebug assembleProDebugAndroidTest

ls app/build/outputs/apk/pro/debug/app-pro-debug.apk
ls app/build/outputs/apk/androidTest/pro/debug/app-pro-debug-androidTest.apk
