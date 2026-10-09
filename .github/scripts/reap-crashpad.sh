#!/usr/bin/env bash
# Kill orphaned emulator crash reporters so inherited output pipes cannot hang CI.

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
