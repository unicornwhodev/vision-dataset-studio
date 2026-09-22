#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
command -v kotlinc >/dev/null || { echo "Install the Kotlin JVM CLI and JDK first." >&2; exit 1; }
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
mkdir -p test-results/v4.2
kotlinc tools/qa/MetadataAnnotation.kt tools/qa/PolicyRegression.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/core/geometry/Geometry.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/core/geometry/ImageViewport.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/core/workflow/StudioWorkflow.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/data/model/DatasetModels.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/inference/MaskCodec.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/validation/AnnotationReview.kt \
 app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/export/WebDatasetTarWriter.kt \
 -include-runtime -d "$OUT/policy-tests.jar"
java -jar "$OUT/policy-tests.jar" "$OUT" | tee test-results/v4.2/policy-tests.txt
python3 - "$OUT/policy-fixture.tar" <<'PY'
import sys, tarfile
with tarfile.open(sys.argv[1]) as tf:
    assert tf.getnames() == ['sample.txt','sample.json']
    assert tf.extractfile('sample.txt').read() == b'real archive fixture'
    assert tf.extractfile('sample.json').read() == b'{"validated":true}'
print('PASS independent Python TAR reader: names, payloads and checksums.')
PY
