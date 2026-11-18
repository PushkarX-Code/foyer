#!/usr/bin/env bash
# Collects what we need to tune Foyer for this TV. Changes nothing on the TV.
# Usage: scripts/inspect-tv.sh [tv-ip]
source "$(dirname "$0")/_connect.sh"
OUT="tv-report.txt"
{
  echo "== Model";      $A shell getprop ro.product.manufacturer; $A shell getprop ro.product.model
  echo "== Android";    $A shell getprop ro.build.version.release; $A shell getprop ro.build.fingerprint
  echo "== Memory";     $A shell cat /proc/meminfo | head -3; $A shell getprop ro.config.low_ram
  echo "== Display";    $A shell wm size; $A shell wm density
  echo "== Storage";    $A shell df -h /data
  echo "== Home screens"; $A shell cmd package query-activities --components -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null
  echo "== Biggest memory users"; $A shell dumpsys meminfo | sed -n '/Total PSS by process/,/Total PSS by OOM/p' | head -30
  echo "== Installed packages"; $A shell pm list packages | sort
  echo "== Disabled packages";  $A shell pm list packages -d | sort
} > "$OUT" 2>&1
echo "Saved $OUT. Send this file over so the debloat list can be checked against your exact model."
