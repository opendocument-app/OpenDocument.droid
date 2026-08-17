#!/usr/bin/env bash
#
# Prints the newest emulator device profile this runner has for a form factor,
# out of a list written newest first.
#
#   .github/scripts/pick-avd-profile.sh phone
#
# Pinned to one name, a release job fails the day the image catalogue renames or
# retires it - and the answer to "photograph the newest Pixel Pro" is not a name
# that has to be walked forward by hand every autumn. So the newest one the
# runner actually has wins, and a runner that has none of them says which it does
# have rather than letting xcodebuild's android cousin spend twenty minutes not
# finding it.

set -euo pipefail

case "${1:-}" in
  phone)
    candidates="pixel_10_pro_xl pixel_9_pro_xl pixel_8_pro pixel_7_pro pixel_6_pro"
    ;;
  tablet)
    # Google has shipped one tablet and it is still the newest, so this list is
    # short by nature rather than by neglect.
    candidates="pixel_tablet pixel_c"
    ;;
  *)
    echo "usage: $0 phone|tablet" >&2
    exit 2
    ;;
esac

available=$(avdmanager list device -c)

for candidate in $candidates; do
  if grep -qx "$candidate" <<< "$available"; then
    echo "$candidate"
    exit 0
  fi
done

echo "::error::this runner has none of: $candidates" >&2
echo "it has: $(tr '\n' ' ' <<< "$available")" >&2
exit 1
