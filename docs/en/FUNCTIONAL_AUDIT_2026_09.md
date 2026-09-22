# Functional audit — September 2026

Models can be installed without a SHA manifest and used when their fingerprint changes. Stored SHA values are provenance only: they do not block import, profile selection, bundle loading or starting training. Compatibility is checked against actual files and tensors. Dataset, export and checkpoint protections remain separate.

[Français](../FUNCTIONAL_AUDIT_2026_09.md) · **English**

This audit exercises the Android application's batch production, corrections, settings, models and optional training. Emulator results establish neither task accuracy nor ARM phone qualification. Model weights remain separate downloads and are never bundled in the APK.

## Changes

- **Inference without training.** Models without training signatures remain importable and usable for their declared tasks. Selecting such a profile disables continuous training; stale preferences do not block cleanup. A raw graph never inherits the previous model’s contract: configure its preprocessing and outputs, or use the contract supplied by the catalog.

- **Stable annotations.** Changing a prompt, model or settings never recalculates existing samples. Workflow resumes and ordinary preannotation skip saved annotations, including imports with stale pending status. Only explicit “Rerun AI” (image) and “Rerun AI proposals” (batch, with confirmation) actions replace unreviewed proposals; human corrections remain protected.
- **Visible suggestions.** The review counter includes points, boxes, masks, tags, captions, visual questions, counts and text-region links. It opens the relevant editor panel directly. Suggestions remain separate from reviewed annotations; rerunning inference preserves human corrections.
- **Projects and batches.** Switching projects clears the previous project's transient information. Deleting a batch retains its number, source cursor and deduplication fingerprints. Resetting is a separate action that explicitly permits reimporting. Active transfers and training protect their source batches.
- **Model settings.** The active model panel exposes the parameters used by its adapter: CPU threads, confidence threshold, output limits, NMS and supported prompts. Overrides belong to the project. TinyCLIP replaces `{label}` with each class; without a marker, it appends the class. Models without a text input reject a prompt instead of silently ignoring it.
- **English.** Application screens, confirmations, errors, diagnostics and descriptions have French and English variants. User content, project names, classes and model responses are not automatically translated. The guidance preference now controls its actual display.
- **Workflows.** All three workflows retain instructions per project and batch. Instructions appear in the editor and reach the optional local planner alongside its system prompt. The planner suggests a template; review, publication and cleanup remain human decisions.
- **Shared reservations.** Reads and commits use the same remote SHA. A conflict requires rereading; malformed reservations and leases owned by another device cannot be silently overwritten.

A caption's declared language is metadata. It does not make an English-only model multilingual. Prompts cannot make a graph trainable without the necessary exported signatures.

## Distinct training scopes

Visual training stays disabled by default. It uses reviewed annotations from the verified exported batch, before cleanup. Rejected samples and other batches are excluded. Failure or cancellation retains images; successful cleanup preserves the learned checkpoint and deduplication history. Activating learned weights remains explicit.

The conversion determines which weights can change. Some signatures train only a head or output adaptation while freezing the encoder. A separate synthetic QA network tests internal visual-layer updates. Its success does not establish encoder training in the HF conversions.

After failure or cancellation, **Models → Training → Abandon** explicitly closes the attempt after confirmation, without activating weights or deleting images. Batch cleanup remains a separate action after export verification. A new attempt can be started explicitly; a previously completed run cannot satisfy the cleanup requirement if the batch export changed.

## Reproduction

`tools/build_android.py` writes APKs and immutable build receipts. `tools/qa/run_device_qualification.sh` checks their hashes before installation and retains instrumentation results. `AnnotationPreservationTest`, `FunctionalUiAuditTest`, `WorkflowExecutionTest`, `LocalEndpointWorkflowTest`, `ProjectMaintenanceTest` and `TrainingWorkflowTest` exercise the relevant workflows.

Opt-in tests remain separate:

- `ConvertedModelQualificationTest`, driven by `tools/qa/qualify_converted_models.py`: pinned conversion, native inference, Android optimizer steps when exposed, changed outputs and checkpoint restoration.
- `NativePhotoInferenceUiTest` with `realPhotoAudit=true`: checksummed public photo, TinyCLIP, changed prompt and top-K, visible proposals, preserved human correction and Room readback.
- `FeatureImplementationAuditTest` with `audit_download_models=true`: public downloads, inference, preannotation and export projections.
- `InferenceOnlyModelTest` with `inferenceOnlyAudit=true`: Android import of MobileNet without a train signature, profile selection, real proposals and cleanup unaffected by a stale training preference.
- `HfLivePublicationTest`: requires authorization for a **new private QA repository** and uses one generated image. It exercises competing reservations, Android publication, remote readback, idempotent resume and cleanup. It refuses existing repositories and does not read the user's credential stored by the application.

Skipped tests due to missing fixtures or authorization are not successes. Receipts retain the actual installed build for every test. A controlled HTTP planner response validates its protocol, not the quality of a real agent.

## rc5 qualification and development results

**Build `20260922T223550Z-3ee0d233110c`: 67 JVM passed; Android 38/39 followed by a successful 1/1 targeted retry, no skips; lint 0 errors / 90 warnings.** The initial import failure came from conversion fixtures consuming the storage budget. Reversibly archiving them allowed the test to pass without changing code. This is not a single green full-suite run. [Complete evidence](../../test-results/functional-audit-20260922/README.md). Model SHA gates are removed; the stale-digest profile passes Android inference.

September 22 tests retain their actual development builds:

| Test | Executed result | Limit |
|---|---|---|
| Build `20260922T211013Z-a83cfea465e4` | 63 JVM tests passed, none failed or skipped; app and instrumentation APKs built | Development build; final rc5 qualification is listed above |
| `FunctionalUiAuditTest`, build `20260922T204312Z-40a01b844d03` | English screens and dialogs, project creation/switching, saved prompt, visible suggestions | API 28 emulator; no TalkBack or physical-phone qualification |
| `NativePhotoInferenceUiTest`, build `20260922T211013Z-a83cfea465e4` | TinyCLIP recognizes cats in a public photo; explicit rerun applies prompt/top-K changes and retains the human correction | Passive preservation of annotations, status and receipts passed; one example is not an accuracy evaluation |
| `FeatureImplementationAuditTest`, build `20260922T203037Z-00cb2a227a15` | 8 tests passed: public models, preannotation, HTTP contract, numerical corrections and export | HTTP fixtures are not a real agent |
| Annotation preservation, build `20260922T211013Z-a83cfea465e4` | 1 record-preservation test and 3 workflow tests passed, including resume after settings changed | Proposals remain unchanged until explicit reprocessing |
| Inference only, same build | MobileNet imported through an Android URI, profile selected, 3 proposals; no train signature or training; stale preference does not block cleanup | Synthetic fixture; no accuracy measurement |
| Android export | Independent ZIP readback: 11 entries, 10 digests, consistent JSONL/COCO/YOLO/TAR | Synthetic QA corpus |
| Inference-only RTMDet, build `20260922T201906Z-8b8b1f4b8b2d` | Native inference with correctly decoded dynamic 40×40, 20×20 and 10×10 outputs | Zero detections on the fixture; no accuracy conclusion |

The new DINOv2 run produces 384 global values and a 256×384 patch map. Inference-only TinyCLIP produces two suggestions and a 512-value representation. Trainable EfficientFormer passes two Android optimizer steps, save, restore and resume. Conversion receipts remain separate from application workflow tests.

The campaign stops here for handoff to the regular workstation: 13 conversions passed, including 8 with training; three emulator timeouts and 15 unexecuted conversions. Trainable HGNetV2 passed inference, learning, save/restore and resume after the initial emulator interruption. Live HF writes await authorization of the dedicated QA repository. Incomplete tests are not counted as successes.

The current personal HF revision `36026262693de56b2cf45a6337a405297bfcfff6` retains all 25 tested manifests. **118 runtime artifacts** were verified identical: 30 current HF LFS digests and 88 downloaded, hashed sidecar files. This explicit equivalence reuses results for identical bytes; it does not qualify unexecuted conversions. [Digest evidence](../../test-results/functional-audit-20260922/public-revision-equivalence.json). Badges do not transfer to other revisions or repository copies.
