# Roadmap — 4.2.0-rc5

[English](en/ROADMAP.md). La priorité reste la production de datasets par lots avec revue humaine. L’apprentissage Android est facultatif, après export vérifié et avant nettoyage.

| Priorité | Livraison | État / prochaine preuve |
|---|---|---|
| 1 | Production locale, doublons exacts persistants, export et nettoyage | Cycle 2+1, concurrence, reprise et migrations testés ; poursuivre sur corpus représentatif |
| 1 | Conversions LiteRT | 13 succès, RTMDet corrigé, trois délais dépassés sur émulateur et 15 conversions à exécuter ; [matrice](LITERT_QUALIFICATION.md) |
| 1 | Poids internes HF | Les 20 conversions entraînables figent l’encodeur ; nouvelles conversions requises pour entraîner les couches internes |
| 1 | Inférence seule, annotations stables, UI anglaise | Import sans apprentissage ; relance explicite requise ; parcours anglais testé |
| 2 | Masques et segmentation | Éditeur, instances, COCO et point temporaire testés ; chaîne SAM réelle à exécuter |
| 2 | Workflows et agent | Trois parcours Android et consignes testés ; backend réel d’agent à qualifier |
| 2 | Qualité des modèles | Corpus autorisé indépendant, comparaison avant/après et oubli catastrophique ; aucun gain annoncé sans mesure |
| 3 | Appareil et stockage | Téléphone ARM, mémoire/latence/chauffe, permissions SAF réelles, corpus volumineux |
| 3 | HF distant et CI | Scénarios de conflit/réponse perdue sur dépôt de QA autorisé ; CI API28/35 bloquée par facturation |
| 4 | Distribution stable | Signature durable, notices transitives, APK optimisée ; rc5 reste une prérelease Debug |

Les sources, outils, preuves et documentation sont conservés pour la reprise. Les critères complets restent dans [ANDROID_QUALIFICATION.md](ANDROID_QUALIFICATION.md). Les anciens audits décrivent leurs commits historiques, pas le statut courant.
