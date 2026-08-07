#!/usr/bin/env bash
#
# Checks that every release bundle and apk a build produced is signed.
#
# A release that silently produced an unsigned bundle would be rejected by the
# play store with a much less obvious error, and an unsigned apk on the github
# release would not install at all. Release variants build unsigned rather than
# failing when the credentials are absent (see app/build.gradle), which is what
# makes this worth checking rather than assuming.
#
# Takes the directory to look under, app/build/outputs by default:
#
#   .github/scripts/verify-signed.sh
#
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

bundles=("$outputs"/bundle/*/*.aab)
apks=("$outputs"/apk/*/release/*.apk)

# nullglob turns an unmatched pattern into nothing at all, so the loops below
# would pass an empty build without a word. Verifying nothing has to be the
# failure it is: that is the state a wrong path leaves this in
[ ${#bundles[@]} -gt 0 ] || fail "no bundles under $outputs/bundle - nothing was verified"
[ ${#apks[@]} -gt 0 ] || fail "no apks under $outputs/apk - nothing was verified"

for aab in "${bundles[@]}"; do
    # an aab is not an apk and apksigner cannot read one; a bundle carries the
    # jar signature the store expects, which does show up in the zip listing.
    # read once into a variable rather than piped into grep -q: -q stops at the
    # first match, and the SIGPIPE that leaves unzip with would fail the pipeline
    # under pipefail whenever it had not finished writing yet - which is a signed
    # bundle reported as unsigned, sometimes
    if ! listing=$(unzip -l "$aab" 2>&1); then
        fail "$aab cannot be read as a zip: $listing"
    fi
    if ! grep -qE " META-INF/[^/]+\.(RSA|DSA|EC)$" <<< "$listing"; then
        fail "$aab is not signed"
    fi
    echo "signed: $aab"
done

# the apks take apksigner rather than the same grep: from minSdk 24 up agp leaves
# v1 signing off, so there is no META-INF/*.RSA to find and the signature sits in
# a block the zip listing does not show
apksigner=$(find "${ANDROID_HOME:-}/build-tools" -maxdepth 2 -name apksigner -type f 2>/dev/null | sort -V | tail -1)
[ -n "$apksigner" ] || fail "found no apksigner under ${ANDROID_HOME:-\$ANDROID_HOME}/build-tools"

for apk in "${apks[@]}"; do
    # what apksigner said goes into the message: it exits non-zero both for an
    # unsigned apk and for not being able to run at all, and "not signed" is a
    # bad way to learn that the runner has no java
    if ! output=$("$apksigner" verify "$apk" 2>&1); then
        fail "$apk did not verify: ${output:-apksigner said nothing}"
    fi
    echo "signed: $apk"
done
