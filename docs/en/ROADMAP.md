# Roadmap

[Documentation](../README.md) · [Français](../ROADMAP.md)

Qualification priorities on **23 September 2026**. rc5 is a published Debug prerelease, not a stable release.

| Priority | Recorded progress | Required next evidence |
|---|---|---|
| **P0 · Reproducible build and coherent QA** | Windows build, generated KSP, 67 JVM/70 Python tests, 39/39 Android on both 4 KB and 16 KB; Flex crash fixed | Reproduce on another installation while binding sources, runtime, APKs and receipts |
| **P1 · Data, devices and transfers** | Local 2+1 cycle, tested database backup/readback, injected SAF/HF guards, original/checkpoint preservation | Physical ARM, whole-app 16 KB qualification, real permissions/volumes, authorized HF fault scenarios and remote CI |
| **P2 · Models and quality** | 13 conversion passes, including 8 with learning; RTMDet fixed | 15 unexecuted conversions and 3 timeouts; SAM/Florence integration; independent-dataset accuracy and forgetting |
| **P3 · Durable distribution** | rc5, OCI artifact, Flex runtime, 100.2 MB optimized unsigned ARM64 candidate, 111-dependency inventory | Durable signing, rc4 transition strategy, signed device qualification and native transitive notice review |

Core tests do not replace per-model tests. Current HF conversions retain frozen visual backbones. Exact save/restore does not prove accuracy or generalization. Three other native libraries retain RELRO findings. rc5's Windows certificate cannot update rc4; preserve existing installations containing data.

[Android matrix](../ANDROID_QUALIFICATION.md) · [LiteRT evidence](LITERT_QUALIFICATION.md) · [Limits](KNOWN_LIMITATIONS.md) · [Publication](RELEASE_PLAN.md).
