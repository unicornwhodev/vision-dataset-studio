# Roadmap — 4.2.0-rc3

[Français](../ROADMAP.md). Batch dataset production with human review remains the priority. Android learning is optional, after verified export and before cleanup.

| Priority | Delivery | Status / next evidence |
|---|---|---|
| 1 | Local batches, persistent exact deduplication, export and cleanup | 2+1 cycle, concurrency, resume and migrations tested; representative corpus remains |
| 1 | LiteRT conversions | 5 passed, 1 RTMDet defect, 25 pending; [matrix](LITERT_QUALIFICATION.md) |
| 1 | HF internal-layer learning | All 20 trainable conversions freeze the encoder; new conversions required for internal-layer updates |
| 2 | Masks and segmentation | Editor, separate instances, COCO and temporary hint tested; real SAM pipeline pending |
| 2 | Workflows and agent | Templates/journal/guards implemented; real agent backend and full integration pending |
| 2 | Model quality | Independent authorized corpus, before/after and forgetting assessment; no unmeasured improvement claims |
| 3 | Device and storage | Physical ARM memory/latency/thermal checks, real SAF grants, large corpus |
| 3 | Remote HF and CI | Authorized QA repository for conflict/lost-response scenarios; API28/35 CI blocked by billing |
| 4 | Stable distribution | Durable signing, transitive notices and optimized APK; rc3 remains a Debug prerelease |

Sources, tools, evidence and documentation are retained for resumption. Historical audits refer to their original commits, not the current status.
