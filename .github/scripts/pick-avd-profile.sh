#!/usr/bin/env bash
#
# Prints the newest emulator device profile this runner has for a form factor,
# out of a list written newest first.
#
#   .github/scripts/pick-avd-profile.sh phone
#
# Pinned to one name, a release job fails the day the image catalogue renames or
# retires it, and "photograph the newest Pixel Pro" becomes a name to walk
# forward by hand every autumn. So the newest one the runner has wins, and a
# runner with none of them says which it does have.

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

# Where the catalogue is read from. A runner has the sdk without cmdline-tools on
# its path, so `avdmanager` is looked for under it rather than called by name -
# and it is the emulator action, later in the job, that puts one there at all.
avdmanager=$(command -v avdmanager || true)
if [ -z "$avdmanager" ]; then
  for root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
    [ -n "$root" ] || continue
    for found in "$root"/cmdline-tools/*/bin/avdmanager "$root"/tools/bin/avdmanager; do
      if [ -x "$found" ]; then
        avdmanager="$found"
        break 2
      fi
    done
  done
fi

# The newest one, unchecked, rather than no answer at all: this step is here to
# save the job twenty minutes, and a job that cannot run for want of a path is
# the thing it was written to avoid. A name the runner does not have is refused
# by the emulator action a minute later, and says so.
if [ -z "$avdmanager" ]; then
  echo "::warning::no avdmanager to read the device catalogue with - taking the newest name unchecked" >&2
  echo "${candidates%% *}"
  exit 0
fi

available=$("$avdmanager" list device -c)

for candidate in $candidates; do
  if grep -qx "$candidate" <<< "$available"; then
    echo "$candidate"
    exit 0
  fi
done

echo "::error::this runner has none of: $candidates" >&2
echo "it has: $(tr '\n' ' ' <<< "$available")" >&2
exit 1
