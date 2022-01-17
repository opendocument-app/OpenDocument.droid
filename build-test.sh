#!/bin/bash

./gradlew assembleProDebug
./gradlew assembleProDebugAndroidTest

ls app/build/outputs/apk/pro/debug/app-pro-debug.apk
ls app/build/outputs/apk/androidTest/pro/debug/app-pro-debug-androidTest.apk
