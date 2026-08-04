#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"

allowed='android.permission.INTERNET|android.permission.CAMERA|android.permission.POST_NOTIFICATIONS'
permissions=$(grep -o 'android.permission.[A-Z_]*' "$MANIFEST" | sort -u || true)
while IFS= read -r permission; do
  [ -z "$permission" ] && continue
  if ! grep -Eq "^($allowed)$" <<<"$permission"; then
    echo "Unexpected permission: $permission" >&2
    exit 1
  fi
done <<<"$permissions"

if grep -RIEq 'firebase|crashlytics|appsflyer|adjust|amplitude|mixpanel|facebook.*sdk|google-analytics|admob|WebView' \
  "$ROOT/app/src/main" "$ROOT/app/build.gradle"; then
  echo "Privacy audit found a forbidden analytics, advertising, or embedded-browser dependency." >&2
  exit 1
fi

if ! grep -q 'usesCleartextTraffic="false"' "$MANIFEST"; then
  echo "Cleartext traffic is not explicitly disabled." >&2
  exit 1
fi
if ! grep -q 'android:allowBackup="false"' "$MANIFEST"; then
  echo "Android cloud backup is not disabled." >&2
  exit 1
fi

echo "Privacy audit passed"
