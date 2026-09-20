# LiteRT qualification — 4.2.0-rc3

[Français](../LITERT_QUALIFICATION.md) · **English**

Snapshot frozen on 20 September 2026 UTC at the owner's request. Tests stopped between conversions to publish the prerelease and close the workstation. **5 conversions passed, 1 failed, 25 remain** out of 31 downloaded and verified artifacts. The full catalogue is not qualified.

Public source `Charlbi/Lite_rt_prepared_for_android_dataset_builder` is pinned to `1244117f490e36ce321d70baa672753caeaef028`: 25 conversions, 15 trainable. Six further conversions from an authorized private source were downloaded and verified, five trainable; none has completed Android qualification. Their identifiers, contracts and weights are not published here.

## Executed Android results

| Conversion | Inference | Train / save / restore / resume | Output delta | Restore delta | Build |
|---|---|---|---:|---:|---|
| edgenext_xx_small_learning | PASS | PASS | 0.09351325 | 0.0 | `20260920T211135Z-09c558a5b622` |
| edgenext_x_small_learning | PASS | PASS | 0.09805727 | 0.0 | `20260920T204726Z-e97732364106` |
| repvit_m1 | PASS | Not exposed / Non exposé | — | — | `20260920T211135Z-09c558a5b622` |
| rtmdet_tiny_learning | PASS | PASS | 0.15809631 | 0.0 | `20260920T211135Z-09c558a5b622` |
| edgenext_small_usi_learning | PASS | PASS | 0.07088137 | 0.0 | `20260920T212400Z-583fa4f10a2d` |

Evidence: [JSON receipts](../../test-results/litert-rc3/models-passed.json) and attempt logs under `test-results/litert-rc3/`. Each receipt retains its actual build ID. Development-build results are not relabelled as results from the final rc3 APK; the final build adds the rc3 version and reruns build/business checks.

Each passing trainable conversion performed two optimizer steps **inside Android**, with changed outputs, save, restore in a fresh interpreter, resumed training and checkpoint inference through the application engine. Original downloaded files remain unchanged. Production activation stays manual.

**Weight scope:** all 20 supplied trainable conversions freeze the visual encoder and update a head or output adapter. They do not yet meet the requested full internal-layer training. The separate tiny synthetic QA network does update its internal visual layers; that evidence must not be attributed to the HF conversions.

## Open defect and remaining work

Inference-only `rtmdet_tiny` completes its computation, but the decoder rejects a feature-map shape. Reading dynamic outputs after invocation is fixed; the shape/stride mapping still needs diagnosis. The verdict remains **FAIL**, separate from the passing `rtmdet_tiny_learning`. The application reports the error and does not manufacture annotations.

The other 25 conversions remain pending, including the six private conversions, TinyCLIP/SAM/Florence bundles and remaining classifiers/detectors/pose models. `fetch_model_fixtures.py`, `stage_android_fixture.py` and `qualify_converted_models.py` keep fixtures outside the APK and write per-case receipts. A `STOP_AFTER_CURRENT` file in the evidence directory stops the queue between cases.

[Community PR #10](https://huggingface.co/spaces/litert-community/README/discussions/10) remains open. It proposes missing personal variants; acceptance depends on the maintainers. No private conversion is included.

## Measurement boundaries

Java CPU runtime: LiteRT 1.4.2 + Select TF Ops 2.16.1; AOSP API 28 x86_64 software emulator without KVM. Synthetic input and corrections follow each contract. These are execution/update/persistence checks, not task accuracy, generalization or ARM phone benchmarks. No optimizer ran on the Linux pod host. The APK embeds no model weights.

## Visual evidence

![Checkpoint round trip](../visuals/benchmarks/android-checkpoints.png)

![Whole-test duration](../visuals/benchmarks/android-test-duration.png)

The duration includes model loading, multiple inference calls and training/checkpoint operations where exposed. It is not inference latency or a phone benchmark. Each row is a single execution; workloads differ. [Reproducible values and image provenance](../VISUALS.md).

[Full model report](https://huggingface.co/Charlbi/Lite_rt_prepared_for_android_dataset_builder/blob/main/docs/BENCHMARKS.en.md) separates host conversion checks, the standalone RepViT learning SDK report and this app campaign. The SDK report does not change the app coverage counts.
