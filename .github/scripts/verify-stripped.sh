#!/usr/bin/env bash
# Check that the native libraries in release bundles and APKs have no debug info.
# AGP packages them unstripped, with only a warning, when it cannot find its NDK.

set -uo pipefail
shopt -s nullglob

outputs="${1:-app/build/outputs}"

fail() {
    if [ -n "${GITHUB_ACTIONS:-}" ]; then
        echo "::error::$1"
    else
        echo "$1" >&2
    fi
    exit 1
}

archives=("$outputs"/bundle/*/*.aab "$outputs"/apk/*/release/*.apk)
[ ${#archives[@]} -gt 0 ] || fail "no bundles or apks under $outputs - nothing was verified"

scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT

for archive in "${archives[@]}"; do
    rm -rf "${scratch:?}"/*
    unzip -q "$archive" '*.so' -d "$scratch" 2> /dev/null
    libraries=$(find "$scratch" -name '*.so' -type f)
    [ -n "$libraries" ] || fail "$archive has no native libraries"
    while IFS= read -r library; do
        description=$(file -b "$library")
        if [[ "$description" != *", stripped"* ]]; then
            fail "${archive}: ${library#"$scratch"/} is not stripped: $description"
        fi
    done <<< "$libraries"
    echo "stripped: $archive"
done
