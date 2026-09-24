# Cadryl architecture

[Français](../ARCHITECTURE.md) · **English**

The app is an Android workspace for producing image datasets in batches. Images, human correction and verified exports govern its workflow. The diagrams show implemented components; they do not establish qualification of every external integration.

![Architecture](../visuals/architecture.en.svg)

## Components and responsibilities

| Component | Responsibility | Source |
|---|---|---|
| Compose UI | Workspace, navigation, region/mask editing and human review | [Screens](../../app/src/main/java/com/unicornwhodev/visiondatasetstudio/ui/screens/) |
| Batch engine | Acquisition, identity reservation, batch preparation and safe resume | [BatchEngine](../../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/batch/BatchEngine.kt) · [ImageIdentity](../../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/batch/ImageIdentity.kt) |
| Room v4 | Projects, annotations, persistent identities and production state | [AppDatabase](../../app/src/main/java/com/unicornwhodev/visiondatasetstudio/data/db/AppDatabase.kt) |
| LiteRT | Load contract/weights, preprocess, execute and decode outputs | [LiteRtEngine](../../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/inference/LiteRtEngine.kt) |
| Learning | Reviewed targets, background Android work, checkpoints, resume and evaluation | [OnDeviceTraining](../../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/training/OnDeviceTraining.kt) · [TrainingTargets](../../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/training/TrainingTargets.kt) |
| Export | Canonical archive plus COCO, YOLO, WebDataset and vision-language projections | [DatasetExporters](../../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/export/DatasetExporters.kt) |
| HF / files | Authorized remote acquisition/publication, SAF read/write and verification | [HfApiClient](../../app/src/main/java/com/unicornwhodev/visiondatasetstudio/data/hf/HfApiClient.kt) · [Sources](../../app/src/main/java/com/unicornwhodev/visiondatasetstudio/data/source/) |
| Workflows / agent | Executable templates, atomic journal and optional local suggestion | [WorkflowRunner](../../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/workflow/WorkflowRunner.kt) |

External arrows represent application access to sources/destinations, not sending every image to a server. HF writes and SAF copies remain explicit operations with readback verification. The development workstation builds and tests APKs; it is not a required inference or training server for users.

## Protected batch lifecycle

![Batch lifecycle](../visuals/batch-flow.en.svg)

1. Import after checking project content identities. Optionally preannotate with an active model; protect existing human edits.
2. Correct and accept/reject each image. Predictions are not human approval.
3. Export and verify the external copy. The canonical archive is authoritative; format projections are supplementary.
4. If learning is enabled, use accepted images and final annotations from this exported batch only. It is off by default. Wait for completion and evaluation; interruptions/errors retain images.
5. Confirm cleanup. Working images and the training snapshot are removed; annotations, identities, receipts and checkpoints survive. Continue to the next batch. Candidate weights require manual activation.

Persistent identities reject exact copies between batches of the same project. They do not guarantee rejection of all crops or lossy recompressions. Models are downloaded after installation: **no weights in the APK**.

## Agent boundary

The optional HTTP planner receives counts, instructions and templates on `localhost`/`127.0.0.1`. It receives no corpus or HF credentials. Its structured response selects a template; it cannot approve annotations, publish, purge, switch sources or activate weights. Filenames, images and model outputs are data, not instructions. Integration with a real agent server is still pending.

## Where the evidence lives

The [current report](VALIDATION.md) identifies the exact rc6 APKs and separates them from earlier phone and interruption campaigns. The [model matrix](LITERT_QUALIFICATION.md) records individual conversion results. This architecture describes responsibilities; it does not claim every integration has been qualified.
