#!/usr/bin/env bash
# Cross-platform implementation; see --help for required external fixtures.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
exec python3 "$ROOT/tools/qa/run_device_qualification.py" "$@"
