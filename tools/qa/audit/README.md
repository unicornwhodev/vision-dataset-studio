# Tests ciblés de complétude

Ces tests accompagnent [l’audit](../../../docs/IMPLEMENTATION_AUDIT.md).
Ils n’activent aucun entraînement visuel ni serveur d’agent. Ils n’écrivent pas
sur Hugging Face. Les deux régressions rouges restent volontairement visibles.

## Android : huit tests d’intégration

`FeatureImplementationAuditTest.kt` utilise les composants réels de l’application :
trois poids publics (~13 Mo), Bitmap, Interpreter CPU, Room, exporteurs, client HTTP,
Moshi et AtomicFile. L’image est synthétique et le répondeur HTTP est une fixture,
pas un modèle. La base du test de lot est en mémoire. Les fichiers sont isolés sous
`files/feature-audit/` et les préférences du test ont un préfixe propre.

Exécuter sur le pod avec le SDK/JDK déjà installés et un appareil de recette.
Le test APK doit être construit avec la même source et la même clé Debug que
l’application installée. Ne pas installer une APK principale différente pour
réétiqueter une ancienne preuve. Relever son empreinte réelle avant l’essai :

```bash
source /workspace/toolchains/android-env.sh
export ANDROID_SERIAL=emulator-5554
adb shell -n pm path com.unicornwhodev.visiondatasetstudio
# Remplacer BASE_APK par le chemin retourné ci-dessus, sans le préfixe package:.
adb exec-out cat BASE_APK | sha256sum
```

Depuis la racine du dépôt, ajouter temporairement le test, assembler uniquement
le test APK puis supprimer l’overlay :

```bash
(
  set -euo pipefail
  audit_src=app/src/androidTest/java/com/unicornwhodev/visiondatasetstudio/FeatureImplementationAuditTest.kt
  test ! -e "$audit_src"
  trap 'rm -f "$audit_src"' EXIT
  cp tools/qa/audit/FeatureImplementationAuditTest.kt "$audit_src"
  ./gradlew :app:assembleDebugAndroidTest
)
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell -n am instrument -w -r \
  -e class com.unicornwhodev.visiondatasetstudio.FeatureImplementationAuditTest \
  -e audit_download_models true \
  com.unicornwhodev.visiondatasetstudio.test/androidx.test.runner.AndroidJUnitRunner
```

Le flag `audit_download_models true` est obligatoire ; sans lui les tests sont
ignorés, pas réussis. Vérifier `OK (8 tests)` et chaque statut d’instrumentation,
pas seulement le code de retour d’ADB. L’exécution du 19 septembre a été réalisée
en deux suites : six premiers tests, puis les deux tests supplémentaires du pack
et du correcteur. Les deux empreintes de test APK sont dans le reçu de l’audit.

Les rapports sont récupérables individuellement, sans extraire la base ni le coffre :

```bash
adb shell -n run-as com.unicornwhodev.visiondatasetstudio ls files/feature-audit
adb exec-out run-as com.unicornwhodev.visiondatasetstudio \
  cat files/feature-audit/export.json
```

Le champ `zip` de ce dernier rapport donne le chemin privé à récupérer par
`adb exec-out run-as PACKAGE cat CHEMIN`. Après les essais, l’APK de tests du
reçu de build peut être réinstallée ; l’APK principale et ses données ne changent pas.

## Origine des corrections de boîtes : test actuellement rouge

```bash
(
  set -euo pipefail
  audit_tmp="$(mktemp -d)"
  trap 'rm -rf "$audit_tmp"' EXIT
  kotlinc tools/qa/MetadataAnnotation.kt tools/qa/audit/CorrectionProvenanceAudit.kt \
    app/src/main/java/com/unicornwhodev/visiondatasetstudio/core/workflow/{StudioWorkflow,ProcessingSettings}.kt \
    app/src/main/java/com/unicornwhodev/visiondatasetstudio/data/model/DatasetModels.kt \
    app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/inference/{ModelConfig,ModelAdapters,TensorCodec,ProposalMerger,AdaptiveCorrection}.kt \
    -include-runtime -d "$audit_tmp/correction.jar"
  java -jar "$audit_tmp/correction.jar"
)
```

Attendu sur la source auditée : sortie non nulle, `Raw xmin=0.2; corrected xmin=0.23;
recorded modelXmin=0.23`. Le test exprime l’invariant attendu sans modifier le moteur.
`MetadataAnnotation.kt` est uniquement la fixture d’annotation Moshi déjà utilisée
par les tests numériques ; ce test ne remplace pas une compilation Android.

## Vérifier la véritable archive Android

```bash
python3 tools/qa/audit/check_android_export.py /chemin/export-fixture.zip \
  --result /chemin/export-host-validation.json
```

Le contrôle est volontairement spécifique à la fixture `audit-export` du test
Android. Il appelle d’abord le validateur livré sans le modifier, puis vérifie
indépendamment CRC, manifeste, empreintes, contenu canonique, COCO, YOLO et TAR.
Sur la source auditée, l’intégrité passe mais le validateur livré échoue : le
script sort donc avec le code 1. Ne pas renommer les dossiers pour fabriquer un succès.
