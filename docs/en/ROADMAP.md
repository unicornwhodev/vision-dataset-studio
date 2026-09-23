# Roadmap

[Documentation](../README.md) · [Français](../ROADMAP.md)

Qualification priorities on **23 September 2026**. rc5 is a published Debug prerelease, not a stable release.

| Priority | Recorded progress | Required next evidence |
|---|---|---|
| **P0 · Reproducible build and coherent QA** | Windows build, generated KSP, 70 JVM/75 Python tests, Debug 39/39 on Honor 4 KB and emulator 16 KB; Flex crash fixed | Reproduce on another installation while binding sources, runtime, APKs and receipts |
| **P1 · Data, devices and transfers** | Local 2+1 cycle, restored Room copy, physical Honor; real ENOSPC, SAF revocation, lost volume, HF interruption/conflict/lost response and interrupted purge checked | Prolonged background operation, physical ARM 16 KB, cloud providers, large multipart transfers, actual legacy user database and remote CI |
| **P2 · Models and quality** | 13 conversion passes, including 8 with learning; RTMDet fixed | 15 unexecuted conversions and 3 timeouts; SAM/Florence integration; independent-dataset accuracy and forgetting |
| **P3 · Durable distribution** | Durable key and second-disk backup verified; actual signed 100.2 MB ARM64 Release on Honor; 110-dependency inventory | Off-machine key backup, full Release-variant suite, publication of the next candidate and native transitive notice review |

Core tests do not replace per-model tests. Current HF conversions retain frozen visual backbones. Exact save/restore does not prove accuracy or generalization. Two native libraries retain explained RELRO findings; this is not whole-APK certification. rc5's Windows certificate cannot update rc4; preserve existing installations containing data. [P1 evidence and limits (FR)](../P1_QUALIFICATION_2026_09.md).

[Android matrix](../ANDROID_QUALIFICATION.md) · [LiteRT evidence](LITERT_QUALIFICATION.md) · [Limits](KNOWN_LIMITATIONS.md) · [Publication](RELEASE_PLAN.md).
