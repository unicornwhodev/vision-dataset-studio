# Your first batch

[Home](../../README.en.md) · [Documentation](../README.md) · [Français](../GETTING_STARTED.md)

Follow a small authorized batch from import to verified export on a QA installation. Manual annotation does not require a model.

## Install the prerelease

Download from [rc5](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc5) and compare each file with the published `SHA256SUMS`.

| File | Purpose |
|---|---|
| `vision-dataset-studio.apk` | Debug application, Android 9+, 413.9 MB, no weights |
| `vision-dataset-studio-4.2.0-rc5-qualification.zip` | User/test APKs, selected evidence and documentation |
| `vision-dataset-studio-arm64-v8a-unsigned.apk` | Optimized 100.2 MB candidate; unsigned and not directly installable |
| `vision-dataset-studio-flex-2.16.1-vds16k1.zip` | Local Maven runtime for building the app |
| `PACKAGE.json`, `PUBLICATION_CHECKS.json`, `SHA256SUMS` | Provenance, publication checks and integrity |

PowerShell: `Get-FileHash .\vision-dataset-studio.apk -Algorithm SHA256`. **rc5 cannot update rc4**, because their certificates differ. Do not uninstall an application containing annotations to bypass this conflict. A safe migration requires compatible signing or a qualified data-transfer procedure; rc5 adds no such migration.

## 1. Create a project

Choose tasks, classes, source and batch size in project setup. Start with a small local folder and choose an export location you can read back. Document-provider permissions can later be revoked.

## 2. Prepare images

Index the source and prepare a batch. Exact file/pixel duplicates already processed in this project are recognized after cleanup. Retouched or lossy-recompressed images may remain distinct. Failed acquisition requires retry or an explicit exclusion with a reason.

## 3. Add model assistance if useful

In **Models**, import a compatible model or configure an authorized HF source. Check [Charlbi conversions](https://huggingface.co/Charlbi/Lite_rt_prepared_for_android_dataset_builder) and their per-variant results. Include contracts and every component of a bundle. Inspect the model and try one image before the batch.

A model without training signatures can still perform inference. Changing a prompt or setting does not rewrite existing annotations; rerunning is explicit.

## 4. Review and decide

Inspect proposals, correct boxes/points/masks and accept or reject each image. A proposal does not replace human validation. An image with unknown annotations is not automatically a negative training sample.

## 5. Export and read back

Retain **canonical JSONL** and required images. COCO, YOLO, WebDataset and vision-language outputs are additional projections. [Data contracts](../../DATA_SCHEMA.md).

Create the archive, copy it and verify readback. HF publication requires an authorized destination. Resolve conflicts and lost responses using receipts rather than silently changing a commit parent.

## 6. Learn only when enabled

Learning is off by default. It requires a trainable model and a reviewed batch with a verified export. The deterministic split must produce **at least 32 training images and 8 control images**; choosing 40 images does not guarantee this split.

The first run creates a separate learned version. Later runs restore its latest validated weights while preserving the original. Interrupted attempts can resume. Activating new weights for inference is explicit. [Versions and checkpoints](MODEL_LINEAGE.md).

## 7. Clean up and continue

Confirm cleanup only after verified copying and completion or explicit abandonment of optional learning. Receipts, persistent identities and referenced model versions remain available. Then prepare the next batch.

## Troubleshooting

| Situation | Check |
|---|---|
| Installation rejected | Existing certificate, Android version and free space; preserve data |
| Model cannot train | Variant signatures and contract; `_learning` is a separate conversion |
| Incomplete bundle | Include all graphs, processors and tokenizers |
| Learning blocked | Verified export, actual split counts, compatible checkpoint and contract |
| Copy/SAF interruption | Volume and folder access; never purge without verified readback |
| Weak detection quality | Execution tests do not measure accuracy; check model, classes, data and preprocessing |

[Known limits](KNOWN_LIMITATIONS.md) · [Workflows](WORKFLOWS.md) · [Qualification matrix](../ANDROID_QUALIFICATION.md).
