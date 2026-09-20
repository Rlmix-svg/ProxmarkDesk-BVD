#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
TMP="${TMPDIR:-/tmp}/pmdesk-1.8-tests"
rm -rf "$TMP" && mkdir -p "$TMP"

python tests/SourceRegressionTests.py

javac -d "$TMP" \
  src/org/proxmarkdesk/android/TagInfo.java \
  src/org/proxmarkdesk/android/session/*.java \
  src/org/proxmarkdesk/android/capability/ActionDef.java \
  src/org/proxmarkdesk/android/capability/CardCapability.java \
  src/org/proxmarkdesk/android/capability/ActionRegistry.java \
  tests/ActionRegistryTests.java
java -cp "$TMP" org.proxmarkdesk.android.capability.ActionRegistryTests

rm -rf "$TMP" && mkdir -p "$TMP"
javac -d "$TMP" \
  src/org/proxmarkdesk/android/TagInfo.java \
  src/org/proxmarkdesk/android/AutoInspect.java \
  src/org/proxmarkdesk/android/AutoSniff.java \
  src/org/proxmarkdesk/android/RfidAuth.java \
  src/org/proxmarkdesk/android/session/CommandState.java \
  src/org/proxmarkdesk/android/session/ProgressState.java \
  src/org/proxmarkdesk/android/session/CommandSessionManager.java \
  tests/TagInfoTests.java tests/AutoSniffTests.java tests/AuthTests.java tests/CommandSessionManagerTests.java
java -cp "$TMP" org.proxmarkdesk.android.TagInfoTests >/dev/null
java -cp "$TMP" org.proxmarkdesk.android.AutoSniffTests >/dev/null
java -cp "$TMP" org.proxmarkdesk.android.AuthTests tests/auth-fixtures >/dev/null
java -cp "$TMP" org.proxmarkdesk.android.session.CommandSessionManagerTests >/dev/null

python tests/python_elf_test.py .
python tests/python_tools_test.py .
echo "PASS ProxmarkDesk 1.8 host-safe checks"
