# Qualification limits — rc5 audit

[Français](../../KNOWN_LIMITATIONS.md) · [Functional audit](FUNCTIONAL_AUDIT_2026_09.md)

## Models and learning

Thirteen converted models have passed Android execution, including eight with train/save/restore/resume. Three conversions reached the software-emulator time limit (RF-DETR and two private conversions); this does not establish model incompatibility. Fifteen further conversions remain unexecuted. The [per-conversion matrix](LITERT_QUALIFICATION.md) retains the actual build and scope of each result.

All twenty supplied trainable HF conversions freeze their visual encoder and update a head or output adapter. The separate synthetic QA network changes internal visual layers; that proof does not apply to those HF encoders. No accuracy improvement, generalization result or physical ARM benchmark is claimed.

Learning is optional and off by default. It consumes the completed batch after its export is verified and before cleanup. Failure or cancellation retains the images; activating learned weights remains manual. After failure or cancellation, **Models → Training → Abandon** explicitly closes the attempt after confirmation, without activating weights or deleting images. Batch cleanup remains a separate action after export verification. A new attempt can be started explicitly; a previously completed run cannot satisfy the cleanup requirement if the batch export changed. Models without training signatures remain usable for inference. SHA manifests and stored model digests do not gate model import or use.

Training requires at least 32 training and 8 control images under the deterministic split. Targets are limited to one million values per image and stored separately. Masks and auxiliary presence/abstention/supervision targets follow the conversion contract; training text inputs are unsupported. Reusing a control set is not evaluation on an independent corpus.

Interpreter/Flex uses LiteRT 1.4.2 and Select TF Ops 2.16.1. The attempted LiteRT 2.2 integration lacked the required Java delegate API and failed FlexSave. GPU/NPU execution and distributed training are not integrated.

RepViT, HGNetV2, RTMDet, DINOv2 and TinyCLIP inference passed. The RTMDet dynamic-output defect is fixed. TinyCLIP proposals were visible on a real photo, with prompt changes applied only after an explicit rerun. SAM and Florence pipelines remain under qualification. Florence uses bounded greedy decoding; actual task quality remains unmeasured.

HTTP grounding requires an explicit `task=grounding` / `httpOutputMode=grounding_proposals` contract, unique regions and validated phrase-region references. Contract and human-review tests pass; no real grounding server is qualified. Florence and caption-plus-box outputs are not advertised as grounding. No instance link is invented during import or inference.

## Batches and external services

The local 2+1 batch cycle, exact-copy exclusion, export, cleanup and database reopening passed Android tests. File and decoded-pixel identities survive cleanup per project. Lossy recompression, cropping and edits are outside this exact-identity guarantee. Previously purged images retain only their historical file hashes.

Four injected SAF failure scenarios pass. They do not replace real DocumentsUI/cloud-provider permission-grant and revocation tests. Room migrations 1/2/3 → 4 passed on Android; v1/v2 inputs are reconstructed fixtures. A database from an actual legacy installation remains to be tested.

Live HF reservations, commit conflicts, lost responses, publication and network-loss cleanup remain unqualified until a dedicated private QA repository is authorized. No existing repository receives QA writes. Three workflow templates support journaling and review/export/cleanup gates. The optional local HTTP planner has a tested protocol fixture; integration with a real agent server remains pending. No VLM server is embedded.

## Interface and distribution

French and English application text is implemented. The English Compose flow passed project creation/switching, persistent settings and visible proposals on API 28. User-written content retains its original language. TalkBack, large-font coverage across all screens, real SAF providers and physical ARM devices remain to be qualified.

The APK contains no model weights. The universal Debug APK remains large because it includes native libraries for four architectures. An optimized distribution build and stable distribution signing are still to be prepared; one user APK is published per prerelease.

The software emulator has no KVM and is used for functional checks, not phone latency, memory or thermal claims. No current CI execution is claimed; the historical GitHub Actions attempt was blocked by account billing. The OCI package is a qualification archive, not a runnable build image, and its visibility is currently private.

Code is Apache-2.0. Model/dataset licences and transitive notices remain separate. Changing application ID does not migrate another application's data. The rc3 Debug key is retained outside Git for subsequent updates. The owner confirmed no rc2 installation was distributed, so no rc2 migration is planned.
