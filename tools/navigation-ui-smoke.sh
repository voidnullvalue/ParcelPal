#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.voidnullvalue.parcelpal.debug"
ACTIVITY="com.voidnullvalue.parcelpal.ui.MainActivity"
DETAIL="com.voidnullvalue.parcelpal.ui.ShipmentDetailActivity"
TRACKING="92612999998771000123456789"
XML="/tmp/parcelpal-window.xml"

adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb shell pm clear "$PACKAGE" >/dev/null
adb shell am start -W -n "$PACKAGE/$ACTIVITY" >/dev/null
sleep 3

pull_ui() {
  adb shell uiautomator dump /sdcard/parcelpal-window.xml >/dev/null
  adb pull /sdcard/parcelpal-window.xml "$XML" >/dev/null
}

node_center() {
  local attribute="$1"
  local value="$2"
  pull_ui
  python3 - "$XML" "$attribute" "$value" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, attribute, expected = sys.argv[1:]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    actual = node.attrib.get(attribute, "")
    if actual == expected or (attribute == "resource-id" and actual.endswith(expected)):
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if not match:
            continue
        x1, y1, x2, y2 = map(int, match.groups())
        print(f"{(x1+x2)//2} {(y1+y2)//2}")
        raise SystemExit(0)
raise SystemExit(f"UI node not found: {attribute}={expected}")
PY
}

tap_node() {
  local coordinates
  coordinates="$(node_center "$1" "$2")"
  adb shell input tap $coordinates
  sleep 1
}

assert_node() {
  node_center "$1" "$2" >/dev/null
}

current_activity() {
  adb shell dumpsys activity activities | sed -nE 's/.*mResumedActivity:.* ([^ ]+) .*/\1/p' | head -n 1
}

assert_activity() {
  local expected="$1"
  local current
  current="$(current_activity)"
  echo "Current activity: $current"
  [[ "$current" == *"$expected"* ]] || {
    adb shell dumpsys activity activities | head -n 120
    exit 1
  }
}

tap_node resource-id ":id/addButton"
assert_node resource-id ":id/carrierInput"
assert_node text "Auto-detect"

tap_node resource-id ":id/nameInput"
adb shell input text "NavigationSmoke"
tap_node resource-id ":id/trackingInput"
adb shell input text "$TRACKING"
adb shell input keyevent 4
sleep 1
tap_node text "Add"
sleep 3
assert_activity "$DETAIL"

# Leave while the one-shot initial lookup is still running.
adb shell input keyevent 4
sleep 2
assert_activity "$ACTIVITY"

# USPS browser lookups are bounded at 52 seconds. Waiting longer proves that a delayed
# completion callback cannot recreate or foreground the detail activity.
sleep 58
assert_activity "$ACTIVITY"

adb shell pidof "$PACKAGE" >/dev/null
if adb logcat -d -v brief | grep -A8 -B2 "FATAL EXCEPTION" | grep -q "$PACKAGE"; then
  adb logcat -d -v brief | grep -A20 -B5 "FATAL EXCEPTION"
  exit 1
fi

echo "Navigation smoke test passed"
