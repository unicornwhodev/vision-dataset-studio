# Architecture de Cadryl

**Français** · [English](en/ARCHITECTURE.md)

L’application est un atelier Android de production de datasets par lots. L’image, la correction humaine et la preuve d’export déterminent le parcours. Les schémas décrivent les composants présents ; ils ne signifient pas que toutes leurs intégrations externes sont qualifiées.

![Architecture](visuals/architecture.fr.svg)

## Composants et responsabilités

| Composant | Responsabilité | Code |
|---|---|---|
| Interface Compose | Atelier, navigation, sélection, édition de régions et masques, revue humaine | [Écrans](../app/src/main/java/com/unicornwhodev/visiondatasetstudio/ui/screens/) |
| Moteur de lots | Acquérir, réserver une identité, préparer les lots, protéger les reprises | [BatchEngine](../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/batch/BatchEngine.kt) · [ImageIdentity](../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/batch/ImageIdentity.kt) |
| Room v4 | Projets, annotations, identités persistantes et états de production | [AppDatabase](../app/src/main/java/com/unicornwhodev/visiondatasetstudio/data/db/AppDatabase.kt) |
| LiteRT | Charger le contrat et les poids, prétraiter, exécuter puis décoder les sorties | [LiteRtEngine](../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/inference/LiteRtEngine.kt) |
| Apprentissage | Cibles corrigées, travail Android en arrière-plan, checkpoints, reprise et évaluation | [OnDeviceTraining](../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/training/OnDeviceTraining.kt) · [TrainingTargets](../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/training/TrainingTargets.kt) |
| Exports | Archive canonique et projections COCO, YOLO, WebDataset, vision-langage | [DatasetExporters](../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/export/DatasetExporters.kt) |
| HF / fichiers | Acquisition distante autorisée, publication, lecture/écriture SAF et vérification | [HfApiClient](../app/src/main/java/com/unicornwhodev/visiondatasetstudio/data/hf/HfApiClient.kt) · [Sources](../app/src/main/java/com/unicornwhodev/visiondatasetstudio/data/source/) |
| Workflows / agent | Templates exécutables, journal atomique et suggestion locale facultative | [WorkflowRunner](../app/src/main/java/com/unicornwhodev/visiondatasetstudio/domain/workflow/WorkflowRunner.kt) |

Les flèches externes du schéma représentent l’accès de l’application aux sources et destinations, pas un envoi de toutes les données à un serveur. Les écritures HF et la copie SAF restent des opérations explicites avec vérification de relecture. Le poste de développement assemble et teste les APK ; il n’est pas requis comme serveur d’inférence ou d’apprentissage pour l’utilisateur.

## Cycle protégé du lot

![Cycle](visuals/batch-flow.fr.svg)

1. Importer un lot après contrôle des identités de contenu du projet. Préannoter si un modèle est actif ; conserver les corrections humaines existantes.
2. Corriger et accepter/rejeter chaque image. Les prédictions ne constituent pas une validation humaine.
3. Exporter et vérifier la copie externe. L’archive canonique reste la référence ; les projections sont complémentaires.
4. Si l’apprentissage est activé, utiliser les images acceptées et annotations finales de ce lot exporté. L’option est désactivée par défaut. Attendre la fin et l’évaluation ; une erreur/interruption conserve les images.
5. Confirmer le nettoyage. Les images de travail et le snapshot d’apprentissage sont supprimés ; annotations, identités, reçus et checkpoints survivent. Passer au lot suivant. L’activation des poids candidats reste manuelle.

La base persistante empêche les copies identiques entre lots du même projet. Elle ne garantit pas l’élimination de toutes les images recadrées ou recompressées avec pertes. Les modèles sont téléchargés après installation : **aucun poids dans l’APK**.

## Frontière de l’agent

Le planificateur HTTP facultatif reçoit des compteurs, consignes et templates sur `localhost`/`127.0.0.1`. Il ne reçoit ni corpus ni identifiant HF. Sa réponse structurée choisit un template ; elle ne peut accepter une annotation, publier, purger, changer de source ou activer des poids. Les noms de fichiers, images et sorties des modèles sont des données, pas des instructions. Un serveur réel d’agent reste à qualifier.

## Où sont les preuves ?

Le [rapport actuel](../TEST_REPORT.md) identifie les APK rc6 et les distingue des anciennes campagnes téléphone et interruptions. La [matrice des modèles](LITERT_QUALIFICATION.md) garde les résultats par conversion. Le schéma décrit les responsabilités du code ; il ne valide pas toutes les intégrations à lui seul.
