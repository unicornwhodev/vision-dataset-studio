# Limites de qualification — 4.2.0-rc2

## Production par lots

Le cycle local 2+1, l’exclusion des copies identiques/renommées, l’export, la purge et la réouverture Room ont passé un essai instrumenté API 28. Les empreintes persistent par projet. Les images modifiées avec pertes, recadrées ou retouchées ne sont pas couvertes par la garantie d’identité exacte ; les anciennes images déjà purgées ne disposent que des empreintes de fichiers conservées. Voir [BATCH_PRODUCTION.md](docs/BATCH_PRODUCTION.md).

Le fournisseur SAF injecté passe ses quatre scénarios de lecture/écriture défaillante. Il ne remplace pas une recette DocumentsUI et fournisseurs cloud avec octroi/révocation réel de permissions persistantes. Les migrations 1/2/3 → 4 ont passé Room sur Android ; les bases v1/v2 restent des fixtures reconstruites. Une base issue d’une ancienne installation réelle reste à tester.

Les réservations HF, conflits de commits, réponse perdue, publication distante et nettoyage après perte de réseau ne sont pas qualifiés sur un dépôt de test autorisé. Aucun dépôt existant n’a reçu d’écritures de QA.

## Apprentissage facultatif Android

L’apprentissage est désactivé par défaut. Il utilise uniquement le lot terminé dont l’export a été vérifié. Le nettoyage attend la fin de l’apprentissage et de son évaluation ; annulation et erreur conservent les images. L’activation des nouveaux poids reste manuelle.

Un petit réseau de contrôle a réellement modifié ses poids internes, diminué sa perte et reproduit ses sorties après sauvegarde/rechargement sur Android. Ce résultat ne qualifie aucune conversion HF de production. Les conversions entraînables annoncées par le propriétaire doivent encore être importées et éprouvées avec leur contrat réel.

Le chemin Interpreter/Flex emploie LiteRT 1.4.2 et Select TF Ops 2.16.1. L’intégration LiteRT 2.2 essayée n’expose pas l’API Java Delegate requise ; sa sauvegarde FlexSave a échoué. Les opérateurs des conversions HF récentes doivent être vérifiés sous le runtime retenu. GPU/NPU et apprentissage distribué ne sont pas intégrés.

Le lot doit contenir au moins 32 images d’apprentissage et 8 de contrôle selon le partage déterministe par hash. Les cibles sont limitées à quatre millions de valeurs par lot pour borner la mémoire. Les signatures d’apprentissage à entrées texte ou masques auxiliaires ne sont pas prises en charge. Un contrôle réutilisé n’est pas une mesure sur corpus indépendant.

## Modèles et fonctions complémentaires

Les adaptateurs multi-graphes TinyCLIP, EfficientViT-SAM et Florence-2, les tokeniseurs et l’index de similarité sont implémentés. La tokenisation et la persistance de l’index passent leurs tests Android. RepViT HF a exécuté son inférence avec succès sous le runtime retenu. Leur présence ne prouve pas l’exécution de tous les modèles HF : DINOv2 a dépassé dix minutes dans l’émulateur logiciel, puis a été arrêté. Les autres conversions et gros bundles restent à qualifier individuellement ; aucune précision ni performance ARM n’est annoncée.

RTMDet reste en inspection de tenseurs sans décodeur de détection intégré. Le masque SAM ne dispose pas encore d’un éditeur/export de masques. Florence utilise un décodage greedy borné ; la fidélité numérique du prétraitement et les tâches réelles restent à mesurer.

Les templates/packs configurent tâches, classes, prompts et modèle ; ils ne sont pas un moteur général d’agent ou de workflows. L’application n’embarque pas de serveur VLM autonome. Les profils HTTP locaux dépendent d’un serveur fourni séparément.

## Distribution

Aucun poids n’est embarqué dans l’APK. Le build contrôle les extensions et signatures de poids et écrit `app-contents.json`. L’APK Debug universelle reste volumineuse à cause des bibliothèques natives de quatre architectures ; des APK par ABI et une release optimisée sont à préparer.

La CI API 28/35, un téléphone ARM, la RAM, la latence et les contraintes thermiques restent à qualifier. Le pod n’a pas KVM : l’émulateur logiciel sert à la vérification fonctionnelle, pas aux performances. Le push des sources est autorisé ; aucun package ni release stable n’est publié.

La licence du code est Apache-2.0. Les licences des modèles/datasets et les notices transitives restent indépendantes. Le changement d’applicationId ne migre pas les données d’une autre application.
