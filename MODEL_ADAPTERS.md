# Contrats et adaptateurs de modèles V3

## Usage

Importer des poids `.tflite` depuis le sélecteur de fichiers ou un lien HTTPS direct, consulter le rapport réel des tenseurs, choisir un gabarit et adapter son JSON. Enregistrer puis tester sur une image du lot avant de préannoter toutes les images. Un profil sauvegarde le contrat, le chemin local, l’empreinte et le rapport ; ses poids ne sont pas inclus dans un pack.

Les contrats d’exemple sont dans `examples/models/`. **Ce sont des contrats, pas des modèles ni des promesses de compatibilité avec tout export d’une même famille.** Le schéma courant est `schemaVersion: 1`. Les champs inconnus sont refusés à l’import.

## Entrée réellement supportée

Une entrée image, batch 1 ; dimensions fixes de 1 à 2 048, 1 ou 3 canaux. Layout NHWC ou NCHW ; RGB, BGR ou gris. FLOAT32, UINT8 ou INT8. Déclarer moyenne/écart-type globaux ou par canal. `quantizationMode: tensor` utilise scale et zero-point du tenseur pour quantifier les valeurs normalisées. INT8 exige ce mode. UINT8 `raw` conserve la sémantique V2 : octets image bruts.

`resizeMode` choisit letterbox, stretch ou center_crop. La couleur du padding est configurable. Les coordonnées de sortie sont ensuite ramenées dans le repère de l’image annotée. Les images originales ne sont pas modifiées par ce redimensionnement d’inférence.

La normalisation est `(valeur_octet - mean) / std`, sauf UINT8 raw. Pour une normalisation ImageNet usuelle, convertir les moyennes/écarts-types du domaine [0,1] vers [0,255] : le contrat ne devine pas cette unité.

## Sorties

| Adaptateur | Contrat de sortie |
|---|---|
| `ssd` | Indices séparés boxes/classes/scores/count, count facultatif avec -1 ; boîte yxyx, xyxy ou xywh. Classes entières avec `classOffset` explicite. |
| `yolo` | `[1,4+C,N]` ou `[1,N,4+C]`, ou +1 canal d’objectness lorsque déclaré. Boîtes cx/cy/w/h, classes dans l’ordre `labels`, coordonnées pixels ou normalisées. |
| `xyxy_score_class` | `[1,N,6]` : x1,y1,x2,y2,score,index de classe. Ne pas sélectionner YOLO brut pour ce format. |
| `classification` | `[C]` ou `[1,C]` ; scores déjà bornés, sigmoid ou softmax, seuil et top-k. |
| `points` | `[1,N,2]` ou `[1,N,3]` : x,y[,score] ; une classe commune ou une classe par slot. Sans score, la valeur 1 sert de sentinelle, pas de confiance calibrée. |
| `heatmap` | `[1,H,W,C]` ou `[1,C,H,W]` ; un argmax par canal, centre de cellule. Ce n’est ni une segmentation ni un extracteur multi-pics. |

NMS par classe pour les détections YOLO brutes ; seuil et maximum de propositions configurables. Une sortie avec boxes/scores/classes déjà filtrés est sélectionnée explicitement. Les formats non conformes, indices absents, scores non finis et activations inconnues échouent au lieu de produire une géométrie devinée.

`outputMode: points` ou `both` permet une proposition au centre, en haut ou en bas d’une boîte. **Le bas d’une boîte ne certifie pas la base physique d’un objet.** `deriveCounts` compte les boîtes retenues ; ces propositions sont non exhaustives et ne créent pas une assertion de zéro objet en cas de sortie vide.

## DINOv3 et modèles multimodaux

Aucun runtime spécifique à un checkpoint DINOv3 n’est livré. Une tête exportée vers un des contrats ci-dessus pourra être intégrée après inspection et essais. Un encodeur d’embeddings seul n’a pas de sortie de pointing à interpréter.

Le runtime intégré demeure l’Interpreter CPU TensorFlow Lite 2.16.1 hérité du projet. La migration vers LiteRT CompiledModel et les délégués GPU/NPU n’est pas implémentée. Aucun modèle réel n’a été exécuté dans cet environnement.

## Serveur de modèles local facultatif

`runtime: local_http` est un **client d’un serveur déjà lancé sur le même appareil**. Il ne lance, n’installe et ne charge pas un VLM. Sans ce serveur, l’appel échoue explicitement ; les outils manuels restent disponibles.

Seuls `http://127.0.0.1` et `http://localhost` sont autorisés. Proxy désactivé, résolution DNS limitée au loopback et redirections interdites. Aucun jeton HF n’est ajouté. Un service sur le PC ou sur le réseau local n’est pas pris en charge par ce client dans cette version.

`requestTemplate` est une chaîne contenant du JSON. Les variables remplacent une **valeur JSON entière**, pas un fragment arbitraire de texte : `$image_base64`, `$image_data_url`, `$model`, `$prompt`, `$width`, `$height`, `$mime`. L’image est envoyée en JPEG. Un prompt différent, un champ `model` différent ou une structure de requête différente peuvent ainsi être configurés sans modifier le client.

`responsePath` traverse des clés et indices séparés par des points, par exemple `choices.0.message.content`. Ce n’est pas un moteur JSONPath ou une expression exécutable.

`httpOutputMode: caption_text` attend une chaîne non vide. Le mode `proposals` attend un tableau d’objets respectant `ModelProposal`, éventuellement contenu dans une chaîne JSON. Les tâches autorisées sont object_detection, pointing, classification, captioning, vqa, counting ou multitask. Le type doit correspondre à la tâche du contrat.

Exemple de réponse pour le mode propositions :

```json
{"predictions":[{"type":"point","label":"object","score":0.8,"pointX":0.4,"pointY":0.6}]}
```

Types possibles : point, box, tag, caption, vqa, count. Caption utilise `text`; VQA utilise `question` et `text`; count utilise `label` et `count`. Les coordonnées doivent être normalisées dans le repère de l’image envoyée. Le client ne peut pas vérifier le hash des poids du serveur : la provenance indique le profil/contrat, pas une attestation de son modèle distant.

Aucun en-tête d’authentification arbitraire, streaming de tokens ou outil agentique n’est implémenté. Ne pas coder de secret dans le JSON du contrat : ce contrat est exporté avec le pack.

## Delta V4 : catalogue et mesures

PublicModelCatalog configure trois entrées publiques à partir de leurs métadonnées lues à l’import ; poids absents de l’archive et essais réels non exécutés. Les références sont dans SOURCES.md. DeviceBenchmark ajoute les durées et mémoire échantillonnée sans modifier les annotations ; aucun résultat Android n’a été produit ici. Les adaptateurs V3 restent inchangés dans leur périmètre.
