#!/usr/bin/env bash
#
# Runs the instrumented tests inside reactivecircus/android-emulator-runner.
#
# The action executes its "script:" input line by line, each line in its own
# "sh -c" - so a multi-line if or loop is a syntax error there, and a variable
# does not survive to the next line. That is not cosmetic: "|| status=$?" on one
# line and "exit $status" on another meant gradle's exit code was swallowed and
# an empty "exit" reported a failed test run as a green step. Everything that
# needs shell state lives here instead, behind a one-line invocation.

set -u

# Clear logcat before the tests. Fails with "failed to clear the 'main' log" on
# some system images (api 26 among them) and must not abort the run.
adb logcat -c || true

status=0
# the test run takes about 4 minutes; anything past 20 is hung, and a hang has to
# end as an ordinary failure so the logcat below still gets dumped and uploaded -
# that is the only view into what the guest was doing
timeout --kill-after=1m 20m ./gradlew connectedCheck || status=$?

# Dump logcat after the tests. This used to sit after a bare gradle call, so the
# only runs whose logs were worth uploading were exactly the ones that never got
# here.
adb logcat -d > logcat.txt || true

# a hung run says nothing about which test wedged, so add what the device still
# knows: anrs and tombstones, plus whether our processes are alive
if [ "$status" = 124 ] || [ "$status" = 137 ]; then
  adb shell ps -A > processes.txt 2>&1 || true
  adb shell "cat /data/anr/*trace* 2>/dev/null" > anr-traces.txt || true
  adb shell "ls -l /data/tombstones 2>/dev/null" > tombstones.txt || true
fi

# the step cannot end while the emulator the action started still holds its
# output open, and the action's teardown only sends "adb emu kill" without
# checking that anything came of it. on api 26 and 29 nothing does often enough
# to matter - both jobs of run 30249138427 sat there for 40 minutes after their
# tests had finished, one of them green - so kill it here, hard if the polite
# request is ignored again
adb emu kill || true
for _ in $(seq 20); do
  pgrep -f qemu-system > /dev/null || break
  sleep 1
done
pkill -9 -f qemu-system || true

exit "$status"
