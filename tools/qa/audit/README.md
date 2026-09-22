# Recette ciblée / Targeted audit

Le [rapport historique](../../../docs/IMPLEMENTATION_AUDIT.md) décrit la source du 19 septembre. La [recette actuelle](../../../docs/FUNCTIONAL_AUDIT_2026_09.md) distingue les correctifs et les nouvelles exécutions. Les anciennes régressions de provenance et d’export ne doivent plus être présentées comme des défauts ouverts sur la source courante.

The [historical report](../../../docs/IMPLEMENTATION_AUDIT.md) describes the 19 September source. The [current audit](../../../docs/en/FUNCTIONAL_AUDIT_2026_09.md) separates fixes from newly executed evidence. Historical provenance/export failures are not current open defects.

## Android

Les huit essais sont intégrés à [FeatureImplementationAuditTest](../../../app/src/androidTest/java/com/unicornwhodev/visiondatasetstudio/FeatureImplementationAuditTest.kt). Ils utilisent trois modèles publics (~13 Mo), Bitmap, Interpreter CPU, Room, exporteurs et Moshi. L’image géométrique et la réponse HTTP sont synthétiques. Aucun accès HF en écriture. Ne pas réinstaller une ancienne APK pour lui attribuer les preuves d’une nouvelle source.

The eight tests are part of the normal test APK. They use three public models (~13 MB), real Android inference/storage/export components, a synthetic image and an HTTP responder fixture. No Hub writes. Install the main and test APKs from the same verified build receipt.

```bash
source /workspace/toolchains/android-env.sh
export ANDROID_SERIAL=emulator-5554
adb shell am instrument -w -r \
  -e class com.unicornwhodev.visiondatasetstudio.FeatureImplementationAuditTest \
  -e audit_download_models true \
  com.unicornwhodev.visiondatasetstudio.test/androidx.test.runner.AndroidJUnitRunner
```

Sans `audit_download_models=true`, les huit essais sont **ignorés**. Le 22 septembre, les huit ont réussi sur le build `20260922T203037Z-00cb2a227a15`. Vérifier les statuts et `OK (8 tests)`, pas seulement le code de sortie ADB.

Without the opt-in, the eight tests are **skipped**. All eight passed on 22 September using build `20260922T203037Z-00cb2a227a15`. Check instrumentation statuses and the JUnit result, not ADB's exit code alone.

Les rapports sont sous `files/feature-audit/` dans l’espace privé de l’application. `export.json` donne le chemin exact de l’archive Android ; récupérer uniquement cet artefact avec `adb exec-out run-as PACKAGE cat PATH`, sans exporter la base ou le coffre de l’application.

Reports are under the app-private `files/feature-audit/`. Use `export.json` to retrieve the exact produced ZIP; do not dump the app database or credential vault.

## Archive et provenance / Archive and provenance

```bash
python3 tools/qa/audit/check_android_export.py /path/android-export.zip \
  --result /path/export-validation.json
bash tools/qa/run_engine_tests.sh
```

Le validateur indépendant est spécifique à la fixture `audit-export`. Sur la nouvelle archive Android, le validateur livré, les CRC, les dix empreintes du manifeste, le contenu canonique, COCO, YOLO et TAR passent. Le test autonome `CorrectionProvenanceAudit.kt` est conservé ; les règles de provenance et d’idempotence sont aussi couvertes par la suite moteur.

The independent archive checker targets the `audit-export` fixture. The new Android ZIP passes the shipped validator, CRCs, ten manifest digests, canonical content, COCO, YOLO and TAR checks. `CorrectionProvenanceAudit.kt` remains available; the engine suite also covers original-coordinate provenance and calibration idempotence.
