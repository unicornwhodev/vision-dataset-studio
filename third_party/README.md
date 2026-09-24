# Runtime dependency notices

Generated on 2026-09-23 UTC from the resolved `releaseRuntimeClasspath`: 109 artifacts,
including DataStore 1.2.1 and the Graphics Path and LiteRT native rebuilds.
`runtime-dependencies.json` records artifact hashes, license declarations and their
POM provenance (including inherited metadata). `NOTICES.runtime.txt` retains the
embedded LICENSE/NOTICE/COPYING/COPYRIGHT text found in the resolved JAR/AAR files.

Flex uses the local `2.16.1-vds16k1` rebuild for Android 16 KB pages. The original
TensorFlow licence and embedded notices are retained alongside `NOTICE.vds-16k`;
the Java classes and 32-bit libraries are unchanged. The source recipe and pinned
inputs are documented in [FLEX_16K.md](../docs/FLEX_16K.md).

Graphics Path uses `1.0.1-vds16k1`. Its pinned AndroidX C++ sources are rebuilt
for ARM64 and x86_64, preserving the official Java classes, resources, 32-bit
libraries and notices. The AAR also contains `NOTICE.vds-16k` identifying the
modification; see [GRAPHICS_PATH_16K.md](../docs/GRAPHICS_PATH_16K.md).

LiteRT uses `2.2.0-vds16k2`: the complete classic Java API and all four JNI ABIs
are built from the same pinned public LiteRT commit. The CPUinfo ARM L2 counting
patch, its BSD licence and modification notice are retained in
[patches](patches/README.md) and in the AAR. See
[LITERT_16K_STATUS.md](../docs/LITERT_16K_STATUS.md) for sources and build inputs.
No Maven licence metadata is missing from this final inventory; this does not
close the native transitive review below.

Regenerate with `tools/export_runtime_dependencies.gradle` and
`tools/collect_dependency_notices.py`; see the Windows qualification document.
No model weights, datasets, private sources or signing material are included.

This inventory does not establish legal clearance. Native libraries can include
additional third-party components whose notices are not recoverable from their
Maven POM. That transitive native review remains open. Preserve the project's
existing LICENSE and NOTICE and the original license terms of each dependency.
