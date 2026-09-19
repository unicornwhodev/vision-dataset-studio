#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
mkdir -p test-results/v4.2
kotlinc tools/qa/V4Reliability.kt app/src/main/java/com/unicornwhodev/visiondatasetstudio/core/workflow/TransferSafety.kt \
  app/src/main/java/com/unicornwhodev/visiondatasetstudio/core/storage/{DurableFiles,DownloadCheckpoint}.kt -include-runtime -d "$OUT/reliability.jar"
java -jar "$OUT/reliability.jar" | tee test-results/v4.2/reliability.txt
