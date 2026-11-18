#!/usr/bin/env bash
# Switches the TV's built-in home screen back on.
# Usage: scripts/restore.sh [tv-ip]
source "$(dirname "$0")/_connect.sh"
LIST=$( [ -f .disabled-by-foyer ] && cat .disabled-by-foyer || true )
if [ -z "$LIST" ]; then
  LIST=$($A shell pm list packages -d | tr -d '\r' | sed 's/package://' | grep -iE 'launcher|tvhome|patchwall' || true)
fi
for p in $LIST; do $A shell pm enable "$p" && echo "Switched on: $p"; done
$A shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "Done. Press Home on the remote."
