# Production par lots

Le parcours principal est : **importer → préannoter → corriger/valider → exporter et vérifier → apprentissage facultatif → nettoyer → lot suivant**.

L’apprentissage est désactivé par défaut. Il ne se déclenche jamais à la validation d’une image. Lorsqu’il est activé, il utilise exclusivement les images acceptées et les annotations finales du lot dont l’export local ou HF a été vérifié. Un lot non exporté ou incomplet est refusé. Les autres lots et les cas rejetés ne sont pas ajoutés au corpus. L’empreinte du lot et la preuve d’export figurent dans le reçu d’apprentissage.

Le nettoyage est interdit pendant un apprentissage en attente, actif, interrompu ou en échec. Il devient disponible après sa fin et son évaluation, y compris lorsque le candidat est rejeté. La suppression conserve sa confirmation et la relecture de la copie externe. Elle supprime les images de travail et la copie d’apprentissage du lot, mais conserve annotations, historique, empreintes, preuves d’export et checkpoints. Les nouveaux poids ne sont jamais activés automatiquement.

Sans apprentissage, aucun modèle n’est nécessaire à l’import, à la correction manuelle ou à l’export. Avec un modèle actif, la préannotation automatique des nouvelles images est activable dans les réglages ; elle ne traite que les cas encore vierges et ne remplace pas les annotations importées ou humaines. Les erreurs d’inférence laissent le lot disponible pour correction et reprise.

## Identité des images

Room v4 conserve un registre d’identité par projet, indépendant du cache et des lots : SHA-256 du fichier source, du fichier normalisé et des pixels décodés en sRGB. Le nom, le chemin et l’identifiant source ne déterminent pas l’identité du contenu. Les empreintes de pixels sont calculées par bandes de 64 lignes, sans réduire l’image.

Une copie identique, renommée ou réencodée sans changement des pixels est exclue avant annotation. Sa provenance reste dans l’historique, avec le numéro du lot original. Les téléchargements concurrents réservent leur identité dans une transaction unique. Les places libérées sont remplies par les images suivantes ; les erreurs de téléchargement restent visibles et récupérables. Une action parcourt au maximum 20 fenêtres, puis propose une reprise pour les sources remplies de doublons.

Le registre survit aux exports, à la purge et au redémarrage de l’application. La migration reprend les SHA-256 déjà conservés dans les anciennes bases. Les pixels d’une ancienne image déjà purgée ne peuvent pas être reconstruits ; sa protection historique repose sur ses empreintes de fichiers. La détection de quasi-doublons transformés (JPEG recompressé avec pertes, recadrage, retouches) n’est pas une garantie de ce registre. Les hashes perceptuels ne servent pas à supprimer automatiquement des images distinctes.

La portée est le projet sur cette installation. Une nouvelle installation sans restauration de sa base, ou un autre projet indépendant, n’hérite pas du registre. L’archive dataset ne remplace pas une sauvegarde complète du projet.

## APK et modèles

L’APK contient le runtime, les adaptateurs, les contrats, le catalogue, les tokeniseurs et la logique de téléchargement/import. Les poids sont téléchargés ou importés après installation et conservés dans le stockage privé Android. Aucun corpus ni poids n’est intégré à l’APK.

Le build contrôle l’archive APK : extensions de poids et signatures LiteRT/GGUF dans assets/raw. `app-contents.json` conserve l’inventaire ; un poids détecté bloque la promotion de l’APK. Les modèles d’essai sont injectés séparément dans le stockage de l’émulateur. La taille de l’APK universelle Debug vient notamment des bibliothèques natives pour quatre architectures.

Les graphes d’apprentissage doivent respecter [le contrat LiteRT](LITERT_TRAINING_CONTRACT.md). Le support d’un contrat ne constitue pas une qualification de toutes les conversions HF. Le rapport d’exécution distingue modèles essayés, échecs et essais encore requis.

## Recette

`BatchProductionTest` couvre un cycle local 2+1 avec deux copies dans le second lot, export réel, écriture/relecture via fournisseur Android, purge, réouverture Room et argument de curseur obsolète. Un autre test couvre les acquisitions concurrentes. `TrainingWorkflowTest` couvre le refus avant export, l’exclusion des autres lots, la barrière de nettoyage, WorkManager et la reprise. Les résultats exécutés sont consignés dans [TEST_REPORT.md](../TEST_REPORT.md).
