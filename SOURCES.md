# Références techniques — qualification UWD 4.2 RC1

Consultées le 19 septembre 2026. Ces documents ne prouvent pas l’exécution de notre code.

- Android/Gradle : https://developer.android.com/build/releases/agp-9-1-0-release-notes ; https://developer.android.com/build/releases/about-agp
- Migrations Room : https://developer.android.com/training/data-storage/room/migrating-db-versions (le projet reste Room 2.7 ; ne pas transposer aveuglément les exemples d’une version plus récente).
- API Hub : https://huggingface.co/docs/hub/api
- Modèles officiels de détection : https://raw.githubusercontent.com/tensorflow/examples/master/lite/examples/object_detection/android/app/download_models.gradle
- Modèles officiels de classification : https://raw.githubusercontent.com/tensorflow/examples/master/lite/examples/image_classification/android/app/download_models.gradle
- MetadataExtractor : https://developers.google.com/edge/api/tflite/java/org/tensorflow/lite/support/metadata/MetadataExtractor
- Schéma des métadonnées : https://github.com/tensorflow/tflite-support/blob/master/tensorflow_lite_support/metadata/metadata_schema.fbs
- Gradle Actions : https://github.com/gradle/actions ; https://docs.gradle.org/current/userguide/github-actions.html

Les poids du catalogue ne sont ni redistribués ni testés ici ; les liens officiels sont une provenance, pas une licence universelle ni une certification de compatibilité.

## Références vérifiées pour cette passe

- Identité Android et namespace : https://developer.android.com/build/configure-app-module
- Compatibilité AGP 9.1.1 / Gradle 9.3.1 / JDK 17 / KGP 2.2.10 : https://developer.android.com/build/releases/agp-9-1-0-release-notes
- SDK en CI : https://github.com/android-actions/setup-android
- Émulateurs instrumentés en CI : https://github.com/ReactiveCircus/android-emulator-runner
- Statut de licence d’un dépôt : https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository

La lecture de ces documentations ne prouve ni la résolution Maven ni l’exécution du workflow.

- SHA-256 Gradle 9.3.1 binaire épinglé dans le bootstrap : https://gradle.org/release-checksums/
- Catalogue de conversions LiteRT proposé dans l’application : https://huggingface.co/Charlbi/Lite_rt_prepared_for_android_dataset_builder (disponibilité vérifiée dynamiquement par l’app ; aucun poids embarqué dans le dépôt Android).
