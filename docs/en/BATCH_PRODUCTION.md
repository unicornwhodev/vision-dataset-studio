# Batch production

Batches keep the job manageable: work on a small set of images, finish its review and export, then move on. This page explains what Cadryl keeps at each step.


[Français](../BATCH_PRODUCTION.md) · **English**

The main workflow is **import → preannotate → human correction/review → verified export → optional learning → cleanup → next batch**.

Learning is off by default. Accepting an image never starts it. When enabled, it uses only accepted images and final annotations from that fully reviewed batch after its local or HF export has been verified. Incomplete or unexported batches are rejected. Other batches, rejected images and duplicate audit rows are excluded. The run receipt records the batch fingerprint and export proof.

Cleanup is blocked while that batch’s learning is queued, active, cancelled or failed. It becomes available after learning and evaluation finish, including when the candidate is rejected, or after explicit abandonment of an interrupted attempt. Cleanup still requires confirmation and a fresh readback of the backup. It removes working images and the training snapshot, while retaining annotations, history, image identities, export receipts and checkpoints. New weights require manual activation.

Without learning, imports, manual corrections and exports do not require a model. With an active model, automatic preannotation can run on newly imported images. It processes only untouched cases; imported annotations and human work are preserved. Inference errors leave the batch available for manual correction and retry.

After failure or cancellation, **Models → Training → Abandon** explicitly closes the attempt after confirmation, without activating weights or deleting images. Batch cleanup remains a separate action after export verification. A new attempt can be started explicitly; a previously completed run cannot satisfy the cleanup requirement if the batch export changed.

## Image identity

Room v4 keeps a per-project ledger independent of cache files and batches: SHA-256 of the source file, normalized file and exact decoded sRGB pixels. Filename, path and source identifier do not define content identity. Pixel hashes use 64-row strips without resizing the image.

Identical files, renamed copies and lossless re-encodings with identical decoded pixels are excluded before annotation. Their source metadata and original batch owner remain in the audit history. Concurrent acquisitions claim identities atomically. Vacant slots are filled from later source entries; failed downloads remain visible and retryable. One import action scans at most 20 windows before offering continuation on duplicate-heavy sources.

The ledger survives export, purge and application restart. Migration seeds it from historical file hashes. Pixel hashes cannot be reconstructed for old images already purged; those remain protected by their retained file hashes. Lossy JPEG recompression, cropping and edits are outside this exact-identity guarantee. Perceptual hashes never automatically remove distinct images.

The scope is one project on this installation. A separate project or a new installation without database restoration does not inherit the ledger. A dataset export is not a full project backup.

## Delete, reset or switch projects

**Workspace → Projects** creates and selects independent projects. Each retains its sources, batches, annotations, model settings and fingerprints. The model library and HF connection belong to the device.

**Delete batch · keep history** discards an unexported batch after confirmation. Working images and annotations are removed, but its source cursor, fingerprints and batch number remain reserved. The same images cannot return in a later batch of that project. Exported batches use the usual verified cleanup action.

**Reset** explicitly removes the relevant history and allows reimporting. Resetting, or deleting a whole project, intentionally discards its historical reimport protection. It is distinct from normal batch cleanup.

## APK and models

The APK includes runtimes, adapters, contracts, catalogue, tokenization and download/import logic. Weights are obtained after installation and stored in Android private storage. No user corpus or model weights are bundled.

The build scans weight extensions and LiteRT/GGUF headers under assets/raw. `app-contents.json` records the result, and a detected weight prevents APK promotion. QA models are staged separately on the emulator. rc6 distributes an ARM64 Release APK; older universal Debug APKs included native libraries for four architectures.

Trainable graphs must satisfy the [LiteRT contract](LITERT_TRAINING_CONTRACT.md). Implementing a contract does not qualify every HF conversion. The [validation report](VALIDATION.md) separates executed tests, failures and pending work.
