# Feuille de route

[Documentation](README.md) · [English](en/ROADMAP.md)

Priorités de qualification au **23 septembre 2026**. rc5 est publiée comme prérelease Debug ; les objectifs ci-dessous n’en font pas une version stable.

| Priorité | Acquis | Prochaine preuve nécessaire |
|---|---|---|
| **P0 · Reproductibilité et recette cohérente** | Build Windows, schémas KSP, 67 JVM, 70 Python, suites Android 39/39 en 4 Ko et 16 Ko ; crash Flex corrigé | Reproduire sur une autre installation et conserver la cohérence sources/runtime/APK/reçus |
| **P1 · Données, appareils et échanges** | Cycle local 2+1, sauvegarde/relecture de la base testée, gardes SAF/HF injectées, original et checkpoints préservés | Téléphone ARM ; qualification 16 Ko globale ; permissions/volumes réels ; transferts HF autorisés, conflits et réponses perdues ; exécution de la CI |
| **P2 · Modèles et qualité** | 13 conversions réussies, dont 8 entraînables ; RTMDet corrigé | 15 conversions à exécuter, 3 délais dépassés à reprendre ; SAM/Florence ; corpus indépendant, précision et oubli mesurés |
| **P3 · Distribution durable** | rc5, package OCI, runtime Flex, candidat ARM64 R8 de 100,2 Mo et inventaire de 111 dépendances | Signature durable, stratégie de transition depuis rc4, candidat signé testé sur appareil et revue des notices natives transitives |

## Frontières à conserver

- Les 39 tests de base ne remplacent pas les tests de chaque modèle.
- Les conversions HF entraînent des têtes/adaptations ; leurs encodeurs restent figés.
- Une sauvegarde/restauration exacte ne prouve ni précision métier ni généralisation.
- La suite 16 Ko passe, mais trois autres bibliothèques gardent des signalements RELRO.
- La signature Windows de rc5 ne met pas à jour rc4 ; préserver les installations contenant des données.

[Matrice Android](ANDROID_QUALIFICATION.md) · [Résultats LiteRT](LITERT_QUALIFICATION.md) · [Limites](../KNOWN_LIMITATIONS.md) · [Publication](RELEASE_PLAN.md).
