#!/usr/bin/env bash
# Install the NDK that gradle/libs.versions.toml pins. Runner images drop NDK versions,
# and AGP then packages the native libraries unstripped with only a warning.

set -euo pipefail

version=$(sed -n 's/^ndk = "\(.*\)"$/\1/p' gradle/libs.versions.toml)
[ -n "$version" ] || { echo "::error::no ndk version in gradle/libs.versions.toml"; exit 1; }

if [ -d "${ANDROID_HOME}/ndk/${version}" ]; then
    echo "ndk ${version} is already installed"
    exit 0
fi
"${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" --install "ndk;${version}" > /dev/null
echo "installed ndk ${version}"
