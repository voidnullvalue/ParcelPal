#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
BROWSER_SOURCE="$ROOT/app/src/main/java/com/voidnullvalue/parcelpal/source/BrowserSource.java"

allowed='android.permission.INTERNET|android.permission.CAMERA|android.permission.POST_NOTIFICATIONS'
permissions=$(grep -o 'android.permission.[A-Z_]*' "$MANIFEST" | sort -u || true)
while IFS= read -r permission; do
  [ -z "$permission" ] && continue
  if ! grep -Eq "^($allowed)$" <<<"$permission"; then
    echo "Unexpected permission: $permission" >&2
    exit 1
  fi
done <<<"$permissions"

if grep -RIEq 'firebase|crashlytics|appsflyer|adjust|amplitude|mixpanel|facebook.*sdk|google-analytics|admob' \
  "$ROOT/app/src/main" "$ROOT/app/build.gradle"; then
  echo "Privacy audit found a forbidden analytics or advertising dependency." >&2
  exit 1
fi

webview_imports=$(grep -RIl 'android\.webkit\.WebView' "$ROOT/app/src/main/java" || true)
if [ "$webview_imports" != "$BROWSER_SOURCE" ]; then
  echo "WebView use is permitted only in the constrained browser source." >&2
  printf '%s\n' "$webview_imports" >&2
  exit 1
fi

if grep -RIEq 'addJavascriptInterface|setAllowUniversalAccessFromFileURLs\(true\)|setAllowFileAccessFromFileURLs\(true\)' \
  "$ROOT/app/src/main"; then
  echo "Privacy audit found an unsafe browser bridge or file-origin setting." >&2
  exit 1
fi

required_browser_guards=(
  'setAcceptThirdPartyCookies(webView, false)'
  'setAllowFileAccess(false)'
  'setAllowContentAccess(false)'
  'setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)'
  'setJavaScriptCanOpenWindowsAutomatically(false)'
  'hostPolicy.isAllowed'
  'removeAllCookies'
  'evaluateJavascript'
)
for guard in "${required_browser_guards[@]}"; do
  if ! grep -Fq "$guard" "$BROWSER_SOURCE"; then
    echo "Missing browser privacy guard: $guard" >&2
    exit 1
  fi
done

if ! grep -q 'usesCleartextTraffic="false"' "$MANIFEST"; then
  echo "Cleartext traffic is not explicitly disabled." >&2
  exit 1
fi
if ! grep -q 'android:allowBackup="false"' "$MANIFEST"; then
  echo "Android cloud backup is not disabled." >&2
  exit 1
fi

echo "Privacy audit passed"
