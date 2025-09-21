#!/bin/bash

# Script to run the ODT edit test and pull the edited file before it's cleaned up

echo "Starting ODT edit test and file retrieval..."

# First, build the test APK
echo "Building test APK..."
./gradlew assembleProDebugAndroidTest assembleProDebug

if [ $? -ne 0 ]; then
    echo "✗ Build failed!"
    exit 1
fi

echo "Build complete. Installing and running test..."

# Start a background process to pull the file after a delay
(
    echo "Waiting for test to create the file..."
    # Now we only need to wait for the actual test execution, not the build
    sleep 8

    # Try to pull the file
    echo "Attempting to pull the edited file..."
    /home/tom/snap/android-sdk/platform-tools/adb pull \
        /storage/emulated/0/Android/data/at.tomtasche.reader.pro/files/edited_test_output.odt \
        ./edited_test_output.odt 2>&1

    if [ $? -eq 0 ]; then
        echo "✓ SUCCESS: File pulled to ./edited_test_output.odt"
        ls -lh ./edited_test_output.odt
        echo ""
        echo "You can now open edited_test_output.odt to verify the edits:"
        echo "  - Text at /child:16/child:0 should be: 'Outasdfsdafdline'"
        echo "  - Text at /child:24/child:0 should be: 'Colorasdfasdfasdfed Line'"
        echo "  - Text at /child:6/child:0 should be: 'Text hello world!'"
    else
        echo "✗ FAILED: Could not pull the file. The test may have completed too quickly."
        echo "Try increasing the Thread.sleep() time in the test."
    fi
) &

# Run the test (should be fast since everything is already built)
echo "Running CoreTest.testCoreLibraryEditFormat..."
./gradlew connectedProDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=at.tomtasche.reader.test.CoreTest#testCoreLibraryEditFormat

# Wait for the background process to complete
wait

echo ""
echo "Test and file retrieval complete!"