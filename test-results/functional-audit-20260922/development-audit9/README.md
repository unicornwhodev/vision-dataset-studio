# Recette rc5 / rc5 qualification

Build `20260922T214516Z-3f844ff6cb1e`, version **4.2.0-rc5 / 10**, sources compilées `c3c9cb1809e58772013bd2fcfd86dd6a9ff195e0`. Les 158 fichiers de compilation correspondent au commit. L’APK ne contient aucun poids.

| Contrôle / Check | Résultat / Result |
|---|---|
| JVM | 66 passed, 0 failed, 0 skipped |
| Android | 39 passed, 0 failed, 0 skipped; 429.532 s |
| Lint | 0 errors, 90 warnings |
| Python | 52 passed |
| Inference-only import | MobileNet content URI, stale profile digest accepted, 3 native proposals, no training |
| Internal-weight training | Synthetic network: 48 Android optimizer steps, changed internal weights, exact checkpoint reload |
| Exported-batch training | 88 accepted images, 8 rejected excluded; cancellation at step 10, resume to 198; cleanup after completion |

Les 39 tests comprennent les projets, lots, conservation des annotations, exports, SAF injecté, Room, masques, UI anglaise, workflows, modèles publics, prompts HTTP de recette, apprentissage et reprise. Les classes nécessitant d’autres conversions, la photo réelle et l’autorisation HF sont explicitement séparées, pas comptées comme réussies.

Le test photo TinyCLIP a passé sur le build `20260922T211013Z-a83cfea465e4` : un changement de prompt/réglage et une préannotation ordinaire préservent les annotations existantes ; la relance explicite change les propositions en conservant la correction humaine. Seuls le reçu numérique et les journaux sont publiés, sans photo ni capture.

La campagne de conversions est distincte : 12 réussites, dont 7 avec apprentissage ; un délai RF-DETR dépassé sur émulateur, et des conversions encore en recette. Les modèles privés ne figurent pas dans ces reçus publics. Les couches visuelles des conversions HF entraînables restent figées. Aucun gain de précision, mesure téléphone ARM, publication HF réelle ni succès CI n’est revendiqué. Le package OCI est une archive de qualification, pas une image exécutable.

## English

The 39 Android tests cover projects, batches, annotation preservation, exports, injected SAF faults, Room, masks, English UI, workflows, public models, the HTTP prompt fixture, learning and resume. Other converted models, real-photo inference and authorized live HF writes are explicitly separate; excluded tests are not counted as passes.

The TinyCLIP photo test passed on build `20260922T211013Z-a83cfea465e4`: prompt/settings changes and ordinary preannotation preserve saved annotations. Explicit rerun changes model proposals while preserving the human correction. Only numeric receipts and logs are published; the photo and screenshots are excluded.

The conversion campaign is separate: 12 passes, including 7 with learning; RF-DETR reached the software-emulator timeout, and further conversions remain under test. Private model identifiers and weights are excluded. The supplied trainable HF conversions still freeze their visual encoders. No accuracy gain, ARM phone benchmark, real HF publication test or CI success is claimed. The OCI package is a qualification archive, not a runnable image.

## Evidence

- [Build and APK inventory](final-build/status.json), [compiled-source provenance](source-manifest.json).
- [JVM/lint](jvm-lint.json), [Android instrumentation](final-device/instrumentation.txt).
- [Inference without training or SHA gate](inference-only.json).
- [Native training](training-internal-weights.json), [exported-batch lifecycle](training-workflow.json).
- [Real-photo and preservation receipt](../native-photo-audit8/native-photo-result.json), [its actual build](../native-photo-audit8/build-receipt.json).
- [Public conversion receipts](../public-model-results.json), [current public runtime-artifact equivalence](../public-revision-equivalence.json).
