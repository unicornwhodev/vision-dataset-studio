# Roadmap

[Français](../ROADMAP.md) · **English**

The primary deliverable is a reliable dataset-production workspace. Learning is an optional Android step after verified batch export and before cleanup. The pod is a development and QA machine.

| Priority | Delivery | Current status / acceptance |
|---|---|---|
| 1 | Local batch production and persistent exact deduplication | Android 2+1 cycle, renamed copies, concurrent claims, export, purge and reopening passed |
| 1 | Optional learning on the exported batch | Synthetic internal-weight updates, checkpoint restore and WorkManager cancel/resume passed; real HF training conversions pending |
| 1 | Final cleanup ordering | Run must finish before removing images; checkpoint and unrelated batches must survive |
| 2 | Per-model HF qualification | RepViT inference passed; DINOv2 exceeded the software-emulator time budget; remaining models need individual tests |
| 2 | Remaining model tools | Implement RTMDet decoding and a mask editor/export; verify multi-graph inference with representative images |
| 3 | General executable workflows | Versioned steps, validated parameters, resumable runs and observable errors; current packs are configuration templates |
| 3 | Autonomous agent | Real configured backend, versioned instructions, validated structured output, connected studio tools and execution audit; a mock HTTP response is insufficient |
| 4 | Physical device and external integrations | ARM memory/latency/battery, actual SAF permissions, authorized HF transfer failure/conflict cases, API 28/35 CI |
| 5 | Public repository and releases | Publish only when the owner’s functional-validation condition is met; exact tested commit, bilingual docs, licence/notice review and matching artifact hashes |

An independent authorized evaluation corpus is required before claiming accuracy or learning improvements for production models. Keep a working previous model and explicit activation/rollback.

The planned public repository is `unicornwhodev/vision-dataset-studio`. APK assets, a release signing process and any GHCR build image have separate qualification gates. No publication is implied by a passing local build.
