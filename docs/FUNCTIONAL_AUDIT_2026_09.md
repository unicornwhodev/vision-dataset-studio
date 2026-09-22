# Recette fonctionnelle — septembre 2026

Les modèles peuvent être installés sans manifeste SHA et utilisés après modification de leur empreinte. Les SHA stockés restent des informations de provenance : ils ne bloquent ni import, ni sélection de profil, ni chargement de bundle, ni démarrage de l’apprentissage. La compatibilité est vérifiée sur les fichiers et tenseurs réels. Les protections du corpus, des exports et des checkpoints restent distinctes.

**Français** · [English](en/FUNCTIONAL_AUDIT_2026_09.md)

Cette recette contrôle les chemins exécutés par l’application Android : production par lots, corrections, paramètres, modèles et apprentissage facultatif. Les résultats des émulateurs ne constituent ni une mesure de précision métier ni une qualification de téléphone ARM. Les poids restent téléchargés séparément de l’APK.

## Corrections apportées

- **Inférence sans apprentissage.** Un modèle sans signatures d’apprentissage reste importable et utilisable pour les tâches déclarées par son contrat. Le changement vers un tel profil désactive l’apprentissage continu ; une ancienne option activée ne bloque pas le nettoyage. Un graphe brut ne récupère jamais le contrat de l’ancien modèle : configurez son prétraitement et ses sorties, ou utilisez le contrat fourni par le catalogue.

- **Annotations stables.** Modifier un prompt, un modèle ou des réglages ne recalcule aucun cas existant. Les reprises et la préannotation ordinaire ignorent les annotations enregistrées, même importées avec un ancien statut « en attente ». Seules les commandes explicites « Relancer l’IA » (image) et « Relancer les propositions IA » (lot, confirmation) remplacent les propositions non validées ; les corrections humaines restent protégées.
- **Suggestions visibles.** Le compteur de relecture couvre points, boîtes, masques, tags, légendes, questions/réponses, comptages et liens texte-région. Dans l’éditeur, il ouvre directement le panneau approprié. Les propositions restent distinctes des annotations relues ; une nouvelle inférence conserve les corrections humaines.
- **Projets et lots.** Le changement de projet vide les informations du projet précédent. Supprimer un lot conserve son numéro, le curseur source et les empreintes anti-doublons. Réinitialiser est une action distincte permettant explicitement une réimportation. Les lots en transfert ou en apprentissage restent protégés.
- **Réglages du modèle.** Le panneau du modèle actif expose les paramètres appliqués par son adaptateur : threads CPU, seuil, limites de résultats, NMS et prompts lorsque disponibles. Les modifications sont propres au projet. Pour TinyCLIP, `{label}` est remplacé par la classe ; sans marqueur, la classe est ajoutée. Les détecteurs sans entrée texte refusent un prompt au lieu de l’ignorer.
- **Anglais.** Les écrans, confirmations, erreurs, diagnostics et descriptions applicatives disposent de variantes françaises et anglaises. Les contenus utilisateur, noms de projets, classes et réponses de modèles ne sont pas traduits automatiquement. L’option de conseils contrôle maintenant réellement leur affichage.
- **Workflows.** Les trois parcours conservent leurs consignes par projet et lot. Les consignes restent visibles dans l’éditeur et sont transmises au planificateur local avec son prompt système. Le planificateur propose un template ; la revue, la publication et le nettoyage restent des décisions humaines.
- **Réservations partagées.** La lecture des réservations et leur commit utilisent le même SHA distant. Un conflit impose une nouvelle lecture ; une réservation invalide ou appartenant à un autre appareil ne peut pas être écrasée silencieusement.

La langue déclarée d’une légende est une métadonnée. Elle ne transforme pas un modèle anglophone en modèle multilingue. Les prompts ne rendent pas entraînable un graphe qui n’expose pas les signatures nécessaires.

## Apprentissages distincts

L’apprentissage visuel reste désactivé par défaut. Il utilise les annotations relues du lot dont l’export a été vérifié, avant le nettoyage. Les rejets et les lots voisins sont exclus. Les images restent disponibles en cas d’échec ou d’annulation ; le checkpoint et l’historique anti-doublons survivent au nettoyage réussi. L’activation des poids appris reste explicite.

Le périmètre dépend de la conversion : certaines signatures ne modifient qu’une tête ou une adaptation des sorties, avec encodeur figé. Le réseau synthétique de recette permet de tester séparément la modification de couches visuelles internes. Un succès de cette fixture ne prouve pas que les conversions HF entraînent leurs encodeurs.

En cas d’erreur ou d’annulation, **Modèles → Apprentissage → Abandonner** clôt explicitement la tentative après confirmation, sans activer de poids ni supprimer d’images. Le nettoyage du lot reste une action distincte après vérification de l’export. Une nouvelle tentative peut être lancée explicitement ; un ancien apprentissage terminé ne satisfait pas la condition de nettoyage si l’export du lot a changé.

## Recette reproductible

`tools/build_android.py` produit les APK et leurs reçus immuables. `tools/qa/run_device_qualification.sh` contrôle leurs empreintes avant installation et conserve le résultat d’instrumentation. Les classes `AnnotationPreservationTest`, `FunctionalUiAuditTest`, `WorkflowExecutionTest`, `LocalEndpointWorkflowTest`, `ProjectMaintenanceTest` et `TrainingWorkflowTest` exercent les parcours concernés.

Les essais opt-in restent séparés :

- `ConvertedModelQualificationTest`, piloté par `tools/qa/qualify_converted_models.py` : conversion épinglée, inférence native, étapes d’optimiseur Android lorsqu’exposées, changement des sorties et restauration du checkpoint.
- `NativePhotoInferenceUiTest` avec `realPhotoAudit=true` : photo publique à empreinte contrôlée, TinyCLIP, changement du prompt et du top-K, propositions visibles, correction humaine conservée et relecture Room.
- `FeatureImplementationAuditTest` avec `audit_download_models=true` : téléchargements publics, inférences, préannotation et projections d’export.
- `InferenceOnlyModelTest` avec `inferenceOnlyAudit=true` : import Android de MobileNet sans signature train, sélection du profil, propositions réelles et nettoyage non bloqué par une ancienne option d’apprentissage.
- `HfLivePublicationTest` : uniquement après autorisation d’un **nouveau dépôt privé de QA**, sur une image générée. Deux réservations concurrentes, publication Android, relecture, reprise sans doublon et nettoyage. Le test refuse un dépôt existant et ne lit pas le jeton stocké par l’utilisateur dans l’application.

Les tests ignorés faute de fixture ou d’autorisation ne sont pas comptés comme réussis. Les reçus associent chaque essai au build réellement installé. Le planificateur HTTP testé avec une réponse contrôlée valide son protocole, pas la qualité d’un agent réel.

## Recette rc5 et résultats de développement

**Build `20260922T223550Z-3ee0d233110c` : 67 JVM réussis ; Android 38/39 puis reprise ciblée 1/1 réussie, aucun ignoré ; lint 0 erreur / 90 avertissements.** Le premier échec d’import provenait du budget occupé par les fixtures de conversions. Leur archivage réversible a permis le succès sans changement de code. Il ne s’agit pas d’une suite complète verte en une seule passe. [Preuves complètes](../test-results/functional-audit-20260922/README.md). Les contrôles SHA des modèles sont retirés ; le profil à empreinte périmée passe l’inférence Android.

Les essais du 22 septembre conservent leurs builds de développement :

| Essai | Résultat exécuté | Limite |
|---|---|---|
| Build `20260922T211013Z-a83cfea465e4` | 63 tests JVM réussis, aucun échec ni ignoré ; APK et APK instrumentée construites | Build de développement ; recette rc5 ci-dessus |
| `FunctionalUiAuditTest`, build `20260922T204312Z-40a01b844d03` | Parcours anglais, dialogues, création et changement de projets, prompt persisté, propositions visibles | Émulateur API 28 ; pas de qualification TalkBack ou téléphone |
| `NativePhotoInferenceUiTest`, build `20260922T211013Z-a83cfea465e4` | TinyCLIP reconnaît les chats d’une photo publique ; prompt et top-K changent les sorties à la relance explicite ; correction humaine préservée | Conservation passive des annotations, du statut et des reçus vérifiée ; un exemple ne mesure pas la précision |
| `FeatureImplementationAuditTest`, build `20260922T203037Z-00cb2a227a15` | 8 tests réussis : modèles publics, préannotation, contrat HTTP, corrections numériques et export | Les fixtures HTTP ne sont pas un agent réel |
| Protection des annotations, build `20260922T211013Z-a83cfea465e4` | 1 test de conservation des enregistrements et 3 tests de workflow réussis, dont reprise après changement de paramètres | Les propositions restent inchangées tant que le recalcul n’est pas explicitement demandé |
| Inférence seule, même build | MobileNet importé via URI Android, profil sélectionné, 3 propositions ; aucune signature train ni aucun apprentissage ; nettoyage autorisé malgré une ancienne option | Fixture synthétique ; ne mesure pas la précision |
| Export Android | ZIP relu indépendamment : 11 entrées, 10 empreintes, cohérence JSONL/COCO/YOLO/TAR | Corpus de recette synthétique |
| RTMDet sans apprentissage, build `20260922T201906Z-8b8b1f4b8b2d` | Inférence exécutée ; sorties dynamiques 40×40, 20×20, 10×10 correctement décodées | Zéro détection sur la fixture ; aucune conclusion de précision |

Le nouvel essai de DINOv2 produit 384 valeurs globales et une carte de 256×384 valeurs. TinyCLIP sans apprentissage produit deux propositions et 512 valeurs de représentation. EfficientFormer entraînable passe deux étapes Android, sauvegarde, restauration et reprise. Les reçus de conversion restent distincts des tests métier.

La campagne s’arrête ici pour la reprise sur le poste habituel : 13 conversions réussies, dont 8 avec apprentissage ; trois délais dépassés sur émulateur et 15 conversions non exécutées. HGNetV2 entraînable a passé inférence, apprentissage, sauvegarde/restauration et reprise après l’interruption initiale de l’émulateur. Les écritures HF réelles attendent l’autorisation du dépôt de QA dédié. Les essais non terminés ne sont pas comptés comme réussis.

La révision HF personnelle actuelle `36026262693de56b2cf45a6337a405297bfcfff6` conserve les 25 manifestes de la révision testée. L’identité de **118 artefacts runtime** a été vérifiée : 30 empreintes LFS exposées par HF et 88 fichiers auxiliaires téléchargés et hachés. Cette équivalence explicite permet de réutiliser les résultats des mêmes octets ; elle ne qualifie pas les conversions encore non exécutées. [Preuve des empreintes](../test-results/functional-audit-20260922/public-revision-equivalence.json). Les badges ne sont transmis à aucune autre révision ou copie du dépôt.
