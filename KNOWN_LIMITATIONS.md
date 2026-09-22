# Limites de qualification — audit rc5

[English](docs/en/KNOWN_LIMITATIONS.md) · [Recette fonctionnelle](docs/FUNCTIONAL_AUDIT_2026_09.md)

Le grounding modèle exige le contrat HTTP explicite `task=grounding` + `httpOutputMode=grounding_proposals`, des identifiants de régions uniques et des liens phrase-région contrôlés. Les tests de contrat et de revue humaine passent ; aucun serveur grounding réel n’a été qualifié. Les bundles Florence-2 et les couples caption+box ne sont pas déclarés grounding. Les imports et modèles ne reçoivent aucun lien d’instance inventé.

## Production par lots

Le cycle local 2+1, l’exclusion des copies identiques/renommées, l’export, la purge et la réouverture Room ont passé un essai instrumenté API 28. Les empreintes persistent par projet. Les images modifiées avec pertes, recadrées ou retouchées ne sont pas couvertes par la garantie d’identité exacte ; les anciennes images déjà purgées ne disposent que des empreintes de fichiers conservées. Voir [BATCH_PRODUCTION.md](docs/BATCH_PRODUCTION.md).

Le fournisseur SAF injecté passe ses quatre scénarios de lecture/écriture défaillante. Il ne remplace pas une recette DocumentsUI et fournisseurs cloud avec octroi/révocation réel de permissions persistantes. Les migrations 1/2/3 → 4 ont passé Room sur Android ; les bases v1/v2 restent des fixtures reconstruites. Une base issue d’une ancienne installation réelle reste à tester.

Les réservations HF, conflits de commits, réponse perdue, publication distante et nettoyage après perte de réseau ne sont pas qualifiés sur un dépôt de test autorisé. Aucun dépôt existant n’a reçu d’écritures de QA.

## Apprentissage facultatif Android

L’apprentissage est désactivé par défaut. Il utilise uniquement le lot terminé dont l’export a été vérifié. Le nettoyage attend la fin de l’apprentissage et de son évaluation ; annulation et erreur conservent les images. L’activation des nouveaux poids reste manuelle.

Sept conversions HF ont passé inférence, apprentissage, sauvegarde/restauration et reprise Android ; cinq autres ont passé l’inférence. Les encodeurs des conversions HF fournies restent figés. Le réseau de contrôle synthétique modifie séparément ses couches visuelles internes. Voir la [matrice par conversion](docs/LITERT_QUALIFICATION.md) : 12 succès, un délai dépassé RF-DETR, 18 conversions encore en recette. Aucun gain de précision n’est démontré.

Le chemin Interpreter/Flex emploie LiteRT 1.4.2 et Select TF Ops 2.16.1. L’intégration LiteRT 2.2 essayée n’expose pas l’API Java Delegate requise ; sa sauvegarde FlexSave a échoué. Les opérateurs des conversions HF récentes doivent être vérifiés sous le runtime retenu. GPU/NPU et apprentissage distribué ne sont pas intégrés.

Le lot doit contenir au moins 32 images d’apprentissage et 8 de contrôle selon le partage déterministe par hash. Les cibles sont limitées à un million de valeurs par image et stockées sur disque avec empreinte. Les masques et cibles auxiliaires de présence/abstention/supervision sont pris en charge selon le contrat ; les entrées d’apprentissage texte ne le sont pas. Un contrôle réutilisé n’est pas une mesure sur corpus indépendant.

## Modèles et fonctions complémentaires

Les adaptateurs multi-graphes TinyCLIP, EfficientViT-SAM et Florence-2, les tokeniseurs et l’index de similarité sont implémentés. La tokenisation et la persistance de l’index passent leurs tests Android. RepViT, HGNetV2, RTMDet, DINOv2 et TinyCLIP ont passé leur inférence Android. TinyCLIP produit des propositions visibles sur une photo réelle et applique le prompt lors d’une relance explicite. RF-DETR a dépassé 30 minutes dans l’émulateur logiciel ; cela ne démontre pas une incompatibilité du modèle. Les autres conversions et gros bundles restent à qualifier individuellement ; aucune précision ni performance ARM n’est annoncée.

Le défaut de lecture des sorties dynamiques RTMDet est corrigé et le test passe. Le masque dispose d’un éditeur multi-instance (pinceau/gomme), d’un export canonique et d’une projection COCO ; le point SAM temporaire ne devient pas une annotation. La chaîne SAM réelle reste à qualifier. Florence utilise un décodage greedy borné ; précision et tâches réelles restent à mesurer.

Trois workflows exécutables disposent d’un journal et de pauses pour revue, export vérifié et nettoyage. Un planificateur HTTP local optionnel valide les sorties structurées et les consignes ; aucun serveur VLM n’est embarqué. Les tests des gardes ne remplacent pas une intégration avec un véritable serveur d’agent. Voir [WORKFLOWS.md](docs/WORKFLOWS.md).

## Interface et localisation

Les textes applicatifs français et anglais sont implémentés. Le parcours anglais de création/changement de projets, réglages persistés et propositions visibles a passé Compose sur API 28. Les noms et contenus saisis par l’utilisateur restent dans leur langue. TalkBack, une grande police sur tous les écrans, les fournisseurs SAF réels et un téléphone ARM restent à qualifier.

## Distribution

Aucun poids n’est embarqué dans l’APK. Le build contrôle les extensions et signatures de poids et écrit `app-contents.json`. L’APK Debug universelle reste volumineuse à cause des bibliothèques natives de quatre architectures ; des APK par ABI et une release optimisée sont à préparer.

La CI API 28/35, un téléphone ARM, la RAM, la latence et les contraintes thermiques restent à qualifier. Le pod n’a pas KVM : l’émulateur logiciel sert à la vérification fonctionnelle, pas aux performances. Les sources sont publiées. La distribution est une prérelease Debug de qualification et un paquet OCI, sans image de build qualifiée. Le lancement GitHub Actions a été refusé pour un problème de facturation du compte ; aucun job CI n’a exécuté de tests.

La licence du code est Apache-2.0. Les licences des modèles/datasets et les notices transitives restent indépendantes. Le changement d’applicationId ne migre pas les données d’une autre application.

La clé Debug rc3 est sauvegardée hors Git pour les mises à jour suivantes. Le propriétaire confirme qu’aucune installation rc2 n’a été distribuée ; aucune migration rc2 n’est prévue. Une signature de distribution stable reste à définir.
