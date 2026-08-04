#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" \
  "$ROOT/app/src/main/java/com/voidnullvalue/parcelpal/util/CarrierDetector.java" \
  "$ROOT/app/src/main/java/com/voidnullvalue/parcelpal/util/StatusNormalizer.java" \
  "$ROOT/app/src/main/java/com/voidnullvalue/parcelpal/backup/BackupCodec.java" \
  "$ROOT/tools/CoreSelfTest.java"
java -cp "$OUT" CoreSelfTest
