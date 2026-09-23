# Original conservé et entraînement continu

[Documentation](README.md) · [English](en/MODEL_LINEAGE.md)

![Original et générations entraînées](visuals/current/model-lineage.svg)

Pour chaque projet et modèle de départ, l’application conserve deux versions
logiques : **l’original** et **la version entraînée**.

1. Le premier entraînement copie le graphe dans un dossier privé distinct. Le
   fichier original et son profil restent disponibles, avec leur empreinte.
2. Une évaluation réussie et une sauvegarde/restauration vérifiée font avancer
   la version entraînée. Son profil garde le même identifiant entre générations.
3. L’entraînement suivant restaure les derniers poids validés de cette version,
   même si l’original est toujours sélectionné pour l’inférence. La filiation
   est sauvegardée sur disque ; elle ne dépend pas de l’état de l’écran.
4. L’activation pour l’inférence reste explicite. Une erreur, une interruption
   ou une évaluation rejetée ne remplace pas la dernière version validée.
   « Reprendre » recharge le checkpoint de la tentative interrompue.

Chaque tentative écrit dans son propre dossier et ses propres checkpoints.
La version entraînée avance par remplacement atomique d’un reçu, après contrôle,
sans réécrire les poids de l’original ni ceux de la génération précédente.
Le graphe `.tflite` et son checkpoint forment ensemble le modèle entraîné :
copier le seul graphe ne transporte pas les poids appris.

Un checkpoint entraîné absent ou altéré bloque la reprise ; l’application ne
revient pas silencieusement aux poids initiaux. Le nettoyage du lot supprime
les copies d’images et de cibles, en conservant les modèles, checkpoints et reçus
référencés. Un autre projet ou un autre contrat modèle a sa propre filiation.
Les anciens reçus sans filiation restent lisibles ; un modèle ancien sélectionné
sert de base conservée au prochain entraînement, sans inventer sa provenance.

La recette Android `TrainingWorkflowTest` contrôle deux lots exportés successifs,
les empreintes de l’original, l’égalité entre poids finaux du premier entraînement
et poids initiaux du deuxième, l’interruption/reprise, la persistance des reçus,
le refus d’un checkpoint manquant et la conservation après nettoyage. Les
résultats exécutés sont consignés dans [le rapport Windows](WINDOWS_QUALIFICATION_2026_09.md).
Ce contrôle synthétique ne mesure pas la qualité sur un corpus métier.

## Recette exécutée le 23 septembre 2026

Build `20260923T104142Z-befce3e3596a` : suite de base **39/39 sur API 35 en 4 Ko et 39/39 en 16 Ko**.
Les deux lots successifs reprennent les mêmes poids : fin du premier = début du
deuxième (`28cb72074ba3d20255ae573e7655fca417946418ae0a74709620a1699e23969a`).
L’original conserve son SHA-256 et son profil importé. Les fichiers et reçus sont
aussi identiques après arrêt/redémarrage réel du processus. Les preuves sont dans
[la suite 16 Ko](../test-results/windows-rc5-release/api35-16k/status.json) et [la suite 4 Ko](../test-results/windows-rc5-release/api35-4k/status.json).
