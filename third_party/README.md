# Runtime dependency notices

Generated on 2026-09-23 from the resolved `releaseRuntimeClasspath`: 110 artifacts, after the DataStore 1.2.1 update.
`runtime-dependencies.json` records artifact hashes, license declarations and their
POM provenance (including inherited metadata). `NOTICES.runtime.txt` retains the
embedded LICENSE/NOTICE/COPYING/COPYRIGHT text found in the resolved JAR/AAR files.

Flex uses the local `2.16.1-vds16k1` rebuild for Android 16 KB pages. The original
TensorFlow licence and embedded notices are retained alongside `NOTICE.vds-16k`;
the Java classes and 32-bit libraries are unchanged. The source recipe and pinned
inputs are documented in [FLEX_16K.md](../docs/FLEX_16K.md).

Regenerate with `tools/export_runtime_dependencies.gradle` and
`tools/collect_dependency_notices.py`; see the Windows qualification document.
No model weights, datasets, private sources or signing material are included.

This inventory does not establish legal clearance. Native libraries can include
additional third-party components whose notices are not recoverable from their
Maven POM. That transitive native review remains open. Preserve the project's
existing LICENSE and NOTICE and the original license terms of each dependency.
