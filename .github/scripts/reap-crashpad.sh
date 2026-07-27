#!/usr/bin/env bash
#
# Kills the emulator's crash reporter once the emulator it belonged to is gone.
#
# crashpad_handler inherits the stdout of the step that started the emulator, and
# a step cannot finish while anything still holds that pipe open. On api 26 and 29
# the emulator regularly exits without taking it along - the green jobs end on
# "Netsim Wifi ... is gone" with only adb and the gradle daemons left over, the
# hung ones stop at "removeAll" and leave a crashpad_handler - and the runner then
# sits there until the job times out. 45 minutes a go, several times over.
#
# run-instrumented-tests.sh cleans up after itself, but the action can also fail
# before it ever runs the script: an "input keyevent 82" killed during boot ends
# the same way. This runs detached from any step, so it covers that too.

set -u

while true; do
  # only when no emulator is running, and only for a handler whose parent has
  # already died - anything else is a live emulator's reporter and stays
  if ! pgrep -f qemu-system > /dev/null; then
    for pid in $(pgrep -f crashpad_handler); do
      parent=$(ps -o ppid= -p "$pid" 2> /dev/null | tr -d ' ')
      if [ "$parent" = "1" ]; then
        kill -9 "$pid" 2> /dev/null || true
      fi
    done
  fi

  sleep 5
done
