#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
if [[ -f /workspace/toolchains/android-env.sh ]]; then
  source /workspace/toolchains/android-env.sh
fi
case "${1:-build}" in
  build) exec bash tools/build_android.sh ;;
  portable)
    python3 tools/qa/test_identity.py
    python3 tools/qa/test_build_evidence.py
    bash tools/qa/run_policy_tests.sh
    bash tools/qa/run_engine_tests.sh
    bash tools/qa/run_v4_reliability.sh
    python3 tools/qa/test_v4_sqlite.py
    python3 tools/qa/test_export_validator.py
    ;;
  emulator) exec bash tools/workstation/start-android-emulator.sh ;;
  device-tests)
    export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
    export VDS_ALLOW_TEST_INSTALL=1
    exec bash tools/qa/run_device_qualification.sh
    ;;
  *) echo 'Usage: run.sh {build|portable|emulator|device-tests}' >&2; exit 2 ;;
esac
