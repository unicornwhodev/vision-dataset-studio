# Vision Dataset Studio 4.2 RC1 — modifications

- Écran **Modèles** dédié, catalogue distant `Charlbi/Lite_rt_prepared_for_android_dataset_builder`, bibliothèque locale et import manuel.
- Le catalogue n’annonce comme disponibles que les `.tflite` réellement présents dans l’arbre HF. Les bundles multi-fichiers restent visibles mais non activés artificiellement.
- Contrats automatiques prudents pour DINOv2 (embedding sans annotation automatique) et ViTPose+ (heatmap keypoints). Les modèles inconnus sont importables en mode inspection.
- Travail partagé HF facultatif : claims déterministes, baux expirables, commit parent optimiste, reprise sur conflit et état `DONE` en fin de lot. Les cas actifs/terminés par un autre collaborateur sont ignorés avant téléchargement.
- Correction adaptative étendue des points aux boîtes : centre + dimensions, uniquement après correction humaine explicite et validation.
- Navigation Material 3 clarifiée avec destination Modèles.

## Limites

- Pas de collaboration temps réel, commentaire multi-utilisateur ou édition simultanée d’une même annotation.
- Les réservations nécessitent un dépôt HF de destination accessible en écriture. Un bail expiré peut rendre un lot abandonné de nouveau disponible.
- TinyCLIP, EfficientViT-SAM et Florence-2 sont des bundles : visibles dans le catalogue, mais l’engine actuel n’exécute pas encore plusieurs graphes chaînés comme un seul profil.
- RF-DETR peut être téléchargé pour inspection quand son fichier est publié ; son contrat de sortie ne sera pas deviné sans preuve des tenseurs.
- DINOv2 seul produit des embeddings et aucune annotation automatique tant qu’une tête/prototype adapté n’est pas implémenté.
