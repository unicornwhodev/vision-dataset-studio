# Votre premier lot

[Accueil](../README.md) · [Documentation](README.md) · [English](en/GETTING_STARTED.md)

Ce guide suit un premier lot, de l’import à l’export relu. Commencez par quelques images autorisées sur une installation de recette ; aucun modèle n’est nécessaire pour annoter manuellement.

## Installer la prerelease

Téléchargez les fichiers depuis la [release rc5](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc5) et comparez leur SHA-256 au fichier `SHA256SUMS` fourni.

| Fichier | Usage |
|---|---|
| `vision-dataset-studio.apk` | Application Debug, Android 9+ ; 413,9 Mo, aucun poids |
| `vision-dataset-studio-4.2.0-rc5-qualification.zip` | APK utilisateur, APK instrumentée, preuves et documentation |
| `vision-dataset-studio-arm64-v8a-unsigned.apk` | Candidat optimisé de 100,2 Mo, non signé et non installable en l’état |
| `vision-dataset-studio-flex-2.16.1-vds16k1.zip` | Runtime local Maven destiné à la compilation |
| `PACKAGE.json`, `PUBLICATION_CHECKS.json`, `SHA256SUMS` | Provenance, contrôles et intégrité |

Sous PowerShell, `Get-FileHash .\vision-dataset-studio.apk -Algorithm SHA256` calcule l’empreinte locale. La clé Debug Windows diffère de rc4 : **rc5 ne peut pas mettre à jour rc4**. Ne désinstallez pas une application contenant vos annotations pour contourner ce conflit. Une migration sûre nécessite la signature compatible ou une procédure de transfert qualifiée ; rc5 n’en fournit pas de nouvelle.

## 1. Créer le projet

Dans **Mon projet**, choisissez les tâches, classes, source et taille du lot. Commencez avec un petit dossier local pour comprendre le parcours. Choisissez un emplacement d’export dont vous pourrez relire le contenu. Les autorisations d’un fournisseur de documents peuvent être révoquées ultérieurement.

## 2. Préparer les images

Indexez la source, puis préparez le lot. Les copies exactes de fichiers ou de pixels déjà traités dans ce projet sont reconnues, même après nettoyage. Les images retouchées ou recompressées peuvent rester des cas distincts.

Une acquisition échouée demande une nouvelle tentative ou une exclusion explicite avec motif. Elle ne devient pas une annotation validée.

## 3. Ajouter une aide de modèle, si utile

Dans **Modèles**, importez un fichier compatible ou configurez une source HF autorisée. Pour commencer, consultez les [conversions Charlbi](https://huggingface.co/Charlbi/Lite_rt_prepared_for_android_dataset_builder) et leurs résultats par variante. Importez également le contrat et les composants des bundles.

Inspectez le modèle et essayez une image avant la préannotation du lot. Un modèle sans signatures d’apprentissage reste utilisable en inférence. Changer ses paramètres ou son prompt ne modifie pas les annotations existantes : une relance explicite est nécessaire.

## 4. Corriger et décider

Contrôlez chaque proposition. Ajustez les boîtes, points ou masques, confirmez les cas utilisables et rejetez les autres avec un motif. Une proposition du modèle ne remplace pas votre validation. Une image sans annotation connue n’est pas automatiquement un exemple négatif.

## 5. Exporter puis relire

Conservez le **JSONL canonique** et les images nécessaires. COCO, YOLO, WebDataset et les formats vision-langage sont des projections complémentaires, avec leurs limites. [Contrats de données](../DATA_SCHEMA.md).

Créez l’archive, copiez-la vers la destination choisie et vérifiez sa relecture. Pour HF, utilisez uniquement une destination sur laquelle vous êtes autorisé à écrire. Un conflit ou une réponse perdue doit être résolu par les reçus ; l’application ne doit pas changer silencieusement le parent du commit.

## 6. Apprendre, uniquement si activé

L’option est désactivée par défaut. Elle exige un modèle entraînable et un lot corrigé dont l’export a été vérifié. Le partage déterministe doit fournir **au moins 32 images d’apprentissage et 8 de contrôle** ; sélectionner 40 images ne garantit pas automatiquement cette répartition.

Le premier entraînement crée une version séparée ; les suivants reprennent ses derniers poids validés. L’original reste disponible. Vous pouvez interrompre puis reprendre une tentative ; l’activation des nouveaux poids pour l’inférence est explicite. [Guide des versions et checkpoints](MODEL_LINEAGE.md).

## 7. Nettoyer et continuer

Confirmez le nettoyage seulement après la copie vérifiée et la fin de l’apprentissage éventuel, ou son abandon explicite. Le nettoyage du lot conserve les reçus, les identités persistantes et les versions de modèle référencées. Préparez ensuite le lot suivant.

## Si une étape bloque

| Situation | Vérification utile |
|---|---|
| Installation refusée | Certificat existant, version Android et espace disponible ; conserver les données |
| Modèle chargé mais apprentissage indisponible | Signatures et contrat de la variante ; `_learning` est une conversion distincte |
| Bundle incomplet | Tous les graphes, processeurs et tokeniseurs sont nécessaires |
| Apprentissage refusé | Export relu, tailles réelles du partage, checkpoint et contrat compatibles |
| Copie/SAF interrompue | Accès au volume et au dossier ; aucune purge sans copie relue |
| Détection peu utile | Les tests d’exécution ne mesurent pas la précision ; revoir modèle, classes, données et prétraitement |

[Limites connues](../KNOWN_LIMITATIONS.md) · [Workflows](WORKFLOWS.md) · [Recette complète](ANDROID_QUALIFICATION.md).
