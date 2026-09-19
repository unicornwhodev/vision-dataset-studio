# V4.2 RC2 — limitations actuelles

## Bloquants de qualification

Deux APK Debug réelles sont produites et vérifiées. Compose/Room/Moshi/LiteRT
compilent ; le schéma Room v3 est généré par KSP. Le build global reste en échec :
25/26 tests JVM Android réussissent ; la reprise d’un téléchargement interrompu
échoue dans `HfTransferV4Test.disconnectedDownloadResumesHashedPrefix`. Lint :
0 erreur, 68 avertissements. Voir `docs/POD_VALIDATION.md` pour les preuves et
les résultats instrumentés. Sur API 28, les deux migrations et l’identité ont
réussi avant le blocage de la suite SAF : `SafFaultProvider` ne trouve pas
`kotlin.jvm.internal.Intrinsics` dans le processus de l’APK de tests.

Les schémas Room JSON historiques v1/v2 ne sont pas disponibles ; les fixtures
SQL sont reconstruites, pas récupérées sur une installation. Une mise à jour
depuis des bases réellement utilisées reste nécessaire.

Le pod n’a pas KVM. L’émulateur AOSP démarre et affiche l’application, mais le
premier affichage a pris environ 28 secondes et des blocages System UI ont été
observés. À 320 dp de largeur, l’action « Importer » de la bibliothèque de modèles
est comprimée et son libellé n’est pas lisible sur la capture. La navigation
initiale ne remplace pas la recette complète sur un téléphone.

Pas de test HF de bout en bout, ni injection de panne réelle sur Android, ni mesure RAM/inférence sur téléphone. Les helpers JVM et le stress SQLite hôte n’établissent pas ces succès.

## Stockage et transferts

Le moteur travaille au premier plan ; pas de garantie de continuation après arrêt du processus. La reprise nécessite une relance et la conservation des fichiers/checkpoints/permissions. Pas d’upload LFS reprenable à l’octet. Les URLs signées renouvelées peuvent redémarrer un téléchargement. Pas de garantie matérielle universelle contre une coupure d’alimentation.

Les fichiers de travail restent dans le stockage privé. SAF sert aux sources et aux copies d’archives. Les fournisseurs sans lecture persistante ne permettent pas une clôture locale sûre. Une écriture SAF échouée peut laisser un document externe incomplet à gérer manuellement, mais n’autorise pas la purge. Aucune suppression des sources distantes n’est automatisée.

Les gros dossiers SAF peuvent être lents malgré une lecture bornée ; aucune promesse de débit sur corpus massif n’est établie. Les modifications de paramètres d’un projet ne doivent pas invalider ses lots verrouillés ; tester les historiques multi-projets avant un usage réel.

## Modèles

Runtime CPU Interpreter conservé. Pas de CompiledModel/GPU/NPU intégré. Le catalogue UWD est résolu dynamiquement et les téléchargements utilisent le SHA du dépôt au moment de la découverte, mais ce parcours n’a pas été exécuté sur Android dans cette livraison. L’import doit toujours inspecter les tenseurs réels et peut refuser un modèle inattendu. La bibliothèque ne garantit ni exactitude métier ni licence universelle de redistribution.

Pas de VLM intégré ; le client HTTP exige un serveur séparé sur le même appareil et reste loopback-only. Les mesures mémoire du client n’incluent pas ce serveur. Aucun poids privé ni modèle de pointing non vérifié n’est fourni.

## Hors périmètre maintenu

Pas de lecteur Parquet natif avec pixels embarqués, import COCO/YOLO universel, vidéo, segmentation/masques, OCR/pose, audio/3D ni annotation collaborative temps réel. La V4.2 ajoute des réservations coopératives HF (claim/DONE) pour éviter le travail en double, mais pas un serveur collaboratif ou des verrous instantanés. La correction adaptative géométrique reste locale et n’est pas une preuve de généralisation.

## Identité et distribution

L’identité Android UWD est nouvelle. Il n’y a pas de transfert automatique des bases ni des permissions depuis une application avec un autre applicationId. La licence Apache-2.0 a été choisie. Les notices transitives restent à auditer et la qualification doit être terminée avant publication de la version validée.
