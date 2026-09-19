#!/usr/bin/env bash
# Use a dedicated test device/emulator. No adb clear, uninstall, or HF production operation.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL explicitly after adb devices}"
: "${VDS_ALLOW_TEST_INSTALL:?Set VDS_ALLOW_TEST_INSTALL=1 to permit test APK installation}"
[[ "$VDS_ALLOW_TEST_INSTALL" == 1 ]] || exit 2
command -v adb >/dev/null
# Reject stale/tampered APKs using the receipt for the most recent build attempt.
APK_LIST="$(python3 tools/qa/resolve_apks.py)"
[[ "$APK_LIST" == *$'\n'* ]] || { echo 'Two verified APK paths are required'; exit 2; }
APK="${APK_LIST%%$'\n'*}"
TEST_APK="${APK_LIST#*$'\n'}"
[[ -n "$APK" && -n "$TEST_APK" && "$TEST_APK" != *$'\n'* ]] || exit 2
OUT="${VDS_EVIDENCE_DIR:-test-results/device-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT"
collect_failure_evidence() {
  code=$?
  adb -s "$ANDROID_SERIAL" logcat -d -b crash >"$OUT/crash-buffer.txt" 2>&1 || true
  printf '%s\n' "$code" >"$OUT/exit-code.txt"
}
trap collect_failure_evidence EXIT
adb -s "$ANDROID_SERIAL" get-state
# Existing app data is not erased. Signature mismatch is a deliberate stop: never uninstall to bypass it.
adb -s "$ANDROID_SERIAL" install -r "$APK" | tee "$OUT/install-main.txt"
adb -s "$ANDROID_SERIAL" install -r "$TEST_APK" | tee "$OUT/install-test.txt"
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
 com.unicornwhodev.visiondatasetstudio.test/androidx.test.runner.AndroidJUnitRunner | tee "$OUT/instrumentation.txt"
# adb may return zero even when the instrumentation itself failed.
python3 - "$OUT/instrumentation.txt" <<'CHECK'
import re,sys
text=open(sys.argv[1]).read()
assert re.search(r'OK \(\d+ tests?\)',text), 'No JUnit success result; inspect instrumentation report'
assert not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=',text), 'Instrumentation failed'
CHECK
adb -s "$ANDROID_SERIAL" shell am start -W -n com.unicornwhodev.visiondatasetstudio/com.unicornwhodev.visiondatasetstudio.MainActivity >"$OUT/start.txt"
adb -s "$ANDROID_SERIAL" exec-out screencap -p >"$OUT/start.png"
adb -s "$ANDROID_SERIAL" shell dumpsys meminfo com.unicornwhodev.visiondatasetstudio >"$OUT/start-meminfo.txt"
adb -s "$ANDROID_SERIAL" shell dumpsys gfxinfo com.unicornwhodev.visiondatasetstudio >"$OUT/start-gfxinfo.txt"
printf 'Automated checks finished. Manual local/HF/physical storage/device tests remain: docs/ANDROID_QUALIFICATION.md\n'
