# Audit de complétude — 19 septembre 2026

> État historique de l’audit initial. Les correctifs de provenance/export, les bundles, l’index de similarité, le cycle anti-doublons et l’apprentissage Android optionnel ont évolué depuis cette matrice. Consulter [le rapport courant](../TEST_REPORT.md), [les limites](../KNOWN_LIMITATIONS.md) et [la production par lots](BATCH_PRODUCTION.md). Le moteur d’agent et les workflows généraux restent incomplets.


**Verdict : l’application n’est pas entièrement implémentée par rapport aux fonctions demandées.**
Le téléchargement, l’inférence CPU et la préannotation ont une implémentation réelle.
L’entraînement continu des poids, le moteur d’agent et les workflows automatisés
ne sont pas livrés. Deux défauts supplémentaires sont reproduits : provenance des
boîtes corrigées et incompatibilité du validateur avec les exports Android.

Cet audit ne modifie pas le code de production. Les tests ajoutés et les corrections
documentaires ne constituent pas une réparation des défauts ni une release validée.

## Périmètre et identité contrôlés

- Source examinée : `22b9cc332638ab02c98b7de732d4a62cc706643e`, version `4.2.0-rc2`.
- APK principale : tentative `20260919T203710Z-e9cd5c4ddbbe`, SHA-256
  `abf2f50fe63835ab885f23efc50f0db5033f167f2db72a778061355f8bc03c2d`.
- Émulateur AOSP API 28 x86_64, 960 × 720 px, 160 dpi, émulation logicielle sur
  le pod A4500. L’application utilise le CPU, pas le GPU du pod.
- Critères : demande du propriétaire, README, notes RC1/RC2, contrats et packs
  d’exemple, schéma de données, code des écrans, ViewModel et moteurs appelés.
- Données synthétiques isolées, base Room en mémoire pour le test de lot,
  répertoires `files/feature-audit/*`. Aucune écriture sur Hugging Face.

Les états « présent », « partiel », « absent » décrivent le code. La colonne de
preuve décrit séparément son exécution : un test numérique ou une lecture du code
ne remplace pas une recette Android complète ou une mesure de précision.

## Matrice fonctionnelle

Les chemins ci-dessous sont relatifs à
`app/src/main/java/com/unicornwhodev/visiondatasetstudio/`.

| Fonction attendue | Implémentation | Preuve et limite actuelle |
|---|---|---|
| Téléchargement des modèles publics LiteRT | Présent | Trois poids officiels réellement téléchargés, métadonnées inspectées et inférences réussies sur Android ; `PublicModelCatalog`, `HfApiClient`, `LiteRtEngine`. |
| Catalogue UWD / Hugging Face | Partiel | 22 références configurées ; 6 familles publiées et 3 téléchargeables par l’app au SHA distant relevé. Présence distante vérifiée, exécution des poids communautaires non qualifiée. |
| Import manuel, profils, activation/suppression | Présent | Chemins réels dans `ui/MainViewModel.kt`, SHA contrôlé à la sélection, refus de suppression de poids référencés. Parcours complet de bibliothèque non exécuté par ce nouvel audit. |
| Prétraitement des pixels | Présent, périmètre borné | Resize, normalisation, RGB/BGR/gris, NHWC/NCHW, quantification ; trois graphes UINT8 exécutés. FLOAT32/INT8 et tous les layouts ne sont pas chacun validés avec de vrais poids. |
| Décodage et préannotation | Présent, périmètre borné | SSD/EfficientDet/classification exécutés ; lot réel Room avec une image traitée, trois tags IA non validés, tag humain préservé, deux cas finalisés ignorés. YOLO/points/heatmaps : fixtures numériques, pas corpus réel qualifié. |
| Bundles et modèles image + texte | Absent | Aucun chaînage TinyCLIP, Florence-2, EfficientViT-SAM ; Grounding DINO/OWLv2 multi-entrées non exécutables par `LiteRtEngine`. |
| Embeddings, similarité, doublons, active learning | Partiel / fonctions aval absentes | `ModelAdapters` vérifie les valeurs puis renvoie une liste vide pour `embedding`. Aucun vecteur exposé/persisté ni index, clustering, sélection active ou tête de classification. |
| Mesures d’inférence | Présent, non qualifié sur téléphone | `DeviceBenchmark` et export des mesures existent. Les durées de cet audit sont des observations isolées sur émulateur logiciel, pas un benchmark ARM ni une mesure de précision. |
| Outils d’annotation et validation humaine | Présent | Neuf types de tâches, validation explicite, provenance et annuler/rétablir. Dessin de boîte et persistance contrôlés auparavant dans [UI_REDESIGN.md](UI_REDESIGN.md). Tous les outils n’ont pas une recette gestuelle complète. |
| Apprentissage des corrections de points | Présent, limité | Apprentissage numérique, promotion, AtomicFile, rechargement et reset testés réellement sur Android avec 160 corrections synthétiques. Propositions acceptées sans déplacement exclues ; lot non finalisé refusé. |
| Apprentissage des corrections de boîtes | Défectueux | La correction écrase la géométrie servant ensuite d’origine du modèle. Reproduction indépendante en échec, détaillée ci-dessous. |
| Entraînement continu du modèle visuel | Absent | Déclenchement manuel du correcteur uniquement. Aucun entraînement/fine-tuning des poids `.tflite`, tâche automatique à la validation, versionnement/promotions de nouveaux poids ou retour arrière de ces poids. |
| Templates de tâches | Présent | Six `WorkflowPreset` : point, multi, detect, caption, vl, sort. Ils sélectionnent des tâches, sans programmer une séquence d’actions. |
| Packs partageables | Présent, périmètre limité | `StudioPack` transporte nom, classes, tâches, taille de lot et contrat de modèle/prompt. Aller-retour Moshi réel et rejet des champs inconnus testés sur Android ; parcours SAF créant un projet non testé ici. |
| Workflows automatisés | Absent | Pas de graphe d’étapes, dépendances, conditions, ordonnanceur ni pipeline import → prétraitement → correction → entraînement → export configurable. |
| Prompt et consignes de modèle | Présent comme configuration | `LocalModelClient` transmet `$prompt` et l’image JPEG ; requête `system` et décodage `choices.0.message.content` réellement testés contre un répondeur HTTP local. Ce répondeur n’est pas un LLM. |
| Agent et serveur VLM | Absent | Aucun moteur d’agent, outils appelables, boucle de décision, historique/mémoire, lancement de serveur ou runtime de génération intégré. Un champ prompt n’implémente pas ces fonctions. |
| Accès à un modèle sur le pod | Absent dans le client courant | `local_http` accepte seulement localhost/127.0.0.1 sur l’appareil Android. Une URL Runpod/réseau n’est pas prise en charge. Pas d’authentification arbitraire ni streaming. |
| Sources, projets, lots, sauvegarde | Présent, recette partielle | Sources HF Viewer, manifestes HF/locaux, dossier SAF, Room et états de lot. Import réel de trois images et conservation après relance précédemment testés ; cycle complet 2 + 1 restant. |
| Transferts interrompus | Implémentés, qualification en échec | Checkpoints/Range/ETag présents. Le test JVM `disconnectedDownloadResumesHashedPrefix` échoue déjà ; les nouveaux téléchargements complets n’annulent pas ce défaut. |
| Exports canonique/COCO/YOLO/VL/WebDataset | Présent, intégration défectueuse | ZIP Android réel, dix empreintes de manifeste et projections vérifiés indépendamment. Le validateur Python fourni refuse le nom des dossiers actuels. |
| Publication HF et coordination | Présent dans le code, non qualifié en service | Commits avec parent, reçus, claims/baux/DONE, gestion de conflit dans `HfApiClient`, `WorkClaimCoordinator` et `BatchEngine`. Aucun aller-retour HF en écriture ni concurrence réelle validés. |
| Copie SAF, purge et reprise | Présent dans le code, qualification bloquée | Contrôle de copie relue/reçus/verrous. Suite SAF bloquée par la dépendance Kotlin du fournisseur de test ; cycle de purge interrompue et panne de volume non exécuté. |
| Travail en arrière-plan | Absent comme garantie | Opérations au premier plan ; reprise manuelle depuis états/checkpoints. Le champ historique `prefetchEnabled` n’est lu par aucun moteur. |
| GitHub, packages, releases | Préparation seulement | Cible publique `unicornwhodev/vision-dataset-studio`, Apache-2.0, scripts/CI/plan présents. Dépôt/push/release non réalisés ; image GHCR non construite ; qualification insuffisante pour publier la version validée. |

## Résultats exécutés pendant l’audit

**Huit tests Android réussis, en deux suites (6 + 2).** Ce sont des tests
d’intégration des composants réels, pas huit parcours de bout en bout de l’UI.

| Essai | Résultat |
|---|---|
| SSD MobileNet V1 : téléchargement → inspection → inférence | PASS, 4 185 175 octets, entrée UINT8 300 × 300 |
| EfficientDet Lite0 : téléchargement → inspection → inférence | PASS, 4 563 519 octets, entrée UINT8 320 × 320 |
| MobileNet V1 classification : téléchargement → inspection → inférence | PASS, 4 287 874 octets, entrée UINT8 224 × 224 |
| Préannotation réelle du lot avec protection humaine | PASS, une image traitée sur trois, trois tags machine, cas finalisés ignorés |
| Transport HTTP du prompt et décodage de réponse | PASS, répondeur synthétique loopback explicitement identifié |
| Construction de l’export Android | PASS, ZIP de onze fichiers ; original inchangé ; manifeste vérifié dans l’app |
| Pack, consignes et validation des champs | PASS, sérialiseur Moshi Android et modèle réels |
| Correcteur de points, persistance et reset | PASS, 160 exemples géométriques synthétiques ; aucune mise à jour des poids visuels |
| Suite numérique existante | PASS, 67 tests, dont 1 500 transformations ; périmètre numérique |
| Origine des coordonnées après correction de boîte | **FAIL**, coordonnées d’origine remplacées par celles du correcteur |
| Validateur fourni contre le ZIP produit par Android | **FAIL**, recherche exclusive de `batches/batch-*` |
| Inspection indépendante du même ZIP | PASS, CRC, dix tailles/SHA, image canonique, boîte COCO en pixels, YOLO normalisé et TAR concordants |

Les trois modèles ont été exécutés sur un rectangle synthétique. Des propositions
SSD ou de classification sur cette image ne prouvent aucune exactitude sémantique.
EfficientDet a renvoyé zéro proposition ; l’inférence a bien terminé. La protection
du lot utilise un seuil de classification nul pour exercer le circuit de fusion,
sans prétention de précision métier.

Les anciens échecs restent ouverts : build global en échec, 25/26 tests JVM Android,
suite SAF bloquée, CI API 28/35 et téléphone non exécutés. Les 196 tests portables
antérieurs ne sont pas recomptés comme une nouvelle suite complète.

## Défauts et écarts prioritaires

### F01 — Origine des boîtes perdue après correction adaptative

`AdaptiveCorrection.apply` réécrit `xmin/ymin/xmax/ymax`, puis `ProposalMerger.merge`
enregistre ces coordonnées déjà corrigées dans `modelXmin/modelYmin/modelXmax/modelYmax`.
`AdaptiveCorrectionStore.train` les lit ensuite comme sortie brute du modèle.

Reproduction : `raw xmin=0.20` → correction `xmin=0.23` → `modelXmin=0.23`,
alors que l’origine devrait rester `0.20`. Une nouvelle génération peut donc
apprendre un résidu sur une mauvaise référence. Les points disposent de champs
d’origine séparés ; le test Android de points passe. Le test rouge est conservé
dans `tools/qa/audit/CorrectionProvenanceAudit.kt`.

Acceptation du correctif : origine brute conservée au travers inférence, correction,
édition humaine, sérialisation et deux générations successives d’apprentissage.
Définir également le traitement des historiques déjà produits, sans réécrire une
origine inconnue ni inventer une correction de migration.

### F02 — Le validateur livré refuse les exports Android actuels

`DatasetExporters` écrit `batches/p-91902-batch-000001/manifest.json` ;
`tools/hf_validate_and_convert.py` cherche `batches/batch-*/manifest.json` et les
annotations sous le même ancien motif. Résultat réel :
`FAIL No V2 manifest found. Legacy exports require their legacy validator.`

Les onze entrées de l’archive passent l’inspection indépendante. Il s’agit d’une
incompatibilité de découverte des lots, pas d’une corruption de ce ZIP.
Acceptation : archives historiques et nouvelles acceptées, chemins dangereux et
dossiers mal formés refusés ; test alimenté par une vraie sortie Android.

### F03 — Apprentissage continu demandé non livré

Le correcteur réalise une régression ridge à six variables sur des résidus
géométriques, avec 32 images d’apprentissage et 8 de contrôle minimum, correction
bornée et promotion conditionnelle. Il conserve au plus 2 048 exemples par groupe
et 128 groupes par projet. Le contrôle est réutilisé, sans preuve de généralisation.

Le bouton `trainCorrectionsFromBatch` est manuel, `adaptiveCorrection` est désactivé
par défaut et seuls points/boîtes admissibles alimentent ce système. Ni tags,
descriptions, réponses, ni poids LiteRT ne sont réentraînés. Le développement
nécessaire est décrit dans la [roadmap](ROADMAP.md), séparément du correcteur local.

### F04 — Agent et workflows : configurations sans moteur d’exécution

Le pack conserve les consignes ; le client les transmet. Aucun composant ne réalise
les étapes d’un agent ou d’un workflow automatisé. Les packs n’incluent pas les
poids, les données, l’historique des corrections ou la configuration complète des
sources/destinations. Aucun outil agentique n’est branché sur les actions du studio.

### F05 — Promesses du catalogue à aligner sur le code et les artefacts

État distant vérifié par API, dépôt
[`Charlbi/Lite_rt_prepared_for_android_dataset_builder`](https://huggingface.co/Charlbi/Lite_rt_prepared_for_android_dataset_builder/tree/92e53670853e0f298b2469b2038fd1552cfcc15e),
SHA `92e53670853e0f298b2469b2038fd1552cfcc15e` : 64 fichiers, 11 `.tflite` dans
6 familles. Les 16 ajouts RC2 ne disposent pas de poids publiés à cette révision.

| Famille publiée | Traitement actuel dans l’application |
|---|---|
| DINOv2 | Téléchargeable ; contrat embedding, vecteur non exposé ni exploité |
| ViTPose | Téléchargeable ; contrat heatmap 17 points proposé, inférence non qualifiée ici |
| RF-DETR | Téléchargeable pour inspection ; décodage de détection absent |
| TinyCLIP | Bundle détecté, activation désactivée |
| EfficientViT-SAM | Bundle détecté, activation désactivée |
| Florence-2 | Bundle détecté, activation désactivée |

De plus, les huit nouveaux backbones sont étiquetés `embedding` dans le catalogue,
mais `suggestedConfig` ne gère explicitement que `dinov2` et `vitpose` : les autres
retomberaient sur `inspect_only`. Le badge UI « Compatible » dépend de la possibilité
de téléchargement, ce qui ne prouve pas une préannotation fonctionnelle.

## Preuves et reproductibilité

- [Résumé structuré et empreintes](implementation-audit.json).
- [Sources des tests et commandes](../tools/qa/audit/README.md).
- Preuves brutes sur le pod : `/workspace/qa/vision-dataset-studio/feature-audit/`.
- Copie locale légère : `test-results/feature-audit/` (hors Git).
- Poids et APK restent hors Git. L’APK principale et le reçu `dist/android/latest.json`
  sont ceux de la tentative d’interface antérieure, sans promotion en succès.

La prochaine étape est de corriger F01/F02 et les blocages de qualification, puis
de développer les capacités manquantes avec leurs critères d’acceptation. Aucune
complétude globale, qualité d’annotation ou capacité agentique n’est certifiée ici.
