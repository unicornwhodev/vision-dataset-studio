# Stabilisation — septembre 2026

## Second stabilization pass — 22 septembre 2026

### Fixed

- La réinitialisation d’un projet efface désormais ses identités d’image dans la même transaction Room que ses échantillons. La réinitialisation d’un lot reste limitée aux identités de ce lot et la suppression complète conserve son comportement.
- Le moteur de lot vérifie lui-même le contrat modèle/tâches avant de sélectionner ou de modifier une image. Cette garde couvre donc les appels manuels, la préannotation automatique et les workflows. Toutes les tâches actives doivent être couvertes ; `POINTING_MULTI` accepte une sortie `point`, et le grounding exige une sortie textuelle et une sortie de région.
- Les nouveaux masques du pinceau, du polygone, du lasso et du remplissage utilisent une seule fabrique de raster, bornée à 512 pixels sur le grand côté et respectant le ratio de l’image. La segmentation produite par l’adaptateur multitâche utilise la même taille.
- Un changement de projet vide maintenant la pile de navigation avant d’ouvrir les contrôles ; Retour ne peut plus rouvrir un écran du projet précédent.
- Le contrôle d’intégrité refuse maintenant tout artefact runtime/config téléchargé mais absent du manifeste ; seul le manifeste racine et la documentation non exécutable échappent à cette exigence.
- Le grounding n’est plus annoncé à partir d’une simple juxtaposition caption+région : aucun adaptateur n’est déclaré compatible tant qu’il ne produit pas le lien canonique `GroundingTarget`.
- Le preflight recalcule désormais les SHA des images, déduplique comme la préparation, exécute les vrais encodeurs de cibles et partage avec `prepare()` l’estimation incluant cibles principales et auxiliaires. Les poids configurés, présents et lisibles sont affichés séparément.
- Les quatre décisions de pointing sont distinctes dans l’inspecteur et s’appuient sur `PointTarget` et `QualityAuditTarget` existants.
- Les diagnostics enregistrent la forme/layout/dtype d’entrée, les formes/dtypes/indices de sorties réellement utilisés, la durée native et l’empreinte du contrat.
- Un collecteur borné aux répertoires `models/training-<UUID>` supprime les candidats non référencés tout en conservant modèles actifs, profils partagés et runs encore indexés.
- Les capacités des profils installés sont dérivées du contrat validé (`adapter`, sorties, bundle et signatures d’apprentissage), tandis que la qualification reste un statut indépendant. La table de qualification ne contient que les cinq succès et l’échec consignés dans `LITERT_QUALIFICATION.md`.
- Les actions principales des écrans Modèles et Apprentissage utilisent maintenant les ressources FR/EN ; un test Compose force un contexte anglais et vérifie les libellés principaux.
- Le catalogue distant lit maintenant le contrat Android épinglé (maximum 1 Mio), le valide, puis associe ses capacités théoriques avant affichage ; un contrat invalide ou inaccessible rend l’entrée non installable sans modifier son statut de qualification.
- Le preflight et `prepare()` utilisent désormais la même inspection en lecture seule : snapshot, états terminaux, SHA persisté/réel, doublons, targets principales/auxiliaires, signatures LiteRT, checkpoint et hash de profil sont contrôlés par une seule implémentation.
- La projection de masque COCO dispose d’un test indépendant de l’ordre colonne, de l’aire et de la boîte après remise à l’échelle.
- L’export COCO valide maintenant avant écriture les identifiants et références, dimensions, catégories, boîtes, aires, `iscrowd` et sommes RLE ; le fichier est remplacé atomiquement et l’aperçu inclut les masques.
- Un contrôle hôte compare toutes les clés FR/EN et vérifie les libellés anglais critiques Modèles, Apprentissage, qualification et COCO.

### Executed tests

- `python -m unittest discover -s tools/qa -p 'test_*.py'` : **47 tests réussis**, aucun échec. Ce sont des contrôles hôte, pas une compilation Android.
- `git diff --check` : réussi.

### Failed tests

- `./gradlew test --no-daemon` : arrêt avant exécution, SDK Android introuvable (`ANDROID_HOME` absent).
- `./gradlew lint --no-daemon` : arrêt avant lint, même cause.
- `./gradlew assembleDebug --no-daemon` : arrêt avant compilation, même cause.
- `./gradlew assembleDebugAndroidTest --no-daemon` : arrêt avant compilation, même cause.

### Not executed

- Tests instrumentés, dont `ProjectMaintenanceTest`, LiteRT, éditeur, Room et navigation : aucun SDK, APK ou émulateur disponible.
- Téléphone ARM, mesures RAM/latence/thermique, SAF réel, corpus autorisé représentatif, précision complète des modèles et intégration d’écriture HF. `adb` n’est pas installé dans ce conteneur.

### Remaining blockers

- Cette passe n’est **pas qualifiée Android** : les deux APK et les schémas KSP n’ont pas été produits dans cet environnement.
- La validation des signatures LiteRT est maintenant partagée par le preflight et `prepare()` ; l’exécution Android doit encore confirmer ce chemin sur le commit courant et ne doit pas être confondue avec une qualification de précision.
- Les écrans Modèles et Apprentissage n’ont plus que deux occurrences candidates du motif statique ciblé, mais 246 occurrences (dont valeurs dynamiques et identifiants techniques) restent à classifier dans l’ensemble de l’UI. Setup, éditeur, publication et contrôles conservent encore des phrases françaises inline : l’anglais complet n’est donc pas encore revendiqué.
- Le test de réimport après reset et les nouveaux tests de compatibilité/raster sont écrits, mais ne doivent pas être annoncés comme réussis avant une exécution Android/JVM réelle.

## Corrigé

- L’intégrité d’installation distingue désormais les artefacts runtime requis de la documentation. Un changement de README, licence, notice ou changelog ne bloque plus un poids intact ; poids et contrats couverts restent vérifiés par taille et SHA-256.
- Les résultats LiteRT distinguent succès avec propositions, succès vide (avec motif) et échec. Le traitement par lot interrompt explicitement l’opération sur un échec et conserve les résultats antérieurs.
- Le catalogue expose des capacités typées et un statut de qualification. DINOv2 est présenté comme encodeur, et l’échec connu de RTMDet non entraînable n’est plus masqué.
- La préannotation refuse les adaptateurs d’embeddings/d’inspection et les sorties incompatibles avec les tâches actives.
- Le retour Android et les boutons internes partagent un historique sans doublons.
- Un calcul de préflight d’apprentissage centralise signatures, export vérifié, minima train/validation, stockage, cibles et apprentissage antérieur.
- La seconde passe relie ce préflight à l’écran d’apprentissage : chaque condition et sa valeur sont visibles et le lancement reste désactivé tant qu’une condition échoue.
- Trois actions locales distinctes disposent maintenant de confirmations explicites : réinitialiser le lot, réinitialiser le projet et supprimer le projet. Elles n’effectuent aucune requête de suppression distante et préservent la bibliothèque de modèles partagée.
- L’éditeur propose des tailles séparées de pinceau et de gomme, duplication/copier-coller de boîtes ou points, suppression directe et états canoniques de pointing absent/non localisable.
- Les masques disposent maintenant de polygone (fermeture par double-tap), lasso, remplissage connexe, fusion et séparation en composantes connexes ; les opérations sont implémentées dans le codec canonique et non comme contrôles décoratifs.
- Chaque inférence d’image ou de lot écrit atomiquement un reçu JSON durable distinguant succès, vide et échec. Les reçus survivent aux purges et réinitialisations locales ; seule la suppression complète et explicitement confirmée du projet les retire.
- Les maintenances écrivent un journal durable avant la transaction et reprennent le nettoyage après une interruption ; un point d’interruption instrumenté vérifie le scénario après commit Room.

## Modifié

- Le contrat d’inférence renvoie `InferenceResult` et des diagnostics structurés (SHA, adaptateur, tâche, entrée, seuil, compte, motif ou erreur).
- La bibliothèque affiche capacités et qualification séparément du simple état installable.
- Aucun schéma Room ni format de projet/export n’a changé ; aucune migration n’est requise.
- Les suppressions utilisent de nouvelles requêtes DAO dans une transaction ; les fichiers privés connus sont nettoyés après la validation de la transaction.
- Les libellés de navigation et les titres/sous-titres des écrans principaux utilisent désormais des ressources Android françaises et anglaises et suivent la langue Android.

## Testé dans cet environnement

- Contrôle statique `git diff --check` réussi.
- Les 45 contrôles Python portables de `tools/qa` réussissent pendant la seconde passe ; ils ne constituent pas une compilation Android.
- `./gradlew test --no-daemon` et `./gradlew lint --no-daemon` ont été lancés, mais Gradle s’est arrêté avant résolution des tâches : aucun SDK Android n’est installé/configuré dans cet environnement (`ANDROID_HOME` absent). Aucun test n’est donc déclaré réussi ici.

## Non testé

- Compilation Android, KSP, lint et tests instrumentés (SDK/émulateur absents de cet environnement).
- Téléphone ARM réel, RAM, latence et thermique.
- Précision métier et conversions LiteRT encore non qualifiées.
- Écritures HF, pannes réseau et permissions SAF réelles ; aucune infrastructure distante n’a été créée.

## Problèmes encore ouverts

- Les libellés contextuels très spécialisés de certains formulaires restent à extraire ; la navigation et le chrome de tous les écrans principaux sont bilingues.
- La recette d’interruption est instrumentée par injection déterministe ; tuer réellement le processus à plusieurs étapes reste une recette appareil à exécuter.

## Risques

- Le nouveau type de résultat touche tous les appelants connus ; une compilation Android réelle reste obligatoire avant qualification.
- Les capacités historiques sans preuve explicite restent volontairement `UNTESTED`/inspection uniquement, ce qui peut rendre certaines actions auparavant proposées indisponibles.
