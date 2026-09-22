# Contrat des conversions entraînables pour Android

L’apprentissage s’exécute **dans l’application Android** avec `Interpreter.runSignature`.
Le pod est un poste de compilation et de recette. Aucun serveur d’entraînement,
transfert de corpus ou envoi de gradients n’est intégré à l’application.

Les graphes d’inférence existants ne deviennent pas entraînables en ajoutant du
JSON. Le convertisseur doit exporter des variables mutables et les fonctions
qui calculent les gradients, mettent à jour les variables et les persistent.
Pour entraîner le backbone, ces gradients doivent couvrir ses couches internes,
pas seulement la tête. La propriété `scope` documente ce choix ; elle ne le prouve pas.

## Fichiers attendus dans une famille HF

- `model.tflite` : graphe avec signatures et variables mutables.
- `android_model_config.json` (ou `model_config.json`) : contrat Android ci-dessous.
- `artifact_manifest.json` : optionnel ; informations de provenance du convertisseur.
- Model card, licence, prétraitement, révision upstream et rapport de conversion.

Le téléchargement utilise une révision HF résolue pour récupérer un ensemble cohérent. Aucun manifeste SHA ni comparaison d’empreinte ne conditionne l’import ou l’utilisation du modèle.
Les conversions dynamiques conservent `runtime_contract.json` et `config.json`.
Le runtime refuse un contrat incompatible ; il ne devine pas l’encodage des cibles.

## Exemple de contrat : classification entraînable

```json
{
  "task": "classification",
  "adapter": "classification",
  "inputWidth": 224,
  "inputHeight": 224,
  "inputLayout": "NHWC",
  "inputType": "FLOAT32",
  "mean": 0.0,
  "std": 255.0,
  "resizeMode": "stretch",
  "labels": ["classe_a", "classe_b"],
  "scoreActivation": "none",
  "training": {
    "trainSignature": "train",
    "inferSignature": "infer",
    "saveSignature": "save",
    "restoreSignature": "restore",
    "imageInput": "x",
    "targetInput": "y",
    "lossOutput": "loss",
    "checkpointInput": "checkpoint_path",
    "learningRateInput": "learning_rate",
    "inferOutputs": ["scores"],
    "targetEncoding": "one_hot",
    "targetShape": [1, 2],
    "weightProbeSignature": "weights",
    "weightProbeOutput": "visual_kernel",
    "weightProbeInput": "probe",
    "scope": "internal_visual_and_output_layers"
  }
}
```

Les dimensions, classes, normalisation et noms ci-dessus sont un exemple,
à remplacer par ceux du modèle. `train` reçoit l’image et les cibles FLOAT32.
`learning_rate` est un scalaire facultatif : laisser `learningRateInput` vide
si le taux est déjà fixé dans le graphe. La sortie `loss` est scalaire et finie.
`infer` reçoit l’image seule et retourne les tenseurs décrits par `inferOutputs`,
dans l’ordre attendu par les indices de l’adaptateur.

`save` et `restore` reçoivent un scalaire STRING `checkpoint_path`. Le chemin est
fourni par l’application dans son stockage privé. Le checkpoint peut comprendre
plusieurs fichiers partageant ce préfixe. Les fichiers sont hachés après écriture
et vérifiés avant restauration. Les signatures ne doivent écrire nulle part ailleurs.
Les noms peuvent être adaptés dans le JSON ; aucune expression ni code distant
n’est exécuté.

La signature facultative `weights` expose un tenseur de poids d’une couche visuelle
interne. Sa comparaison avant/après permet de prouver que cette couche a changé.
Le binding Java exige une entrée : `probe` est un scalaire FLOAT32 fourni à zéro
par l’application. La sortie doit être le poids inchangé lorsque `probe = 0`.
Elle ne doit pas retourner une constante, une métrique ou seulement la tête finale.

## Encodages des cibles actuellement implémentés

| Encodage | Forme | Sémantique |
|---|---|---|
| `one_hot` | `[1,C]` | Une seule classe humaine par image |
| `multi_hot` | `[1,C]` | Décision humaine positive ou négative explicite pour chaque classe |
| `points_xyv` | `[1,C,3]` | x, y normalisés dans l’entrée modèle, présence ; une cible par classe |
| `heatmap_nchw` | `[1,C,H,W]` | Gaussiennes centrées sur les points humains, sigma 1 pixel |
| `boxes_xyxy_class_mask` | `[1,N,6]` | xmin, ymin, xmax, ymax, indice classe, masque 0/1 ; zéro-padding explicite |
| `segmentation_point_valid_mask_nchw` | `[1,3,H,W]` | Masque, carte de point et validité ; supervision auxiliaire par tâche |

Le convertisseur doit utiliser exactement ces encodages, ou l’application doit
recevoir un adaptateur supplémentaire. Le contrat multitâche accepte les entrées auxiliaires `presence`, `abstention` et `supervision`. Les quatre indicateurs de supervision suivent l’ordre segmentation, point, abstention, présence. Une tâche non revue reçoit un indicateur nul : une annotation manquante ne devient jamais une absence. Les entrées texte pour l’apprentissage restent non prises en charge. Un bundle d’inférence peut être utilisable sans être entraînable.

## Cycle local et contrôle

L’option est désactivée par défaut. Un snapshot privé contient exclusivement les images acceptées du lot terminé, exporté et vérifié. Sa preuve d’export et son empreinte sont enregistrées. Aucun déclenchement ne suit une validation individuelle. Le nettoyage attend la fin de l’apprentissage et de son évaluation ; une interruption ou un échec conserve les images. Les fichiers
identiques restent dans le même groupe : attribution déterministe par SHA-256,
environ 80 % apprentissage / 20 % contrôle. Les quasi-doublons ou réencodages
ne sont pas regroupés automatiquement. Minimum : 32 images train et 8 contrôle. Les cibles sont limitées à un million de valeurs par image. Elles sont sérialisées séparément, hachées et chargées une image à la fois ; le budget disque couvre le lot entier.
Les doublons avec des cibles contradictoires sont refusés. Les propositions
non vérifiées ne peuvent pas servir de cibles.

WorkManager exécute le job sans exigence réseau, lorsque la batterie n’est pas basse.
L’état et les checkpoints permettent une reprise. La signature `infer` est exécutée
sur le contrôle, sans apprentissage de ces images. Un candidat doit améliorer la
perte de plus de 1 % ; le contrôle est réutilisé entre générations et ne constitue
pas une mesure indépendante de généralisation. Le rechargement du checkpoint doit
reproduire les mêmes sorties. L’activation reste explicite et l’ancien profil est
conservé. Un lot refusé, un échec et un arrêt ne remplacent pas le modèle actif.

## Validation à effectuer par conversion

1. Charger et exécuter les quatre signatures dans le runtime Android cible.
2. Vérifier qu’une couche interne annoncée change après des exemples supervisés.
3. Fermer l’interpréteur, restaurer le checkpoint et reproduire les sorties.
4. Vérifier la reprise et l’annulation, les absences, les classes et la géométrie.
5. Mesurer RAM, temps et batterie sur téléphone ; l’émulateur ne prouve pas ces coûts.
6. Évaluer précision et oubli sur un corpus autorisé représentatif indépendant.

Le script `tools/qa/create_training_fixture.py` compile seulement un petit réseau
visuel non entraîné. `OnDeviceTrainingTest` effectue les étapes d’apprentissage sur
Android. Cette recette synthétique ne qualifie ni les futurs modèles HF ni leur
précision. Les résultats réellement obtenus seront consignés séparément.

## Runtime Android

Le chemin `Interpreter` utilise LiteRT 1.4.2 avec Select TF Ops 2.16.1 et un délégué Flex explicite pour les signatures secondaires `save`/`restore`. Les AAR officiels LiteRT 2.1.5 et 2.2.0 inspectés ne fournissent pas l’API Java Delegate nécessaire : le test Android sous 2.2.0 a modifié les poids mais échoué sur FlexSave. Ce choix de compatibilité doit être qualifié avec chaque conversion HF ; il ne garantit pas tous les opérateurs des conversions récentes. L’API moderne CompiledModel n’est pas annoncée comme un backend d’apprentissage.

## Portée des conversions actuellement fournies

Les conversions HF entraînables inspectées exposent des têtes de classification, des adaptations de détection, un mélange de cartes de points ou une adaptation de sorties multitâches. **Leurs encodeurs visuels restent figés.** L’application peut mettre à jour les variables exportées par `train`, mais ne peut pas créer des gradients absents du graphe. Le réseau synthétique de recette expose aussi ses couches internes ; ce test distinct ne transforme pas les conversions HF en entraînement du backbone.

Les entrées de signatures à dimensions dynamiques sont redimensionnées à partir de tableaux multidimensionnels ; les sorties sont lues après l’invocation. Les checkpoints sont rechargés lorsque leur sélection change, même avec un modèle source identique. Le runner `tools/qa/qualify_converted_models.py` conserve les résultats d’inférence, de mise à jour, de sauvegarde et de reprise par conversion. Voir le rapport de qualification pour les résultats exécutés, sans les confondre avec une mesure de précision.
