# Android trainable-conversion contract

[Français](../LITERT_TRAINING_CONTRACT.md) · **English**

Learning executes inside Android through `Interpreter.runSignature`. The pod only builds the app and runs QA. The app has no remote training server or corpus/gradient upload path.

Adding JSON cannot make an inference graph trainable. The converter must export mutable variables, gradient/update operations and persistence signatures. Internal-layer learning requires actual gradients for those layers; a `scope` string alone is not proof.

## Expected files

- `model.tflite` with mutable variables and train/infer/save/restore signatures.
- `android_model_config.json` or `model_config.json` describing the Android contract.
- `artifact_manifest.json` with file sizes and SHA-256 hashes.
- Model card, licence, preprocessing details, upstream revision and conversion report.

Downloads pin the HF revision and verify hashes. Dynamic models retain their runtime contract and configuration. Incompatible contracts are rejected instead of guessing target semantics.

## Classification example

```json
{
  "task": "classification",
  "adapter": "classification",
  "inputWidth": 224,
  "inputHeight": 224,
  "inputLayout": "NHWC",
  "inputType": "FLOAT32",
  "mean": 0.0,
  "std": 255.0,
  "resizeMode": "stretch",
  "labels": ["classe_a", "classe_b"],
  "scoreActivation": "none",
  "training": {
    "trainSignature": "train",
    "inferSignature": "infer",
    "saveSignature": "save",
    "restoreSignature": "restore",
    "imageInput": "x",
    "targetInput": "y",
    "lossOutput": "loss",
    "checkpointInput": "checkpoint_path",
    "learningRateInput": "learning_rate",
    "inferOutputs": ["scores"],
    "targetEncoding": "one_hot",
    "targetShape": [1, 2],
    "weightProbeSignature": "weights",
    "weightProbeOutput": "visual_kernel",
    "weightProbeInput": "probe",
    "scope": "internal_visual_and_output_layers"
  }
}
```

Replace the example dimensions, labels, preprocessing and tensor names with the actual model contract. `train` takes an image and FLOAT32 targets. `learning_rate` is an optional scalar; leave `learningRateInput` empty if the graph fixes the rate. `loss` must be scalar and finite. `infer` takes the image and returns tensors in the order declared by `inferOutputs`.

`save` and `restore` take a scalar STRING checkpoint path supplied inside app-private storage. Checkpoints may contain several files sharing that prefix. Files are hashed after saving and checked before restoration. Signatures must not write elsewhere. JSON never executes arbitrary downloaded source code.

The optional `weights` probe exposes an internal visual-layer tensor. Comparing it before/after training demonstrates that layer changed. The Java binding requires an input: `probe` is a FLOAT32 scalar supplied as zero. The output must be the actual weight tensor, not a constant, metric or only the output head.

## Implemented targets

| Encoding | Shape | Meaning |
|---|---|---|
| `one_hot` | `[1,C]` | One human-confirmed class per image |
| `multi_hot` | `[1,C]` | Explicit positive or negative decision for each class; missing tags do not prove absence |
| `points_xyv` | `[1,C,3]` | Coordinates in model-input space and visibility; one point per class |
| `heatmap_nchw` | `[1,C,H,W]` | Human point-centred Gaussian targets, sigma one pixel |
| `boxes_xyxy_class_mask` | `[1,N,6]` | Box coordinates, class index and presence mask, with explicit zero padding |

Other encodings need a dedicated adapter. Training graphs requiring text or auxiliary mask inputs are not supported by this contract. An inference bundle may work without being trainable.

## Dataset and lifecycle

Learning is off by default. Only accepted images from the completely reviewed and verified exported batch enter its private snapshot. The batch fingerprint and export proof are recorded. Other batches and unreviewed proposals are excluded. Cleanup waits for learning and evaluation to finish; errors and cancellation retain the images.

A deterministic file-hash split assigns roughly 80% training / 20% control, with at least 32 training and 8 control images. Exact duplicates cannot cross that split; transformed near-duplicates are not automatically grouped. Conflicting targets for an identical file are rejected. Target values are capped at four million per batch to bound memory.

WorkManager runs without a network requirement when the battery is not low. Durable checkpoints support cancellation and resume. Candidate acceptance requires more than 1% control-loss improvement and, when a probe is available, changed internal weights. A fresh interpreter must reproduce checkpoint outputs. This reused control set is not an independent generalization benchmark. Activation remains manual; rejected, interrupted or failed runs never replace the active model.

## Runtime and qualification

The Interpreter path uses LiteRT 1.4.2, Select TF Ops 2.16.1 and an explicit Flex delegate for secondary save/restore signatures. Inspected official 2.1.5/2.2.0 AARs do not expose the required Java Delegate interface; Android training under 2.2.0 changed weights but failed on FlexSave. Compatibility must be tested per conversion. CompiledModel is not advertised as a training backend.

For each real conversion, exercise all four signatures, prove internal-weight updates, restore in a fresh interpreter, test cancellation/resume and target geometry, then measure memory/latency/battery on a phone and evaluate accuracy/forgetting on an authorized independent corpus.

`tools/qa/create_training_fixture.py` only compiles an untrained synthetic visual network. Optimizer steps run in Android tests. Its successful validation does not qualify production HF models. See [executed validation](VALIDATION.md).
