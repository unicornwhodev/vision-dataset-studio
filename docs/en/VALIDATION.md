# Workstation handoff — 23 September 2026

Compiled source `a0d9737`, build `20260922T223550Z-3ee0d233110c`: **67 JVM and 52 Python tests passed**, lint **0 errors / 90 warnings**; 158 compiled files match the commit. The APK contains no model weights.

**Android: 38/39 passed, then the remaining test passed in a targeted retry (1/1), no skips.** Inference-only import initially hit the storage budget consumed by conversion fixtures. Reversibly moving those fixtures out of the working directory allowed it to pass without code changes, data deletion or reinstallation. Both logs are retained; no single green full-suite run is claimed for this build.

Explicit abandonment, starting a fresh attempt and refusing cleanup based on an obsolete export pass in `TrainingWorkflowTest`. Its 88 accepted images exclude 8 rejections and the neighboring batch, pause after 9 steps and resume to 198. Bundles accept minimal manifests and stale digests while requiring their actual runtime files.

[Evidence](../../test-results/functional-audit-20260922/README.md) · [Resume development](DEVELOPMENT_RESUME.md). Models have 13 passes, including 8 with learning; three timeouts and 15 unexecuted conversions. rc5 source is pushed; the published distribution remains rc4.

---

# rc5 qualification — 22 September 2026 UTC

Build `20260922T214516Z-3f844ff6cb1e` (versionCode 10): **66 JVM and 39 Android tests passed**, no failures or skips in these selections. **52 Python tests**, lint **0 errors / 90 warnings**. No model weights embedded; 158 compiled files match commit `c3c9cb1`. [Bilingual report and evidence](../../test-results/functional-audit-20260922/development-audit9/README.md).

Inference-only import, stale-digest profile selection, annotation preservation, model settings, projects, batches, English UI and workflows pass on Android. Fixture learning consumes the 88 accepted images from the verified exported batch, resumes after cancellation to 198 steps, then permits cleanup. The separate conversion campaign is ongoing; no ARM, task-accuracy or real HF publication qualification is claimed.

---

# Release 4.2.0-rc4 validation — 22 September 2026

Build `20260922T183415Z-818e4cf57d14` (versionCode 9): **54 JVM, 24 core Android and 2 HF regression tests passed**, with no failures or skips in these selections. **52 Python tests**, lint **0 errors / 89 warnings**. Weight-free APK, 143 compiled files matched to `d9b2239`. [Bilingual rc4 report and evidence](../../test-results/stabilization-rc4/README.md).

Model classes requiring explicit fixtures/options run separately. The 2 HF regressions cover previously qualified models and do not increase coverage. Debug prerelease on API 28 software emulator; no physical ARM qualification or new CI execution.

---

# Validation — 22 September 2026 UTC

Corrected build **`20260922T175957Z-8ef3e5cc2b0d`**, based on `351dd3f`: **54 JVM tests**, **48 Python tests**, **24 Android tests passed / 10 skipped**, zero failures. Lint: **0 errors, 89 warnings**. Both APKs were built, signed, installed and verified; no bundled weights, 181 source files matched.

[Full bilingual report and evidence](../validation-20260922/README.md) cover fixes, skipped model fixtures and the software-emulator System UI ANR. Earlier SDK blockers below are historical. This is not a physical ARM or stable-release qualification.

---

# Historical rc3 validation — 20 September 2026 UTC

[Français](../../TEST_REPORT.md) · **English**

Final APK: **4.2.0-rc3**, build `20260920T215432Z-cdd13d7fbf98`, compiled source `2738b13` (132 files matched to the commit).

| Check | Executed result |
|---|---|
| Build, identity and signing | Main and test APK built, verified, installed |
| JVM suite | **26/26 passed**, no skips |
| Lint | **0 errors, 72 warnings** |
| Android business/Compose suite | **19/19 passed**, no skips, **99.092 seconds** |
| Python host suite | **45 passed** |
| Portable numerical engine / training targets | **72 / 16 checks passed** |
| Model conversions | **5 passed, 1 failed, 25 pending**; individual development-build receipts retained |
| APK weight check | No embedded model weights |

The Android suite covers local batch/deduplication, concurrency, Room migrations, injected SAF failures, tokenizers, similarity, synthetic learning/save/restore/resume, exported-batch cleanup ordering, workflow gates, mask gestures and Compose navigation. The initial mask-tap regression is fixed and passes on the final APK; historical failed attempts remain in evidence.

APK: **365,878,582 bytes**, SHA-256 `37af2f8daa53169a44eca215fd4c50ebec6ba0d648759fd31ef8b6c6be083072`. See [selected evidence](../../test-results/litert-rc3/) and the [LiteRT matrix](LITERT_QUALIFICATION.md). Four HF conversions passed native train/save/restore/resume with frozen encoders. Internal visual-layer updates were separately demonstrated by the synthetic fixture, not by those HF conversions.

No credentials, private repository identifiers or weights are included in published evidence. GitHub Actions remains blocked before execution by account billing. Physical ARM performance, task accuracy, a real agent backend and authorized remote HF QA writes remain pending. **Debug prerelease, not a stable release.**

---

# Historical rc2 validation

# Validation and remaining limitations

[Français](../../TEST_REPORT.md) · **English**

Executed on 20 September 2026 (Europe/Paris), using the existing development pod and its API 28 x86_64 software emulator. Optimizer steps executed inside Android; no host-side training job ran.

## Final tested build

`20260919T234604Z-8a3382d3e997`

| Check | Executed result |
|---|---|
| Main and instrumentation APK | Built, signature/identity/hash checked and installed |
| JVM tests | **26/26 passed**, none skipped |
| Lint | **0 errors, 71 warnings** |
| Android business tests | **14/14 passed**, 90.509 seconds |
| Android Compose navigation | **2/2 passed**, 75.55 seconds |
| Local batch cycle | 2+1 unique images, two copies excluded, export/readback/purge/Room reopen |
| Concurrent identity claims | One owner for identical content; distinct image retained |
| Room migration | Versions 1/2/3 → 4 passed; annotations, cursor and hashes retained |
| SAF fault provider | Four scenarios passed, including changed or unreadable copies |
| Native internal-weight learning | 48 Android steps, loss 0.685404 → 0.018374, changed internal kernel and identical checkpoint reload |
| Exported-batch WorkManager lifecycle | 88 accepted images, 8 rejected cases and a neighbouring batch excluded; cancel at step 12, resume to 198; loss 0.689740 → 0.003558 |
| Cleanup ordering | Blocked before completion; then images and training snapshot removed, checkpoint and neighbouring image preserved |
| Tokenizers and similarity index | HF reference tokenization, persistence and model-contract isolation passed |
| APK weight inventory | No bundled weight files; native runtime libraries remain present |
| Local/pod source comparison | 155 files, zero mismatches |

Main APK SHA-256: `0fe9e90838183153b32b4a0c712eb814e4e6aa274e09cdb547a38d9e8477d73f`. Size: **403,138,636 bytes**. This is a universal debug APK, not a signed stable distribution release.

Raw evidence is retained under `test-results/batch-production/`. The build/weight-guard host suite passed 17 checks. Portable engine and target checks are supplementary evidence, not substitutes for Android execution.

## Models

RepViT from the pinned HF catalogue passed Android inference under the same runtime on the preceding build (`20260919T233636Z-2bc826cf21ad`), with a total test time of 67.051 seconds. This is neither phone latency nor accuracy evidence.

The trainable test model is synthetic. No production HF training conversion is yet qualified. DINOv2 exceeded ten minutes in the software emulator and was stopped. Remaining families and multi-graph bundles need conversion-specific testing. RTMDet decoding, mask editing/export and the autonomous agent/general workflow engine remain incomplete.

## Release blockers

- Actual trainable HF conversions, representative accuracy/forgetting evaluation and remaining model operators.
- Physical ARM device memory, latency, battery and thermal behaviour.
- Real DocumentsUI/cloud-provider persistent grants and their revocation; injected-provider tests do not replace them.
- Authorized HF publication, conflict, lost-response and interrupted-transfer scenarios.
- Executed API 28/35 CI, signing/distribution workflow and transitive licence/notice review.

The exact-image ledger is scoped to a project on one installation. It does not guarantee detection of cropped, edited or lossy-recompressed near-duplicates. Old already-purged images retain only the hashes previously available.

The source repository is public. Qualification artifacts use the tested pod build; publishing them does not qualify a stable release. The GitHub Actions attempt was blocked before any job started by an account billing issue. The OCI package is a qualification archive; the prepared build image has not been built or validated.
