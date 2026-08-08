#!/usr/bin/env bash
#
# Kills the emulator's crash reporter once the emulator it belonged to is gone.
#
# crashpad_handler inherits the emulator step's stdout, and a step cannot finish while
# anything still holds that pipe open. On api 26 and 29 the emulator regularly exits
# without taking it along, and the runner then sits there until the job times out.
#
# Detached from any step, because the action can fail before run-instrumented-tests.sh
# (which cleans up after itself) ever runs - an "input keyevent 82" killed during boot.

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
