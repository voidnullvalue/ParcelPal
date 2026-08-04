#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path
import json
import xml.etree.ElementTree as ET
root = Path.cwd()
json.loads((root/'app/src/main/assets/sources.json').read_text())
for path in (root/'app/src/main/res').rglob('*.xml'):
    ET.parse(path)
ET.parse(root/'app/src/main/AndroidManifest.xml')
print('XML and JSON validation passed')
PY

if grep -R "com.voidnullvalue.privateparcel" -n "$ROOT/app/src"; then
  echo "Old package namespace remains" >&2
  exit 1
fi

bash "$ROOT/tools/privacy-audit.sh"
bash "$ROOT/tools/core-self-test.sh"

echo "Source validation passed"
