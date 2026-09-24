# Guided workflows and masks

Choose a guided path when you want Cadryl to take care of the sequence. It pauses wherever your review or confirmation is needed.


[Français](../WORKFLOWS.md) · **English**

**Workflow** offers assisted production, manual production and batch finalization. **Continue** executes available steps and pauses at human decisions. The journal persists per project and batch, including the current step and any error.

| Step | Behaviour |
|---|---|
| Import | Uses the project's source index and persistent duplicate checks |
| Preannotation | Runs the selected model on new cases and preserves human edits |
| Review | Waits for explicit acceptance or rejection of every case |
| Audit | Checks annotations and accepted-image uniqueness |
| Export | Prepares an archive or closes a fully rejected batch |
| Verification | Waits for proof of a read-back copy |
| Learning | Optional, restricted to the exported batch; cleanup waits for completion |
| Cleanup | Waits for your confirmation in Export |

An exhausted empty batch or an already cleaned batch finishes without importing again. Advancing to the next batch remains explicit. Restarting a template does not bypass these gates.

The optional local agent receives instructions and case counts, then suggests one of the three templates. You choose whether to use it. It cannot accept annotations, publish or delete. A server must be supplied separately on the device; only HTTP `127.0.0.1` or `localhost` endpoints without redirects are accepted. No autonomous VLM server is bundled. The `dataset-agent/1` system prompt is visible in the agent panel.

## Masks and SAM

Enable Segmentation. The brush adds to the selected mask; the eraser removes pixels. **Case actions → New mask** starts another instance, including another instance of the same class. Masks can be selected in the region panel, relabeled and reviewed; undo/redo remains available.

With a SAM bundle selected, **Case actions → Point for SAM** places a temporary hint. **Segment this point** runs inference. This hint never becomes an exported annotation. An existing human box or point may also guide SAM when no temporary hint is selected.

Proposed masks require correction and review. Later inference preserves human masks. Canonical JSONL retains the RLE mask and its raster dimensions. COCO projects it to original image dimensions and marks each instance `iscrowd=0`, streaming the RLE without allocating another full-size image. Limits: four million canonical-mask pixels, one hundred million projected-image pixels and bounded RLE complexity. YOLO detection remains a box projection.
