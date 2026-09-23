# Feuille de route

[Documentation](README.md) · [English](en/ROADMAP.md)

Priorités de qualification au **23 septembre 2026**. rc5 est publiée comme prérelease Debug ; les objectifs ci-dessous n’en font pas une version stable.

| Priorité | Acquis | Prochaine preuve nécessaire |
|---|---|---|
| **P0 · Reproductibilité et recette cohérente** | Build Windows, schémas KSP, 70 JVM, 75 Python ; suite Debug 39/39 sur Honor 4 Ko et émulateur 16 Ko ; crash Flex corrigé | Reproduire sur une autre installation et conserver la cohérence sources/runtime/APK/reçus |
| **P1 · Données, appareils et échanges** | Cycle local 2+1, copie Room restaurée, Honor ARM64 ; ENOSPC, révocation SAF, volume retiré, transfert HF coupé, conflit, réponse perdue et purge interrompue vérifiés | Arrière-plan prolongé ; téléphone ARM 16 Ko ; fournisseurs cloud et gros multipart ; ancienne base utilisateur ; exécution de la CI |
| **P2 · Modèles et qualité** | 13 conversions réussies, dont 8 entraînables ; RTMDet corrigé | 15 conversions à exécuter, 3 délais dépassés à reprendre ; SAM/Florence ; corpus indépendant, précision et oubli mesurés |
| **P3 · Distribution durable** | Clé durable et sauvegarde sur deuxième disque vérifiées ; vraie Release ARM64 signée de 100,2 Mo sur Honor ; inventaire de 110 dépendances | Copie de clé hors machine ; suite complète de la variante Release ; publication du prochain candidat ; revue des notices natives transitives |

## Frontières à conserver

- Les 39 tests de base ne remplacent pas les tests de chaque modèle.
- Les conversions HF entraînent des têtes/adaptations ; leurs encodeurs restent figés.
- Une sauvegarde/restauration exacte ne prouve ni précision métier ni généralisation.
- La suite 16 Ko passe sur émulateur ; deux bibliothèques gardent des signalements RELRO expliqués, sans certification globale.
- La signature Windows de rc5 ne met pas à jour rc4 ; préserver les installations contenant des données.

[Campagne P1](P1_QUALIFICATION_2026_09.md) · [Matrice Android](ANDROID_QUALIFICATION.md) · [Résultats LiteRT](LITERT_QUALIFICATION.md) · [Limites](../KNOWN_LIMITATIONS.md) · [Publication rc5](RELEASE_PLAN.md).
