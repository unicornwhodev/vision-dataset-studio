# Reprise rc5 / rc5 handoff

Build `20260922T223550Z-3ee0d233110c`, **4.2.0-rc5 / 10**, sources `a0d9737586971ac243252cb147a62010a4398083`. Les 158 fichiers compilés correspondent au commit. APK sans poids.

| Contrôle / Check | Résultat / Result |
|---|---|
| JVM / Python | 67 / 52 passed |
| Lint | 0 errors, 90 warnings |
| Android, initial run | 38 passed, 1 failed, 0 skipped |
| Android, targeted retry | 1 passed, 0 failed, 0 skipped; 48.567 s |
| Final coverage | All 39 selected cases passed across the initial run and retry; not a single green full-suite run |
| Inference-only import | MobileNet content URI, stale digest accepted, 3 native proposals, no training |
| Synthetic internal-weight training | 48 Android optimizer steps; changed weights and exact checkpoint reload |
| Exported-batch workflow | 88 accepted, 8 rejected excluded; explicit abandonment, restart, cancellation at step 9, resume to 198, cleanup after training |

Le premier échec d’import provient du budget consommé par les fixtures de conversions accumulées. Ces seules fixtures de QA ont été déplacées réversiblement de `files/conversion-qualification` vers `no_backup`, sans effacer les données, la base ou les annotations et sans modifier l’APK. L’import passe ensuite. Les contrôles SHA des modèles sont supprimés ; les preuves de build identifient toujours les artefacts testés.

The first import failure came from accumulated conversion fixtures consuming the storage budget. Only those QA fixtures were reversibly moved from `files/conversion-qualification` to `no_backup`, without clearing data, the database or annotations, and without changing the APK. Import then passed. Model SHA gates are removed; build evidence still identifies tested artifacts.

La campagne de conversions est distincte : 13 succès, dont 8 avec apprentissage ; trois délais dépassés sur émulateur, 15 conversions non exécutées. Les modèles privés ne sont identifiés dans aucun reçu public. Les encodeurs HF restent figés. Aucun benchmark ARM, gain de précision, publication HF réelle ni succès CI n’est revendiqué.

The separate conversion campaign has 13 passes, including 8 with learning; three emulator timeouts and 15 unexecuted conversions. No private model identifier is published. HF encoders remain frozen. No ARM benchmark, accuracy improvement, live HF publication or CI success is claimed.

## Evidence

- [Build and APK inventory](final-build/status.json), [compiled-source comparison](source-manifest.json).
- [JVM/lint](jvm-lint.json), [initial Android run](final-device/instrumentation.txt), [successful targeted retry](inference-only-retry/instrumentation.txt), [machine-readable outcome](core-result.json).
- [Inference without training or SHA gate](inference-only.json).
- [Native synthetic training](training-internal-weights.json), [exported-batch lifecycle](training-workflow.json).
- [Earlier complete 39-test pass, build audit9](development-audit9/README.md).
- [Real-photo receipt on its actual audit8 build](native-photo-audit8/native-photo-result.json), [build receipt](native-photo-audit8/build-receipt.json); photo and screenshots excluded.
- [Public conversion results](public-model-results.json), [public artifact equivalence](public-revision-equivalence.json).
