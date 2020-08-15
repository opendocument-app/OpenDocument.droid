#!/bin/bash

./gradlew assembleDebug
./gradlew assembleDebugAndroidTest

ls app/build/outputs/apk/debug/app-debug.apk
ls app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
