# V4.2 RC1 — limitations actuelles

## Bloquants de qualification

Aucune APK construite, aucune installation, aucun typage Compose/Room/Moshi complet. Les dépendances KSP/Room/Moshi/MetadataExtractor et le SDK mineur hérité doivent être résolus par un vrai build. Les schémas Room JSON historiques ne sont pas disponibles ; les fixtures SQL sont reconstruites, pas récupérées sur une installation. Une mise à jour depuis des bases réellement utilisées reste nécessaire.

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

L’identité Android UWD est nouvelle. Il n’y a pas de transfert automatique des bases ni des permissions depuis une application avec un autre applicationId. La licence du projet n’est pas choisie et les notices transitives résolues restent à auditer : ce candidat n’est pas prêt pour publication open source ou release Android.
