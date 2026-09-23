# TensorFlow Flex et pages Android de 16 Ko

Après la publication rc5, la [campagne P1](P1_QUALIFICATION_2026_09.md#les-signalements-natifs)
a corrigé le signalement DataStore et documenté les deux bibliothèques restantes.
Le contrôle strict de l’APK reste inchangé ; la recette 16 Ko demeure sur émulateur.

Le binaire Maven d'origine `tensorflow-lite-select-tf-ops:2.16.1` est compilé avec des segments
de 4 Ko. Son chargement plantait sur l'émulateur API 35 / 16 Ko pendant
`OnDeviceTrainingTest.internalVisualWeightsTrainPersistAndReloadOnAndroid`.
L'alignement ZIP de l'APK seul ne corrige pas ce défaut.

Le projet reconstruit **les sources TensorFlow 2.16.1**, avec Bazel 6.5.0 et le
NDK r25b, pour `arm64-v8a` et `x86_64`. Les options de l'éditeur de liens sont :

```text
-Wl,-z,max-page-size=16384
-Wl,-z,common-page-size=16384
```

Tous les opérateurs Flex sont conservés, y compris les gradients et Save/Restore.
L'API Java, les notices et les binaires 32 bits proviennent de l'AAR officiel
2.16.1 dont l'empreinte est vérifiée. Les bibliothèques 64 bits sont réellement
recompilées, puis dépouillées de leurs symboles de débogage ; leurs en-têtes ELF
ne sont pas réécrits. LiteRT Interpreter 1.4.2 reste inchangé.

## Reconstruire le runtime

Le package `vision-dataset-studio-flex-2.16.1-vds16k1.zip` de la prérelease rc5
contient aussi le runtime vérifié. Après vérification avec `SHA256SUMS`, extraire
ses chemins `dist/native-flex/maven/...` à la racine du clone. Le build compare
le reçu, la recette et l’AAR avant usage. La reconstruction source ci-dessous
reste disponible ; aucun binaire natif n’est stocké dans Git.

Prérequis Linux x86_64, Ubuntu 22.04 testé : `build-essential`, `python3-dev`,
`python3-numpy`, `unzip`, `zip`, `curl`, `git`, `ca-certificates`. Fournir un dossier
`licenses` de SDK Android déjà accepté. Le script n'accepte pas de licence et
n'effectue aucune publication distante. Les gros fichiers générés restent dans
`dist`, hors Git. Prévoir plusieurs Go et plusieurs minutes de compilation.

```bash
python3 tools/build_flex_runtime.py \
  --work-dir "$HOME/.cache/vds-flex" \
  --android-sdk-licenses "$ANDROID_HOME/licenses"
```

Sur le poste Windows de qualification, la distribution WSL dédiée
`VDS-Flex-Build` contient les outils Linux. Depuis PowerShell :

```powershell
wsl -d VDS-Flex-Build -- python3 /mnt/d/Dev/project/vision-dataset-studio/tools/build_flex_runtime.py --work-dir /opt/vds-flex --downloads /mnt/d/Dev/project/vision-dataset-studio/dist/native-flex/downloads --android-sdk-licenses /mnt/d/Programs/Android/SDK/licenses
python -X utf8 tools/build_android.py
```

Un autre poste doit fournir son environnement Linux/WSL et adapter ces chemins.
La compilation Android ordinaire peut ensuite être refaite sous Windows à partir
de ce runtime local, sans reconstruire TensorFlow à chaque fois.

Chaque archive source/outillage est épinglée par SHA-256 dans
`tools/build_flex_runtime.py`. Chaque tentative conserve ses commandes, journaux,
empreintes et sorties `llvm-readelf` dans `dist/native-flex/runs/`. Seule une
tentative réussie installe l'artefact local Maven
`org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1-vds16k1`. Gradle ne peut pas
revenir silencieusement à l'ancien binaire.

Les scripts Debug/Release vérifient le reçu, le SHA de l'AAR et les segments ELF.
Le reçu Android consigne également le SHA du runtime effectivement utilisé.
La CI prépare ce même runtime une fois sous Linux, puis le fournit aux builds
Linux et Windows ; son cache est indexé sur les scripts de construction/contrôle.

## Recette obligatoire

```powershell
$env:VDS_ALLOW_TEST_INSTALL='1'
python -X utf8 tools/qa/run_device_qualification.py --serial emulator-5582 --expected-page-size 16384 --training-fixture dist/fixtures/training-fixture --tokenizer-fixture dist/fixtures/hf-runtime-fixture --output test-results/flex-16k
```

La mesure réelle `getconf PAGE_SIZE`, les résultats JUnit et le reçu d'apprentissage
sont conservés. Le test entraîne les poids internes, réduit la perte, sauvegarde
un checkpoint puis retrouve les mêmes poids et prédictions après rechargement.
La suite complète doit aussi repasser sur l'émulateur 4 Ko.

Le contrôle Flex ne qualifie pas les autres bibliothèques natives de l'APK.
`tools/qa/check_apk_page_sizes.py` reste l'audit global distinct. Un émulateur
x86_64 ne qualifie pas le fonctionnement ni les performances d'un téléphone ARM.

## Résultats du 23 septembre 2026

Le crash Flex est corrigé : les suites Android de base passent **39/39 sur API 35
en 4 Ko et 39/39 en 16 Ko**, sans test ignoré. L'apprentissage, la modification des
poids internes, la sauvegarde, la restauration et l'enchaînement de deux lots sont
exécutés. Les APK et reçus exacts sont dans [le rapport Windows](WINDOWS_QUALIFICATION_2026_09.md).

Le runtime provient de `dist/native-flex/runs/20260923T100533Z-9ec1f3d7/`.
SHA-256 de l'AAR : `9a41568edb27d4e03c30446b1ab55903811bdbb81d9a13a7aa80067523726115`.
Le Flex ARM64 a aussi exécuté le test d'apprentissage sous traduction native de
l'émulateur x86_64 en pages 16 Ko, avec `primaryCpuAbi=arm64-v8a` vérifié.
Il s'agit d'une exécution traduite, pas d'un téléphone ni d'un benchmark ARM.

L'audit statique global reste ouvert : les fins RELRO de
`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so` et
`libtensorflowlite_jni.so` ne satisfont pas le contrôle strict en ARM64 et x86_64.
Les segments LOAD et RELRO des deux Flex reconstruits satisfont ce contrôle.
Le succès de la recette 16 Ko ne supprime pas ces six signalements statiques.

La reconstruction complète augmente la taille du runtime. Aucune sélection
d'opérateurs, suppression de gradients ou modification d'en-têtes ELF n'a été
utilisée pour diminuer artificiellement les fichiers.

Références : [construction Flex officielle](https://developers.google.com/edge/litert/conversion/tensorflow/ops_select),
[défaut amont confirmé](https://github.com/tensorflow/tensorflow/issues/94048#issuecomment-2915281663),
[exigences LOAD/RELRO Android](https://developer.android.com/guide/practices/page-sizes).
