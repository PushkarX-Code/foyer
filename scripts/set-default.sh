#!/usr/bin/env bash
# Makes Foyer the only home screen by switching off the TV's built-in one.
# Nothing is deleted: scripts/restore.sh switches it back on.
# Usage: scripts/set-default.sh [tv-ip]
source "$(dirname "$0")/_connect.sh"

$A shell pm path app.foyer.launcher >/dev/null 2>&1 || { echo "Install Foyer on the TV first."; exit 1; }

HOMES=$($A shell cmd package query-activities --components -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
  | tr -d '\r' | cut -d/ -f1 | sort -u | grep -v '^app.foyer.launcher$' || true)
# Keep the TV's emergency home (Settings) and the setup wizard untouched.
CANDIDATES=$(echo "$HOMES" | grep -viE 'settings|setup|fallback' || true)

if [ -z "$CANDIDATES" ]; then
  echo "No other home screen found. Press Home on the remote; Foyer should open."
  exit 0
fi

echo "Home screens found on this TV:"; echo "$CANDIDATES" | sed 's/^/  /'
: > .disabled-by-foyer
for p in $CANDIDATES; do
  read -r -p "Switch off $p? [y/N] " yn
  if [[ "$yn" =~ ^[Yy]$ ]]; then
    $A shell pm disable-user --user 0 "$p" && echo "$p" >> .disabled-by-foyer
  fi
done
$A shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "Done. Press Home on the remote. To undo: scripts/restore.sh"
