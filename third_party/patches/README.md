# Native source patches

`cpuinfo-l2-count.patch` applies to CPUinfo commit
[`ea6b9f1bb6e1001d8b21574d5bc78ddef62e499d`](https://github.com/pytorch/cpuinfo/tree/ea6b9f1bb6e1001d8b21574d5bc78ddef62e499d).
The unchanged upstream copyright and BSD 2-Clause terms are retained in
[LICENSE.cpuinfo](LICENSE.cpuinfo) and in the source-built AAR.

The patch makes ARM L2 cache counting use the same sysfs size as cache population,
and rejects writes beyond the allocated count if topology changes. It addresses
the reproduced null L2-table access in the ARM64 native bridge. XNNPACK and
reported CPU instruction features are preserved.

[config/litert-source.json](../../config/litert-source.json) records the archive,
patch, original file and patched file SHA-256 values. The builder applies the patch
to freshly verified sources; a context or hash mismatch fails the attempt.
See [the runtime recipe](../../docs/LITERT_16K_STATUS.md) and
[qualification evidence](../../docs/NATIVE_FIX_2026_09.md).
