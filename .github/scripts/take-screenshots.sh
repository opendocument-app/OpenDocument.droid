#!/usr/bin/env bash
#
# Takes the store screenshots inside reactivecircus/android-emulator-runner.
#
# The action executes its "script:" input line by line, each line in its own
# "sh -c" - so a multi-line if or loop is a syntax error there, and a variable
# does not survive to the next line. Everything that needs shell state lives
# here instead, behind a one-line invocation. Same arrangement, and the same
# reason, as run-instrumented-tests.sh next to it.

set -u

adb logcat -c || true

status=0
# One device in fifteen languages is ninety launches, each opening a document the
# core has to translate first; an hour is the honest budget and two is a wedged
# emulator. It has to end as an ordinary failure so the logcat below is still
# dumped and uploaded - that is the only view into what the guest was doing.
timeout --kill-after=1m 120m bundle exec fastlane android screenshots || status=$?

adb logcat -d > logcat.txt || true

if [ "$status" = 124 ] || [ "$status" = 137 ]; then
  adb shell ps -A > processes.txt 2>&1 || true
  adb shell "cat /data/anr/*trace* 2>/dev/null" > anr-traces.txt || true
fi

# Nothing gets to outlive the run: the action's teardown is one "adb emu kill"
# with no check that anything came of it, and a step cannot end while anything
# the action started still holds its stdout open. See run-instrumented-tests.sh.
adb emu kill || true
for _ in $(seq 20); do
  pgrep -f qemu-system > /dev/null || break
  sleep 1
done
pkill -9 -f qemu-system || true
pkill -9 -f crashpad_handler || true

exit "$status"
