#!/usr/bin/env bash
#
# Opens every document of a corpus on a connected device, one at a time, and
# records what the app made of it: a screenshot, the text the WebView ended up
# showing, and whatever logcat had to say. Meant for the input corpus of
# OpenDocument.core (test/data/input), which is far larger than the handful of
# files in app/src/androidTest/assets and covers formats no test opens.
#
# It only ever looks. Nothing is tapped, which matters: when a load fails the app
# offers to upload the document to the conversion service, and that offer is a
# dialog with a positive button. Photographing it sends nothing. Accepting it
# would send someone's test document to a third party, so this script has no code
# path that taps anything at all.
#
# Usage: tools/render-sweep/render-sweep.sh [--corpus DIR] [--filter REGEX] ...
# See README.md next to this file.

set -u

PKG=at.tomtasche.reader.pro
# the activity-alias, not the relocated class - see the package names section of
# CLAUDE.md for why the component names still read at.tomtasche.reader.*
ALIAS_SUFFIX=at.tomtasche.reader.ui.activity.MainActivity.STRICT_CATCH

REPO_ROOT=$(cd "$(dirname "$0")/../.." && pwd)
# the sibling checkout this repo is normally developed next to
CORPUS=$REPO_ROOT/../OpenDocument.core/test/data/input
OUT=$REPO_ROOT/build/render-sweep
FILTER=
LIMIT=0
INSTALL=0

# every extension the app claims. Kept as a plain list rather than derived,
# because the tables live in libodr_jni and reading them needs a device - the
# same reason SupportedDocumentTypesTest is an instrumented test. A format added
# upstream and missing here just means this sweep skips it.
CLAIMED='csv|doc|docm|docx|dot|dotm|dotx|fodg|fodp|fods|fodt|odg|odm|odp|ods|odt|otg|otm|otp|ots|ott|pdf|pot|potm|potx|pps|ppsm|ppsx|ppt|pptm|pptx|text|txt|xlm|xls|xlsm|xlsx|xlt|xltm|xltx|zip'

usage() {
  cat <<'EOF'
Opens every document of a corpus on a connected device, one at a time, and records
what the app made of it: a screenshot, the text the WebView showed, and logcat.

It only ever looks - nothing is tapped, so the app's offer to upload a document it
could not open is photographed rather than accepted. See README.md next to this file.

Options:
  --corpus DIR    directory to walk (default: ../OpenDocument.core/test/data/input)
  --out DIR       where to write results (default: build/render-sweep)
  --package ID    application id to drive (default: at.tomtasche.reader.pro)
  --filter REGEX  only paths matching this (case-insensitive), e.g. '\.odt$'
  --limit N       stop after N documents
  --install       adb install the pro debug apk before starting
  --help
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --corpus) CORPUS=$2; shift 2 ;;
    --out) OUT=$2; shift 2 ;;
    --package) PKG=$2; shift 2 ;;
    --filter) FILTER=$2; shift 2 ;;
    --limit) LIMIT=$2; shift 2 ;;
    --install) INSTALL=1; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

ADB=${ADB:-}
if [ -z "$ADB" ]; then
  if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
    ADB=$ANDROID_HOME/platform-tools/adb
  else
    ADB=$(command -v adb || true)
  fi
fi
[ -n "$ADB" ] || { echo "adb not found - set ANDROID_HOME or ADB" >&2; exit 1; }

[ -d "$CORPUS" ] || { echo "corpus not found: $CORPUS" >&2; exit 1; }
"$ADB" get-state >/dev/null 2>&1 || { echo "no device (adb get-state failed)" >&2; exit 1; }

if [ "$INSTALL" = 1 ]; then
  APK=$REPO_ROOT/app/build/outputs/apk/pro/debug/app-pro-debug.apk
  [ -f "$APK" ] || { echo "no apk at $APK - run ./gradlew assembleProDebug" >&2; exit 1; }
  "$ADB" install -r -d "$APK" || exit 1
fi
"$ADB" shell pm path "$PKG" >/dev/null 2>&1 || {
  echo "$PKG is not installed - pass --install or install it yourself" >&2; exit 1; }

# wc -c rather than stat, whose flags differ between macos and linux
filesize() { wc -c < "$1" | tr -d ' '; }

mkdir -p "$OUT/shots" "$OUT/logs" "$OUT/ui"
TSV=$OUT/results.tsv
printf 'idx\trepo\trelpath\text\tbytes\tlaunch\tpngbytes\ttextnodes\talive\tsettled\tsignal\n' > "$TSV"

LIST=$OUT/filelist.txt
(cd "$CORPUS" && find . -type f ! -path '*/.git*' | sed 's|^\./||' | sort) > "$LIST"

"$ADB" shell run-as "$PKG" mkdir -p files/sweep >/dev/null 2>&1

i=0
# the list is read on fd 3 because the adb calls in the body would otherwise eat it
while IFS= read -r rel <&3; do
  ext=$(printf '%s' "${rel##*.}" | tr 'A-Z' 'a-z')
  printf '%s' "$ext" | grep -qiE "^($CLAIMED)$" || continue
  [ -n "$FILTER" ] && { printf '%s' "$rel" | grep -qiE "$FILTER" || continue; }
  i=$((i+1))
  [ "$LIMIT" -gt 0 ] && [ "$i" -gt "$LIMIT" ] && { i=$((i-1)); break; }

  idx=$(printf '%03d' $i)
  src=$CORPUS/$rel
  repo=${rel%%/*}
  bytes=$(filesize "$src")
  safe=$(printf '%s' "$rel" | tr '/ $+' '____')

  "$ADB" shell am force-stop "$PKG" >/dev/null 2>&1
  "$ADB" logcat -c -b all >/dev/null 2>&1

  # The app declares only INTERNET, so a file pushed to shared storage is unreadable
  # to it; run-as into its internal storage is the one route that works. It lands as
  # doc.<ext> so names with spaces, '$' or '+' need no quoting or percent-encoding.
  "$ADB" shell run-as "$PKG" sh -c "rm -f files/sweep/doc.*" >/dev/null 2>&1
  "$ADB" shell "run-as $PKG sh -c 'cat > files/sweep/doc.$ext'" < "$src" >/dev/null 2>&1

  # No -t: leaving the mime type off puts the app's own detection (Odr.mimetype, via
  # MetadataLoader) in the path instead of taking the caller's word for it.
  launch=$("$ADB" shell am start -W -a android.intent.action.VIEW \
      -d "file:///data/data/$PKG/files/sweep/doc.$ext" \
      -n "$PKG/$ALIAS_SUFFIX" 2>&1 | grep -E '^Status:' | head -1 | awk '{print $2}')
  [ -z "$launch" ] && launch=nostart

  # A fixed shutter reports slow documents as broken ones: a 5MB .doc still showed
  # "Loading..." at 11s and rendered fine given a minute. So shoot until two frames
  # agree in size to within 1% (the clock ticks, so they are never byte-equal).
  wait=$(( 4 + bytes / 2000000 ))
  [ $wait -gt 12 ] && wait=12
  sleep $wait

  shot=$OUT/shots/$idx-$safe.png
  "$ADB" exec-out screencap -p > "$shot" 2>/dev/null
  pngbytes=$(filesize "$shot" 2>/dev/null || echo 0)
  settled=no
  for _ in 1 2 3 4 5 6 7 8; do
    sleep 6
    "$ADB" exec-out screencap -p > "$shot.next" 2>/dev/null
    nextbytes=$(filesize "$shot.next" 2>/dev/null || echo 0)
    mv -f "$shot.next" "$shot"
    delta=$(( pngbytes > nextbytes ? pngbytes - nextbytes : nextbytes - pngbytes ))
    prev=$pngbytes
    pngbytes=$nextbytes
    [ "$prev" -gt 0 ] && [ $(( delta * 100 )) -le "$prev" ] && { settled=yes; break; }
  done

  # Twice on purpose. A WebView only builds its accessibility tree once
  # something asks for one, so the first dump after a load comes back with no
  # text at all and the second has the document in it.
  "$ADB" shell uiautomator dump /sdcard/render-sweep.xml >/dev/null 2>&1
  "$ADB" shell uiautomator dump /sdcard/render-sweep.xml >/dev/null 2>&1
  "$ADB" shell cat /sdcard/render-sweep.xml 2>/dev/null > "$OUT/ui/$idx.xml"
  textnodes=$(grep -oE 'text="[^"]+"' "$OUT/ui/$idx.xml" 2>/dev/null | wc -l | tr -d ' ')

  alive=$("$ADB" shell pidof "$PKG" >/dev/null 2>&1 && echo yes || echo no)

  { echo "=== crash buffer ==="; "$ADB" logcat -d -b crash 2>/dev/null | tail -60
    echo "=== app errors ==="
    "$ADB" logcat -d 2>/dev/null \
      | awk -v pkg="$PKG" 'BEGIN{IGNORECASE=1} /System\.err|FATAL|OdrException|Fatal signal/ || (/DEBUG/ && index($0, pkg))' \
      | tail -40
  } > "$OUT/logs/$idx.log"

  signal=ok
  # A fatal only counts if it names our process. uiautomator's own launcher logs
  # "D AndroidRuntime" every single iteration, and matching that alone reports
  # every document in the corpus as a crash.
  if grep -qiE "FATAL EXCEPTION|Fatal signal|SIGSEGV" "$OUT/logs/$idx.log" \
     && grep -qi "$PKG" "$OUT/logs/$idx.log"; then signal=CRASH; fi
  # covers a native crash in the core that never reached the java handler
  [ "$alive" = "no" ] && signal=CRASH-died
  # These match the app's own strings in full, not a keyword. Matching "upload" or
  # "password" anywhere in the dump instead flags every *document* that happens to
  # contain the word - the app's own about.odt and changelog fixtures did exactly
  # that, and reported themselves as failures they were not.
  grep -q "Couldn't find file" "$OUT/ui/$idx.xml" && signal=notfound
  grep -q "This document is password-protected" "$OUT/ui/$idx.xml" && signal=encrypted
  grep -q "doesn't seem to be a supported file format" "$OUT/ui/$idx.xml" && signal=upload-offer
  grep -q "Unsupported file format" "$OUT/ui/$idx.xml" && signal=unsupported
  # DocumentFragment's dialog for a format it claimed and then could not render, which is the
  # failure this sweep exists to find - without this such a document is recorded as ok
  grep -q "Couldn't open this file" "$OUT/ui/$idx.xml" && signal=broken-file
  [ "$launch" != "ok" ] && signal=launch-$launch

  # settled=no means the shutter gave up before the screen stopped changing, so a
  # blank or half-drawn page in that row says nothing about the document
  [ "$settled" = "no" ] && [ "$signal" = "ok" ] && signal=still-rendering

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$idx" "$repo" "$rel" "$ext" "$bytes" "$launch" "$pngbytes" "$textnodes" "$alive" \
    "$settled" "$signal" >> "$TSV"
  echo "[$idx] $signal ($textnodes texts) $rel"
done 3< "$LIST"

"$ADB" shell run-as "$PKG" sh -c "rm -f files/sweep/doc.*" >/dev/null 2>&1

# Downscaled copies, purely so a reviewer (or a model) can flip through them
# cheaply. Optional - the full resolution shots are the record.
if command -v sips >/dev/null 2>&1; then
  mkdir -p "$OUT/small" && cp "$OUT/shots/"*.png "$OUT/small/" 2>/dev/null
  sips -Z 640 "$OUT/small/"*.png >/dev/null 2>&1
elif command -v magick >/dev/null 2>&1; then
  mkdir -p "$OUT/small"
  for f in "$OUT/shots/"*.png; do magick "$f" -resize x640 "$OUT/small/$(basename "$f")"; done
fi

echo
echo "$i documents, results in $OUT"
echo "signals:"
awk -F'\t' 'NR>1 {c[$11]++} END {for (s in c) printf "  %-14s %d\n", s, c[s]}' "$TSV" | sort -k2 -rn

# A blank page is not a signal the device reports, so it is left to the reviewer:
# sort by pngbytes and textnodes and look at the small ones first.
echo
echo "least content (look at these first):"
awk -F'\t' 'NR>1 {printf "  %-6s %-8s %s\n", $8" txt", $7" B", $3}' "$TSV" \
  | sort -k1 -n | head -10
