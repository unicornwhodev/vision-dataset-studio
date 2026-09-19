# Roadmap

La version de travail est **4.2.0-rc2**. Une étape ne passe à « validée » qu'avec
une commande exécutée, son résultat et les artefacts correspondants.

## 1. Poste de travail reproductible

- Importer l'archive avec vérification SHA-256 et conserver la source initiale.
- Installer JDK, SDK Android, Gradle, Kotlin, ADB et outils de contrôle graphique.
- Conserver sources, SDK, caches et preuves dans le volume persistant.
- Fournir les commandes de build, test, démarrage d'émulateur et connexion.

## 2. Qualification Android P0

État du 19 septembre : APK et schéma produits, 25/26 tests JVM réussis, lint sans
erreur. Priorité : diagnostiquer/corriger la reprise HTTP interrompue puis refaire
le build complet. Corriger le chargement Kotlin du fournisseur SAF de test et
réexécuter la suite instrumentée entière. Corriger aussi le bouton Importer comprimé sur écran étroit.
Les détails et les échecs instrumentés sont dans `POD_VALIDATION.md`.


- Résoudre les dépendances sans substitution silencieuse.
- Compiler APK Debug et APK de tests ; vérifier signatures, identité et SHA-256.
- Générer réellement les schémas Room avec KSP.
- Exécuter tests JVM dépendant d'Android, migrations Room, Moshi, Compose et lint.
- Installer les APK issues du reçu de build sur des appareils de test dédiés.
- Exécuter les tests instrumentés API 28 et 35 et collecter logs/captures.

## 3. Recette fonctionnelle P1

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
- Améliorations ergonomiques guidées par les parcours observés, sans refonte
  préalable de l'interface existante.
