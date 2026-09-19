# Vision Dataset Studio — Unicorn Who Dev

**4.2.0-rc2 · Android · applicationId `com.unicornwhodev.visiondatasetstudio`**

Atelier mobile générique de préparation de datasets image pour la vision par ordinateur et les corpus vision-langage. Le projet configure ses tâches, classes, sources, lots, modèles et exports, sans domaine imposé.

**Version en qualification sur le poste Runpod.** Les résultats courants sont consignés dans [le rapport du pod](docs/POD_VALIDATION.md). Les tests portables ne remplacent pas la recette Android, les essais sur téléphone et les échanges HF réels.

Licence du code : **Apache-2.0**, choisie par le propriétaire. Les modèles, datasets et composants tiers conservent leurs propres conditions.

## Périmètre conservé

- Projets indépendants, lots configurables de 1 à 1 000 cas ; sources HF Viewer, manifeste HF, dossier ou manifeste local. Réservations HF facultatives pour plusieurs annotateurs afin d’ignorer les cas déjà terminés ou en cours chez un autre collaborateur.
- Pointing, détection, classification/tags, captions, grounding, questions-réponses, comptage et qualité ; les corrections humaines restent distinctes des propositions.
- Modèles LiteRT CPU avec contrats explicites, bibliothèque locale et catalogue Hugging Face UWD découvert dynamiquement puis téléchargé volontairement ; client HTTP limité au même appareil.
- Annotations canoniques et projections d’export, publication HF avec parent conservé, preuves de copie locale/distante, purge explicite et reprenable.

Les contrats décrivent ce qui est interprété ; ils ne promettent pas la compatibilité avec tous les exports portant le même nom de modèle. Aucune sortie IA ne valide automatiquement un échantillon. Les poids, données personnelles, tokens et clés de signature ne sont pas inclus.

## Identité et données existantes

Namespace, applicationId, sources, tests et scripts utilisent le même identifiant UWD. Le thème et les noms des tests de démarrage ne sont plus ceux du template.

**Le changement d’applicationId crée une autre application Android.** Ce n’est pas une mise à jour sur place d’une installation portant un ancien identifiant. Ses données privées et autorisations SAF ne sont pas transférées automatiquement. Ne pas désinstaller une ancienne application contenant des données avant d’en avoir vérifié la sauvegarde. Les migrations Room 1 → 2 → 3 sont conservées mais ne font pas migrer les données entre deux applicationIds. Aucun import inter-application complet n’est implémenté.

## Poste de travail préparé

Le [guide du poste Runpod](docs/WORKSTATION.md) décrit les outils persistants, VS Code, ADB et le contrôle visuel de l’émulateur. La [roadmap](docs/ROADMAP.md) et le [plan de publication](docs/RELEASE_PLAN.md) séparent préparation, qualification et distribution.

## Construire et tester

Prérequis Linux/macOS : JDK 17+, Python 3.11+, Android SDK `platforms;android-36` et `build-tools;36.0.0`, accès aux dépôts officiels. Gradle 9.3.1 est vérifié ; le lanceur fourni est un bootstrap Python et non le wrapper JAR officiel.

```bash
bash tools/build_android.sh
```

Chaque tentative crée `dist/android/runs/<identifiant>/status.json` et actualise `dist/android/latest.json`, même lorsqu’un prérequis manque. Seuls des APK réellement assemblés, vérifiés par `apksigner` et contrôlés avec `aapt` sont copiés dans cette tentative. Les anciennes preuves ne sont pas supprimées ni réétiquetées comme un succès du nouveau build.

Le workflow manuel `.github/workflows/android-qualification.yml` prévoit build, tests JVM avec dépendances, lint, APK instrumentée, puis exécution des tests instrumentés sur des émulateurs dédiés API 28 et 35. Les APK installés sont ceux du build, avec empreintes vérifiées, sans recompilation avec une autre clé. Le workflow accepte explicitement les licences SDK lorsqu’il est lancé par l’opérateur ; il ne publie rien et n’utilise aucun token HF. **Il n’a pas été déclenché pour cette livraison.**

```bash
python3 tools/qa/test_identity.py
python3 tools/qa/test_build_evidence.py
bash tools/qa/run_policy_tests.sh
bash tools/qa/run_engine_tests.sh
bash tools/qa/run_v4_reliability.sh
python3 tools/qa/test_v4_sqlite.py
python3 tools/qa/test_export_validator.py
```

Ces tests hôte ne remplacent pas Room, Compose, Moshi ou SAF sur Android. Les résultats courants sont dans `TEST_REPORT.md` et `docs/POD_VALIDATION.md` ; les preuves brutes sont conservées hors Git sur le pod.

## Avant publication

Lire `LICENSING_STATUS.md`, `NOTICE`, `KNOWN_LIMITATIONS.md`, `docs/ANDROID_QUALIFICATION.md` et `QUALIFICATION_STATUS.json`. La licence Apache-2.0 a été choisie. Il reste à vérifier les droits des composants et les notices transitives, puis à terminer la recette réelle en s’appuyant sur les résultats du rapport du pod. Changer le branding ne prouve pas à lui seul la titularité des droits sur du code antérieur.
