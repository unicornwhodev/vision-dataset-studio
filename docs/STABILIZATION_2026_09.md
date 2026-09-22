# Stabilisation — septembre 2026

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
