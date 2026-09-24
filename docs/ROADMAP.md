# La suite pour Cadryl

[Documentation](README.md) · [English](en/ROADMAP.md)

La priorité est de rendre le parcours actuel fiable sur téléphone. Les nouvelles fonctions viendront après les retours sur cette base.

## D’abord : finir la recette du candidat

- Reprendre les deux suites Release sur le Honor avec le runtime final, puis tester une mise à jour signée en conservant ses données.
- Exécuter le parcours sur un vrai téléphone ARM en pages de 16 Ko. L’alignement strict est corrigé ; la preuve matérielle manque encore.
- Refaire les essais longs et observer les restrictions d’arrière-plan. Conserver les traces si ART replante ; la cause historique reste à expliquer.

## Ensuite : données et reproductibilité

Compléter les interruptions et les gros transferts avec des destinations de test autorisées. Vérifier une ancienne base issue d’un usage réel, avec une copie de sauvegarde. Reproduire le build sur un autre poste et exécuter la CI préparée.

## Puis : mesurer les modèles

Reprendre le catalogue avec le nouveau runtime, terminer les variantes manquantes et les bundles, puis mesurer la qualité sur un corpus indépendant. La campagne Charlbi compte aujourd’hui 13/25 réussites, un délai dépassé et onze variantes non exécutées ; les huit entraînements réussis ne prouvent pas un gain de précision.

## Pour une distribution durable

Garder la même clé, ajouter une sauvegarde hors machine, terminer la revue des notices natives et suivre la taille de l’APK. Les modèles restent téléchargeables séparément. Les [limites actuelles](../KNOWN_LIMITATIONS.md) et les [résultats](../TEST_REPORT.md) servent de repères avant chaque release.
