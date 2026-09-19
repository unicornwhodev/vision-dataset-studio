# Vision Dataset Studio — V4.2 RC1 UWD

> Note historique de préparation RC1. L’état courant de compilation, licence,
> exécution et complétude figure dans [TEST_REPORT.md](TEST_REPORT.md),
> [LICENSING_STATUS.md](LICENSING_STATUS.md) et [l’audit](docs/IMPLEMENTATION_AUDIT.md).

**19 septembre 2026 — sources renforcées, aucune APK construite dans cet environnement.**

Cette passe conserve l’architecture V4.1 et termine les changements fonctionnels discutés autour du catalogue de modèles, du travail partagé et de l’apprentissage des corrections géométriques. Elle ne réintroduit aucune dépendance FireViewer.

## Ajouts principaux

### Bibliothèque de modèles

- nouvelle destination principale **Modèles** dans la navigation ;
- catalogue distant `Charlbi/Lite_rt_prepared_for_android_dataset_builder` lu directement depuis Hugging Face ;
- révision du dépôt modèle résolue en SHA avant téléchargement ;
- seuls les artefacts réellement présents sont annoncés comme disponibles ;
- DINOv2 peut être enregistré comme encodeur `embedding` sans inventer d’annotation ;
- ViTPose+ peut recevoir un contrat heatmap après inspection des tenseurs ;
- les modèles mono-fichier inconnus peuvent être téléchargés en mode `inspect_only` ;
- les bundles multi-graphes restent visibles mais ne sont pas présentés comme exécutables avant support réel de leur chaîne de calcul ;
- import manuel `.tflite` et URL HTTPS conservés.

Aucun poids n’est embarqué dans l’application et le téléchargement n’active jamais automatiquement un modèle.

### Plusieurs personnes sur le même dataset

Un projet HF peut activer une coordination coopérative. Avant téléchargement des images, les cas sont associés à des fichiers de réservation déterministes dans le dépôt HF de destination.

- `CLAIMED` actif par un autre collaborateur : cas ignoré ;
- `DONE` : cas ignoré ;
- claim expiré : cas récupérable ;
- claim encore actif du même collaborateur : réutilisable ;
- les écritures utilisent le parent de commit attendu et relisent après conflit ;
- la fin d’un lot tente d’écrire `DONE` pour ses cas terminés.

Ce système évite le travail en double dans un workflow coopératif, mais ce n’est pas une édition collaborative temps réel. Un bail expiré peut rendre à nouveau disponible un lot abandonné.

### Correction adaptative

Le correcteur local déjà prévu pour les points couvre maintenant aussi les boîtes explicitement corrigées puis validées. Il apprend des résidus bornés de centre et dimensions, isolés par modèle/contrat/classe. Une proposition simplement acceptée n’est toujours pas un exemple supervisé.

## Interface

La navigation principale comporte Atelier, Lot, Modèles, Export et Qualité. La bibliothèque de modèles a ses propres vues **Disponibles / Installés / Importer**. Les réglages avancés restent séparés dans « Moteur et transferts » afin que le parcours courant ne soit pas encombré par les contrats JSON et diagnostics.

Le thème Material 3, les surfaces arrondies, le contraste clair/sombre, les états textuels et les mises en page adaptatives sont conservés. Les nouveaux contrôles de travail partagé restent optionnels et n’apparaissent en détail qu’après activation.

## Vérifications exécutées

- 44 tests de règles Kotlin ;
- 67 tests du moteur numérique, avec 1 500 transformations aléatoires incluses ;
- 43 tests JVM des helpers de fiabilité ;
- 9 tests SQLite hôte ;
- 6 tests du validateur d’export ;
- 12 tests d’identité/configuration ;
- 15 tests de preuves de build ;
- **196 tests portables réussis** au total ;
- 75 fichiers Kotlin/KTS analysés par le parseur PSI, zéro erreur syntaxique.

Le build Android a été relancé mais s’arrête **avant compilation**, car aucun SDK Android n’est installé dans l’environnement de préparation. Il n’existe donc toujours aucune preuve de typage Compose/Room/Moshi/KSP, d’installation, de test instrumenté, de catalogue HF réel ou de concurrence réelle entre deux appareils.

## Statut

V4.2 RC1 est un candidat source plus complet, pas encore une release Android qualifiée. La licence UWD reste également à décider avant publication publique.
