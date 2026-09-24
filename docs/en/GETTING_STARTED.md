# Your first batch with Cadryl

[Documentation](README.md) · [Français](../GETTING_STARTED.md)

Start with a few images and no model. Add AI assistance once you are comfortable with the workflow.

## Install Cadryl

Get **`vision-dataset-studio.apk`** from the [rc6 release](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc6). This is the signed Release app for Android 9+ on ARM64. Model weights are downloaded separately.

Check the download against `SHA256SUMS`. In PowerShell:

```powershell
Get-FileHash ./vision-dataset-studio.apk -Algorithm SHA256
```

The qualification ZIP also contains test APKs and an x86_64 build. The Flex, Graphics Path and LiteRT ZIPs are build dependencies; you do not need them to install the app.

**Already have rc4 or rc5?** Those builds use different certificates and cannot receive rc6 as an update. Keep their data and installation. Candidates signed with the [durable key](../SIGNING.md) retain the same Android identity. Phone acceptance for this candidate is still pending.

## 1. Create a project

Open project management, choose tasks and classes, then configure the source through **Import**. Pick a small local folder and a manageable batch size. Choose an export folder you can open again later.

Each project keeps its own source, batches, annotations and history.

## 2. Prepare images

Index the source and prepare a batch. Exact copies already processed in the project are recognised even after cleanup. Crops and edited images may remain separate cases.

Retry failed acquisitions or exclude them with a reason. A failed download never counts as a reviewed image.

## 3. Add a model if it helps

In **Models**, import a compatible file or choose an authorised Hugging Face source. [Charlbi’s conversions](https://huggingface.co/Charlbi/Lite_rt_prepared_for_android_dataset_builder) have individual contracts and test results. Keep every required graph, processor and tokenizer in a bundle.

Inspect the model, try one image, then request suggestions for the batch. Inference-only models are supported. Changing a prompt or setting does not rewrite saved annotations.

## 4. Review and correct

Adjust boxes, points and masks. Approve usable images and reject others with a reason. Model suggestions need your review. An image with no known annotation is not automatically a negative example.

## 5. Export and read back

Keep the **canonical JSONL** and required images. COCO, YOLO, WebDataset and vision-language exports cover different needs; see the [data schema](../../DATA_SCHEMA.md).

Create the archive, copy it to your chosen folder or authorised HF repository, then wait for readback before cleanup. Resolve network failures and commit conflicts using their receipts rather than assuming an upload succeeded.

## 6. Train a copy, optionally

Training starts disabled. It needs a compatible model and a reviewed batch with a verified export. The deterministic split must contain **at least 32 training and eight validation images**; 40 images in total do not automatically meet that split.

The first run creates a separate trained version. Later runs continue from its last validated weights even while the original is still selected for inference. Activating new weights remains your choice. [Versions and checkpoints](MODEL_LINEAGE.md).

## 7. Clean up and continue

Confirm cleanup after the copy is verified. Complete or explicitly abandon any unfinished training first. Receipts, duplicate history and referenced model versions remain available. Then prepare the next batch.

## If something gets stuck

| Problem | Check |
|---|---|
| Installation refused | Android version, storage and the existing signing certificate |
| Inference works but training does not | This variant’s contract and signatures; `_learning` variants are separate |
| Bundle fails to load | Missing graphs or supporting files |
| Training refused | Verified export, actual split sizes, contract and checkpoint |
| Export folder unavailable | Storage availability and Android document permission |
| Poor predictions | Classes, preprocessing and whether the model fits your images |

[Known limits](KNOWN_LIMITATIONS.md) · [Workflows](WORKFLOWS.md) · [Report a problem](https://github.com/unicornwhodev/vision-dataset-studio/issues).
