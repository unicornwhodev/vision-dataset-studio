# Ton premier lot avec Cadryl

[Documentation](README.md) · [English](en/GETTING_STARTED.md)

Le plus simple : quelques images, un projet et aucun modèle pour commencer. Tu pourras ajouter l’IA une fois le parcours pris en main.

## Installer Cadryl

Dans la [release rc6](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc6), prends **`vision-dataset-studio.apk`**. C’est l’app Release signée pour Android 9+ sur ARM64. Les modèles se téléchargent séparément.

Le fichier `SHA256SUMS` permet de vérifier le téléchargement. Sous PowerShell :

```powershell
Get-FileHash ./vision-dataset-studio.apk -Algorithm SHA256
```

Compare le résultat à la ligne de l’APK. Les archives `qualification` contiennent aussi les APK de tests et la variante x86_64 ; elles servent au développement. Les trois ZIP Flex, Graphics Path et LiteRT sont les dépendances de compilation.

**Une ancienne rc4/rc5 est déjà installée ?** Elle utilise une autre signature et ne peut pas recevoir rc6 comme mise à jour. Garde ses données et ne la désinstalle pas pour forcer le passage. Les candidats signés avec la [clé durable](SIGNING.md) conservent, eux, la même identité Android. Cette version est encore en recette sur téléphone.

## 1. Crée ton projet

Ouvre la gestion des projets, choisis les tâches et les classes, puis configure ta source dans **Importer**. Pour le premier essai, prends un petit dossier local et une taille de lot facile à relire. Choisis aussi un dossier de sortie auquel tu pourras revenir.

Cadryl garde chaque projet séparément : sa source, ses lots, ses annotations et son historique.

## 2. Prépare les images

Indexe la source puis prépare le lot. Les copies exactes déjà traitées dans ce projet sont reconnues, même après nettoyage. Une image recadrée ou retouchée peut rester un cas différent.

Si une image n’a pas pu être récupérée, réessaie ou exclus-la avec un motif. Un échec de téléchargement ne compte jamais comme une image validée.

## 3. Ajoute un modèle si tu en as besoin

Dans **Modèles**, importe un fichier compatible ou choisis une source Hugging Face autorisée. Les [conversions Charlbi](https://huggingface.co/Charlbi/Lite_rt_prepared_for_android_dataset_builder) ont chacune leur contrat et leurs résultats. Un bundle peut nécessiter plusieurs graphes, un processeur et un tokenizer : garde l’ensemble.

Inspecte le modèle, essaie une image, puis lance ses propositions sur le lot. Tu peux utiliser un modèle pour l’inférence même s’il n’est pas entraînable. Changer le prompt ou les réglages ne réécrit pas ce que tu as déjà annoté.

## 4. Relis et corrige

Ajuste les boîtes, points ou masques. Valide les images utilisables et rejette les autres en indiquant pourquoi. Les propositions restent des propositions jusqu’à ta décision. Une image sans annotation n’est pas automatiquement un exemple négatif.

## 5. Exporte et vérifie la copie

Garde le **JSONL canonique** avec les images nécessaires. Les exports COCO, YOLO, WebDataset et vision-langage complètent ce format selon la tâche ; [le schéma](../DATA_SCHEMA.md) précise ce qu’ils conservent.

Crée l’archive et copie-la vers le dossier ou le dépôt HF choisi. Attends sa relecture avant de nettoyer le lot. Si le réseau coupe ou qu’un commit est en conflit, règle l’erreur depuis son reçu ; ne repars pas du principe que l’envoi a réussi.

## 6. Entraîne une copie, si tu le souhaites

L’apprentissage est désactivé par défaut. Il demande un modèle compatible et un lot corrigé dont l’export a été vérifié. Le partage des images doit fournir **au moins 32 images d’apprentissage et 8 de contrôle** ; avoir 40 images au total ne garantit pas ce partage.

Le premier passage crée une version entraînée distincte. Les suivants repartent de ses derniers poids validés, même si tu continues à utiliser l’original pour les propositions. L’activation des nouveaux poids reste ton choix. [Versions, checkpoints et reprise](MODEL_LINEAGE.md).

## 7. Nettoie et passe à la suite

Après copie vérifiée, confirme le nettoyage. Si un entraînement est en cours ou interrompu, termine-le ou abandonne explicitement la tentative d’abord. Les reçus, l’historique anti-doublons et les versions de modèle référencées restent disponibles. Tu peux alors préparer le lot suivant.

## Un blocage ?

| Ce qui se passe | Où regarder |
|---|---|
| Android refuse l’installation | Version Android, espace libre et signature de l’app déjà installée |
| Le modèle fonctionne mais ne s’entraîne pas | Contrat et signatures de cette variante ; les variantes `_learning` sont distinctes |
| Le bundle ne s’ouvre pas | Graphes et fichiers annexes manquants |
| L’apprentissage est refusé | Export relu, répartition réelle des images, contrat et checkpoint |
| Le dossier de sortie n’est plus accessible | Volume disponible et autorisation Android toujours accordée |
| Les prédictions sont mauvaises | Classes, prétraitement et pertinence du modèle pour tes images |

[Limites connues](../KNOWN_LIMITATIONS.md) · [Workflows](WORKFLOWS.md) · [Signaler un problème](https://github.com/unicornwhodev/vision-dataset-studio/issues).
