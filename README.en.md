![Vision Dataset Studio — Unicorn Who Dev](docs/visuals/banner.png)

# Vision Dataset Studio

**From images to datasets, with AI proposals and human decisions.**

An Android workspace for importing images, reviewing model proposals in batches and producing verified dataset exports. Optional local learning keeps the original model available while a separate learned version continues across approved batches.

[Français](README.md) · **English**

[Get started](docs/en/GETTING_STARTED.md) · [Download rc5](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc5) · [Documentation](docs/README.md) · [LiteRT models](https://huggingface.co/Charlbi/Lite_rt_prepared_for_android_dataset_builder)

| Version | Platform | Code licence | Status |
|---|---|---|---|
| **4.2.0-rc5** | Android 9+ · API 28+ | [Apache-2.0](LICENSE) | Qualification prerelease |

> **Installation:** rc5 has a different certificate and cannot update rc4. Preserve existing installations containing data. The 100.2 MB ARM64 candidate is **unsigned**. [Choose an artifact](docs/en/GETTING_STARTED.md#install-the-prerelease).

## The complete batch workflow

**Import → propose → review → export and verify → optionally learn → confirm cleanup.**

| Workspace | Your controls |
|---|---|
| **Sources and batches** | Local or HF source, batch size, resume and persistent image identity |
| **Preannotation** | Explicit model/settings selection; existing annotations change only through an explicit rerun |
| **Human review** | Boxes, points, masks, acceptance/rejection and protected saved corrections |
| **Exports** | Canonical JSONL plus task-specific COCO, YOLO, WebDataset and vision-language projections |
| **Android learning** | Reviewed exported batch, checkpoints, interruption/resume and explicit weight activation |
| **Cleanup** | Confirmation and verified readback; receipts and exact-duplicate history survive cleanup |

Manual annotation works without a model. **The APK contains no model weights**: import or download them separately. Learning is off by default.

## See the application

| rc5 studio · 23 September qualification | Editor · historical 19 September qualification |
|---|---|
| <img src="test-results/windows-rc5-release/api35-16k/start.png" alt="Actual rc5 studio home on an Android emulator" width="205"> | <img src="docs/ui-refined/refined-editor-persisted.png" alt="Actual editor showing an annotation over a synthetic QA image" width="560"> |

Unmodified emulator screenshots. The banner is a generated illustration. [Gallery and provenance](docs/VISUALS.md).

## Preserve the original, continue the learned version

![Original model preserved while a separate learned version progresses](docs/visuals/current/model-lineage.svg)

The first training run creates a separate copy. Later runs restore its latest validated weights, even when the original is still selected for inference. Each attempt has its own checkpoint and receipt; activating learned weights for preannotation remains explicit.

The trained version consists of **both its graph and checkpoint**. Missing or changed checkpoints block continuation. Current supplied conversions train heads or output adapters with frozen visual backbones; a separate synthetic QA network demonstrates updates to internal visual layers. [Lifecycle and evidence](docs/en/MODEL_LINEAGE.md).

## Choose your starting point

| Goal | Start here |
|---|---|
| Produce your first dataset | [Getting started](docs/en/GETTING_STARTED.md) |
| Choose and use a model | [Charlbi conversions](https://huggingface.co/Charlbi/Lite_rt_prepared_for_android_dataset_builder) · [LiteRT contract](docs/en/LITERT_TRAINING_CONTRACT.md) |
| Explore fire/smoke models | [Public FireViewer catalogue](https://huggingface.co/fireviewer/litert-models) — separately qualified |
| Build or contribute | [Developer setup](docs/en/DEVELOPMENT_RESUME.md) · [Architecture](docs/en/ARCHITECTURE.md) · [Contributing](CONTRIBUTING.md) |
| Verify a release | [Validation](docs/en/VALIDATION.md) · [Known limits](docs/en/KNOWN_LIMITATIONS.md) · [rc5 receipt](docs/RC5_PUBLICATION_RECEIPT.json) |

## Recorded validation

| Scope | Recorded result | Evidence boundary |
|---|---|---|
| rc5 Windows build | **67 JVM tests**, generated KSP, no lint errors | 88 lint warnings |
| Python tooling | **70 passed** | 63 at build time plus 7 packaging checks |
| API 35, 4 KB pages | **39/39**, no skips | x86_64 emulator |
| API 35, 16 KB pages | **39/39**, no skips; Flex crash fixed | Three other libraries retain RELRO findings |
| Training continuation | Original unchanged, two consecutive batches, interruption/resume and process-restart persistence | Synthetic data |
| Charlbi model campaign, 22 September | **13/25 passed**, **8 with learning**; 1 timeout, 11 not run | API 28 x86_64, earlier application builds; no ARM benchmark |

[Windows evidence](docs/WINDOWS_QUALIFICATION_2026_09.md) · [Per-model evidence](docs/en/LITERT_QUALIFICATION.md).

Full catalogue coverage, physical ARM, independent-dataset quality, real HF transfers and the new CI remain open. Emulator passes do not establish model accuracy or whole-APK 16 KB compatibility.

## Build locally

Use **JDK 21, Python 3.11+, Android SDK 36 and build-tools 36.0.0**. The bootstrap downloads and verifies Gradle 9.3.1. Prepare the [16 KB Flex runtime](docs/FLEX_16K.md) once, using the verified rc5 ZIP or a Linux/WSL source rebuild:

```powershell
git clone https://github.com/unicornwhodev/vision-dataset-studio.git
cd vision-dataset-studio
python -X utf8 tools/build_android.py
```

Set `JAVA_HOME` and `ANDROID_HOME` for your workstation. Every attempt writes an evidence receipt under `dist/android/runs/`, identifying the APKs actually built. [Setup and Android qualification](docs/en/DEVELOPMENT_RESUME.md).

## Release and next steps

The prerelease contains a **413.9 MB Debug APK**, qualification archive, Flex runtime, an **unsigned 100.2 MB ARM64 candidate** and integrity receipts. GHCR retains private artifact access; GitHub release assets are public.

The [roadmap](docs/en/ROADMAP.md) prioritizes data/device qualification, model coverage and measured quality, then durable signing and transitive notices. The optional local HTTP agent, SAM and Florence-2 still need integration qualification.

## Rights and data protection

Project code is [Apache-2.0](LICENSE). Models, datasets and third-party components retain their own conditions and [attributions](NOTICE). [third_party](third_party/README.md) inventories 111 resolved runtime dependencies; native transitive notice review remains open.

Preserve human corrections, export receipts and original models. Keep credentials, signing keys, weights and user datasets outside Git. [Publication checks](docs/PUBLICATION_CHECKS.md) · [Known limits](docs/en/KNOWN_LIMITATIONS.md).

<sub>Unicorn Who Dev · Android identity: <code>com.unicornwhodev.visiondatasetstudio</code> · Documentation updated 23 September 2026.</sub>
