# Vision Dataset Studio — Unicorn Who Dev

[Français](README.md) · **English**

An Android workspace for producing image datasets in batches: model proposals, human correction and export. A compact dark interface keeps the image at the centre of the work.

**4.2.0-rc2 · Apache-2.0 · Under qualification**
Android application ID: `com.unicornwhodev.visiondatasetstudio`

## Main workflow

**Import → preannotate → correct/review → export and verify → optional learning → clean up → next batch.**

Learning is **off by default**. When enabled, it uses only the corrected, exported batch, and cleanup waits until learning finishes. New weights require manual activation. Manual annotation and export work without a model.

Persistent file and decoded-pixel fingerprints prevent identical copies from entering later batches of the same project. The ledger survives cache cleanup. Edited near-duplicates and lossy recompressions are outside this exact-identity guarantee.

**The APK contains no model weights.** It includes the runtime and integration tools; models are downloaded or imported after installation. Learning runs on Android. The pod is used for development and QA.

## Features and status

| Feature | Status |
|---|---|
| Local / HF import, configurable batches, correction and export | Implemented; local 2+1 cycle verified on Android |
| Persistent identity, resume and protected cleanup | Android tests passed, including renamed copies and concurrent claims |
| Internal-weight learning, checkpoints, cancel/resume | Tested with a synthetic network; trainable HF conversions still need qualification |
| HF download, LiteRT contracts and preprocessing | Implemented; RepViT executed on Android, other families pending |
| Tokenizers and persistent similarity index | Android tests passed |
| TinyCLIP / SAM / Florence-2 bundles | Adapters implemented; conversion-specific qualification incomplete |
| Mask editor/export and RTMDet decoder | Incomplete |
| Autonomous agent and general executable workflows | Not shipped; existing packs configure the workspace |

See [executed validation](docs/en/VALIDATION.md) and [the roadmap](docs/en/ROADMAP.md). This repository shares source code under qualification; the product is not yet complete or ready for a stable release.

## Using the workspace

1. Create a project and choose its tasks, labels, source and batch size.
2. Import a local folder/manifest or configure HF, then index the source.
3. Download/import a model when preannotation is wanted. Check its contract and test one image.
4. Prepare the batch, review proposals, correct annotations and accept or reject each image.
5. Create an archive, choose its destination and verify readback, or publish and verify to an authorized HF destination.
6. If learning is enabled, wait until it finishes. Confirm cleanup and prepare the next batch.

Canonical annotations are retained. COCO, YOLO, WebDataset and vision-language outputs are optional projections with their own constraints; they do not replace canonical JSONL.

## Build and test

Validated build environment: JDK 21, Python 3.11+, Android SDK 36, build-tools 36.0.0 and Gradle 9.3.1. The Python Gradle bootstrap verifies the downloaded distribution; the original archive did not contain a wrapper JAR.

```bash
bash tools/build_android.sh
```

Each attempt creates `dist/android/runs/<id>/status.json`. It records the signatures, identities and hashes of the two APKs actually built. `app-contents.json` checks for bundled weights. A failed attempt is not promoted to a qualified build.

On a dedicated QA device:

```bash
export ANDROID_SERIAL=emulator-5554
export VDS_ALLOW_TEST_INSTALL=1
bash tools/qa/run_device_qualification.sh
```

Model tests need fixtures staged separately and may be skipped when those are absent. Physical ARM devices, performance, API 28/35 CI and real HF writes still require qualification. Emulator results are functional evidence, not device performance measurements.

## Documentation and repository

- [FR/EN documentation index](docs/README.md), [batch production](docs/en/BATCH_PRODUCTION.md), [training contract](docs/en/LITERT_TRAINING_CONTRACT.md).
- [Roadmap](docs/en/ROADMAP.md), [release plan](docs/en/RELEASE_PLAN.md), [contributing](CONTRIBUTING.md).
- [Workstation guide](docs/WORKSTATION.md) and [UI evidence](docs/UI_REFINEMENT.md) are currently French technical references.

Authorized public destination: [unicornwhodev/vision-dataset-studio](https://github.com/unicornwhodev/vision-dataset-studio). APKs belong in GitHub Releases; GHCR is planned for a separately qualified build environment. Publishing the source does not certify the complete product. No package or stable release is claimed to have been published.

Project code uses [Apache-2.0](LICENSE), subject to contributor rights. Models, datasets and dependencies keep their own licences; see [NOTICE](NOTICE) and [licensing status](LICENSING_STATUS.md).

Changing the application ID does not migrate another app’s data. Room migrations 1/2/3 → 4 apply only to the same Android identity. Keep an older installation until its data has a verified backup.
