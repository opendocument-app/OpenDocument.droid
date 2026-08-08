#!/usr/bin/env bash
#
# Checks that the release keystore can be opened and that both signing keys can
# actually be used with the passwords supplied for them.
#
# Signing is the very last thing a release build does, so a wrong password
# otherwise surfaces as a signProReleaseBundle failure six minutes in - and
# gradle wraps both failures in the same "Failed to read key <alias> from store",
# which does not say which of the two it was. keytool tells them apart in a
# second: -list proves the store password, and -certreq proves a key password,
# because it needs the private key itself. Neither writes to the keystore.
#
# Takes the same environment variables the build takes (see the "Release signing"
# section in the README), so it can be run against a keystore by hand:
#
#   ODR_KEYSTORE=google_play.keystore ODR_KEYSTORE_PASSWORD=... verify-keystore.sh
#
set -uo pipefail

keystore="${1:-${ODR_KEYSTORE:-}}"
store_password="${ODR_KEYSTORE_PASSWORD:-}"

# the aliases the two flavors are signed with, as app/build.gradle names them
pro_alias="reader-pro"
lite_alias="reader"

fail() {
    if [ -n "${GITHUB_ACTIONS:-}" ]; then
        # ::error:: also annotates the run itself
        echo "::error::$1"
    else
        echo "$1" >&2
    fi
    exit 1
}

[ -n "$keystore" ] || fail "no keystore given: pass one as an argument or set ODR_KEYSTORE"
[ -f "$keystore" ] || fail "$keystore does not exist"
[ -n "$store_password" ] || fail "ODR_KEYSTORE_PASSWORD is not set"

if ! keytool -list -keystore "$keystore" -storepass "$store_password" > /dev/null; then
    fail "ODR_KEYSTORE_PASSWORD does not open the keystore, or ODR_KEYSTORE_BASE64 did not decode to it. A trailing newline in either secret is enough to do this."
fi

# an unset key password falls back to the store one, the same way app/build.gradle
# resolves it
verify_key() {
    local alias="$1" password="${2:-$store_password}"
    if ! keytool -certreq -alias "$alias" -keystore "$keystore" \
            -storepass "$store_password" -keypass "$password" > /dev/null; then
        fail "the $alias key cannot be used - check its key password secret"
    fi
    echo "usable: $alias"
}

verify_key "$pro_alias" "${ODR_KEY_PASSWORD_PRO:-}"
verify_key "$lite_alias" "${ODR_KEY_PASSWORD_LITE:-}"
