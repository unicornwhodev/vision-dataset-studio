# Schéma canonique et compatibilité

Les classes `data/model/DatasetModels.kt` sont la définition effective. Le canonique conserve une image et toutes ses annotations : points, boîtes, captions, tags, groundings, VQA, comptages et qualité. Les coordonnées géométriques sont normalisées [0,1] dans le repère de l’image effectivement exportée.

Le format garde séparés : annotations manquantes, négatif vérifié, présence non localisable, abstention et rejet. Un modèle ne transforme jamais son résultat vide en négatif vérifié. Un comptage de zéro est un choix explicite, différent d’une liste d’objets encore incomplète.

## Extensions V3 additives

| Objet | Champs ajoutés / utilisés |
|---|---|
| `PointTarget` | `modelX`, `modelY`, `boxWidth`, `boxHeight`, `explicitlyAdjusted`, `modelLabel`, `correctionGeneration`. Les coordonnées initiales servent à distinguer une acceptation d’une retouche supervisée. |
| `CaptionTarget`, `VqaTarget`, `CountingTarget` | Provenance de la suggestion ; revue humaine distincte. |
| `CanonicalMediaInfo` | `source_sha256`, `transform` ; `sha256` reste le hash de l’image exportée. |
| Métadonnées Room | Ordinal de sélection, paramètres du projet, preuves de copie et namespace de push. Les URI locales ne sont pas des liens publics. |

Le JSONL canonique utilise `sample_id`, `asset_id`, `dataset_source`, `source_revision`, `source_config`, `source_split`, `source_row_index`, `group_id`, `split`, `media`, `annotations`, `review_status` et `audit`.

Les champs internes des annotations gardent les noms Kotlin camelCase : par exemple `isHumanVerified`, `sourceProvenance`, `vqaList`. Ne pas importer un schéma approximatif ou un COCO brut dans `annotations`.

## Contrats, packs et migrations

`ModelConfig.schemaVersion` et `ProcessingSettings.schemaVersion` valent 1. `StudioPack.schema` vaut `vision-studio-pack/1`. Un pack contient nom, classes, tâches, taille de lot et contrat optionnel. Il n’est pas une sauvegarde du corpus ni de la base. Il n’inclut pas source/destination, poids, jeton HF du coffre, réglages complets de stockage ou historique des corrections.

Le contrat et ses prompts sont inclus : les secrets ajoutés manuellement par un utilisateur dans ce texte ne peuvent pas être considérés automatiquement comme retirés. L’export le signale.

Room migre de 1 vers 2 sans `fallbackToDestructiveMigration`. Les valeurs par défaut des colonnes ajoutées sont définies dans les entités et dans le SQL. La migration n’a pas été exécutée sur une base Android réelle.

Une version V2 peut avoir des lots déjà vérifiés sans les nouveaux reçus persistants. Ne pas inventer une preuve de copie pendant la migration. Le contrôle de ces anciens états fait partie de la recette, avant réutilisation sur un corpus réel.

## Delta V4 — base version 3

BatchEntity conserve remoteRepoId, remoteParentCommit, preparedManifestSha256, lastTransferError, remoteReceiptJson et archiveSizeBytes. Ces champs sont nullable pour les anciennes bases, sans preuve de succès fabriquée. RemoteReceipt contient une version et les chemins/tailles/SHA-256 ; il survit à la purge. PREPARED, CONFLICT et PURGING sont des états explicites de transfert. Le schéma canonique des annotations et ses contrats ne sont pas remplacés.
