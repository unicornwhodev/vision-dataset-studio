#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)";cd "$ROOT"
OUT="$(mktemp -d)";trap 'rm -rf "$OUT"' EXIT
mkdir -p test-results/v4.2
kotlinc tools/qa/MetadataAnnotation.kt tools/qa/EngineRegression.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/core/workflow/{StudioWorkflow,ProcessingSettings}.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/data/model/DatasetModels.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/validation/AnnotationReview.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/inference/{MaskCodec,ModelConfig,ModelAdapters,TensorCodec,ProposalMerger,AdaptiveCorrection,ModelPresets,StudioPack}.kt \
 -include-runtime -d "$OUT/engine-tests.jar"
java -jar "$OUT/engine-tests.jar" | tee test-results/v4.2/engine-tests.txt
