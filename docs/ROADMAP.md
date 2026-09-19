# Roadmap

La version de travail est **4.2.0-rc2**. Une étape ne passe à « validée » qu'avec
une commande exécutée, son résultat et les artefacts correspondants.

## Priorités issues de l’audit de complétude

L’[audit du 19 septembre](IMPLEMENTATION_AUDIT.md) distingue les capacités
existantes des fonctions demandées encore absentes. Les éléments ci-dessous
sont **à réaliser**, pas des fonctionnalités déjà disponibles.

| Ordre | Livraison concrète | Critère d’acceptation |
|---|---|---|
| 1 | Réparer la provenance des boîtes corrigées (F01) | Coordonnées brutes conservées à travers sauvegarde, correction humaine et deux générations ; règle explicite pour les anciens exemples sans origine fiable. |
| 1 | Réparer la découverte des exports dans le validateur (F02) | Une archive produite par Android passe sans renommage ; anciens noms pris en charge ; mauvaises empreintes et chemins invalides toujours refusés. |
| 1 | Réparer reprise HTTP et fournisseur SAF de tests | Build complet vert, test de reprise réellement réussi, suite SAF terminée avec pannes injectées. |
| 2 | Rendre le catalogue conforme à ses capacités | États distincts pour téléchargement, inspection et préannotation ; disponibilité au SHA constaté ; contrat et normalisation vérifiés pour chaque modèle activable. |
| 2 | Terminer les modèles communautaires utiles | ViTPose et détecteurs testés sur images autorisées ; vecteurs d’embedding accessibles et persistés avant d’annoncer similarité/doublons ; bundles seulement après implémentation et essais de leurs graphes/tokenizers. |
| 3 | Chaîne d’entraînement à partir des corrections validées | Export supervisé versionné avec origine et hash du modèle, séparation apprentissage/validation/test, absence de propositions non revues ; entraînement sur le pod existant, métriques comparées au modèle précédent, poids versionnés et retour arrière. |
| 3 | Boucle continue contrôlée | Déclencheur après un seuil de nouvelles corrections, état de tâche/progression/reprise visible, annulation ; conversion LiteRT et compatibilité Android testées avant proposition d’activation. Aucune validation humaine créée par le modèle. |
| 4 | Moteur de workflows et templates exécutables | Schéma de version/étapes/paramètres, import/export et reprise ; template import → préannotation → revue → export exécuté entièrement ; entraînement ajouté comme étape observable. |
| 4 | Agent avec prompts et consignes versionnés | Backend réel configuré et joignable, consigne système/projet/tâche, sorties structurées validées, outils du studio explicitement branchés ; essai sur serveur réel, journal des actions et erreurs. Le test du répondeur HTTP ne suffit pas. |
| 5 | Recette produit et publication | Cycle local 2 + 1 avec copie relue/purge/reprise, HF sur destination de test autorisée, appareil ARM, CI API 28/35 ; publication du commit et des seuls artefacts correspondants. |

L’entraînement continu reste distinct de la régression géométrique actuelle.
Il faut conserver un jeu d’évaluation indépendant et un modèle précédent utilisable
pour juger une nouvelle version. Les seuils de promotion et le modèle entraînable
seront documentés avec les résultats du corpus choisi, sans inventer de gain.

## 1. Poste de travail reproductible

- Importer l'archive avec vérification SHA-256 et conserver la source initiale.
- Installer JDK, SDK Android, Gradle, Kotlin, ADB et outils de contrôle graphique.
- Conserver sources, SDK, caches et preuves dans le volume persistant.
- Fournir les commandes de build, test, démarrage d'émulateur et connexion.

## 2. Qualification Android P0

État du 19 septembre : APK et schéma produits, 25/26 tests JVM réussis, lint sans
erreur. Priorité : diagnostiquer/corriger la reprise HTTP interrompue puis refaire
le build complet. Corriger le chargement Kotlin du fournisseur SAF de test et
réexécuter la suite instrumentée entière. La refonte corrige les onglets sur écran
étroit ; ses deux tests Compose passent sur API 28.
Les détails et les échecs instrumentés sont dans `POD_VALIDATION.md`.


- Résoudre les dépendances sans substitution silencieuse.
- Compiler APK Debug et APK de tests ; vérifier signatures, identité et SHA-256.
- Générer réellement les schémas Room avec KSP.
- Exécuter tests JVM dépendant d'Android, migrations Room, Moshi, Compose et lint.
- Installer les APK issues du reçu de build sur des appareils de test dédiés.
- Exécuter les tests instrumentés API 28 et 35 et collecter logs/captures.

## 3. Recette fonctionnelle P1

La refonte sombre cyan/violet est implémentée ; la composition et les preuves
de contrôle sont dans [UI_REDESIGN.md](UI_REDESIGN.md).

- Parcourir Atelier, Lot, Modèles, Export, Qualité et réglages sans crash.
- Exécuter le cycle local sur les fixtures : lots 2 + 1, corrections, export,
  copie relue, purge et reprise ; protéger les annotations humaines.
- Vérifier portrait, paysage, grande police, tablette et accessibilité.
- Tester stockage SAF révoqué, manque d'espace et interruptions.
- Tester téléchargement et inférence d'un modèle autorisé sur appareil ARM réel.
- Tester HF uniquement sur un dépôt privé de test explicitement autorisé,
  notamment concurrence, conflit et réponse perdue.
- Mesurer mémoire et latence sur téléphone ; séparer ces mesures de l'émulateur.

La matrice détaillée et les critères de sortie restent dans
[ANDROID_QUALIFICATION.md](ANDROID_QUALIFICATION.md).

## 4. Dépôt, paquets et releases

- Destination choisie : dépôt public `unicornwhodev/vision-dataset-studio`.
- Licence choisie : Apache-2.0 ; conserver les notices de dépendances.
- Mettre README, guide de build, architecture, limitations et résultats à jour.
- Publier uniquement le commit correspondant aux artefacts contrôlés.
- Produire le paquet de qualification (APK, empreintes et preuves).
- Préparer une release candidate explicitement marquée prerelease tant que P1
  n'est pas terminé ; ne pas présenter une APK Debug comme release signée.
- Pour une distribution stable, définir et sauvegarder la clé de signature hors
  du dépôt, construire APK/AAB release, puis répéter les contrôles adaptés.
- Pour GitHub Packages, préparer un conteneur GHCR de l'environnement de build ;
  les APK sont des assets de GitHub Releases, pas un registre Android natif.

## 5. Évolutions après qualification

- Automatisation de la matrice de non-régression et des preuves de livraison.
- Import inter-application explicite et vérifié des données existantes.
- Support des modèles multi-entrées uniquement après validation des contrats.
- Ajustements ergonomiques guidés par les usages réels de l’atelier et de l’éditeur.
