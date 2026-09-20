#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
kotlinc tools/qa/MetadataAnnotation.kt tools/qa/TrainingTargetsRegression.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/core/workflow/{StudioWorkflow,ProcessingSettings}.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/data/model/DatasetModels.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/inference/{MaskCodec,ModelConfig,ModelAdapters}.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/training/TrainingTargets.kt \
 -include-runtime -d "$OUT/training-target-tests.jar"
java -jar "$OUT/training-target-tests.jar"
