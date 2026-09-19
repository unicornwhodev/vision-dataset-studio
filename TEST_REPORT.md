# Tests — passe UWD 4.2.0-rc1

Exécutions effectuées le 19 septembre 2026 après l’ajout du catalogue LiteRT UWD, des réservations coopératives HF et de la correction adaptative des boîtes. JDK hôte : OpenJDK 21.0.11. La CI préparée vise JDK 17.

## Résultats réellement obtenus

| Suite | Tests réussis | Preuve |
|---|---:|---|
| Règles Kotlin | 44 | `test-results/v4.2/policy-tests.txt` |
| Moteur numérique Kotlin | 67 | `test-results/v4.2/engine-tests.txt` |
| Helpers de fiabilité JVM | 43 | `test-results/v4.2/reliability.txt` |
| SQLite hôte / SQL de migration | 9 | `test-results/v4.2/sqlite.txt` |
| Validateur des exports | 6 | `test-results/v4.2/export-validator.txt` |
| Identité, chemins et configuration | 12 | `test-results/v4.2/identity.txt` |
| Outillage et preuves de build | 15 | `test-results/v4.2/build-evidence-tests.txt` |
| **Total portable** | **196** | Aucun test Android inclus |

Les essais géométriques aléatoires restent inclus dans les suites Kotlin et ne sont pas comptés comme tests séparés. Les trois nouveaux tests moteur couvrent : identité stable d’un collaborateur, contrats `embedding`/`inspect_only` sans vocabulaire artificiel et correction adaptative d’une boîte.

Le parseur PSI Kotlin a analysé **75 fichiers Kotlin/KTS, avec zéro erreur syntaxique**. Ni les types Android ni les symboles Compose/Room/Moshi ne sont résolus par ce contrôle. Preuve : `test-results/v4.2/kotlin-syntax.txt`.

## Stress et migrations

Le test SQLite hôte insère et page 200 000 lignes de **métadonnées**. Il ne simule pas 200 000 images sur Android. Les migrations SQL 1 → 2 → 3 restent additives et les neuf contrôles hôte réussissent. La validation par le vrai runtime Room reste à exécuter sur build Android.

## Tentative de build Android

`bash tools/build_android.sh` a été relancé. Le contrôle préalable reconnaît OpenJDK 21 puis s’arrête avant Gradle car **aucun SDK Android n’est installé/configuré dans l’environnement de préparation**. Le reçu exact est conservé dans `test-results/v4.2/android-build-status.json`.

**Aucune compilation Android n’a démarré et aucune APK n’a été produite.** Compose, Room, Moshi/KSP, SAF, les appels HF réels, le catalogue modèle réel et les claims coopératifs doivent encore être exercés sur Android/CI.

## Nouvelles zones non qualifiées par les tests hôte

- découverte réelle du dépôt modèle `Charlbi/Lite_rt_prepared_for_android_dataset_builder` ;
- téléchargement puis exécution d’un modèle LiteRT réel ;
- compétition de deux appareils sur le même parent de commit HF ;
- expiration/récupération d’un claim abandonné avec deux clients réels ;
- ergonomie tactile et affichage des nouveaux écrans sur téléphone.

La licence du projet reste à décider avant une publication publique.
