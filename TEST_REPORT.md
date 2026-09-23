# Qualification Windows — 23 septembre 2026

**23 septembre 2026 :** build Windows réel, **67 tests JVM, 70 tests Python (63 au build et 7 contrôles de packaging ajoutés), 39/39 tests Android en 4 Ko et 39/39 en 16 Ko**, aucun ignoré. Le crash Flex est corrigé. L’original est conservé et deux entraînements successifs reprennent la même version entraînée. APK ARM64 optimisée : **100,2 Mo**, non signée. Trois autres bibliothèques gardent des signalements RELRO ; la qualification 16 Ko globale et le téléphone ARM restent ouverts. [Preuves et limites](docs/WINDOWS_QUALIFICATION_2026_09.md).

Build `20260923T104142Z-befce3e3596a` : 99 fichiers KSP, lint 0 erreur / 88 avertissements. Les reçus incluent les empreintes des modèles, la reprise du deuxième entraînement et les fichiers conservés après arrêt/redémarrage du processus. La CI distante reste bloquée par la facturation ; les transferts HF et la qualité sur corpus représentatif restent à qualifier. Les paragraphes suivants sont historiques.

# Reprise sur le poste habituel — 23 septembre 2026 (avant correctifs Windows)

Sources compilées `a0d9737`, build `20260922T223550Z-3ee0d233110c` : **67 tests JVM et 52 tests Python réussis**, lint **0 erreur / 90 avertissements** ; les 158 fichiers compilés correspondent au commit. L’APK ne contient aucun poids.

**Android : 38/39 réussis, puis le test restant réussi à la reprise ciblée (1/1), aucun ignoré.** L’import sans apprentissage a d’abord été refusé car les fixtures de conversions dépassaient le budget de stockage. Leur déplacement réversible hors du répertoire de travail a permis le succès sans modification de code, suppression de données ni réinstallation. Les deux journaux sont conservés ; aucune suite complète verte en une seule passe n’est revendiquée pour ce dernier build.

L’abandon explicite, la reprise d’une nouvelle tentative et le refus de nettoyage fondé sur un ancien export passent dans `TrainingWorkflowTest`. La fixture utilise 88 images acceptées, exclut 8 rejets et le lot voisin, s’interrompt après 9 étapes puis reprend jusqu’à 198. Les bundles acceptent un manifeste minimal ou d’anciennes empreintes tout en exigeant leurs fichiers runtime réels.

[Preuves](test-results/functional-audit-20260922/README.md) · [Reprendre le développement](docs/DEVELOPMENT_RESUME.md). Les modèles ont 13 succès, dont 8 avec apprentissage ; trois délais dépassés et 15 conversions non exécutées. rc5 est poussé en sources ; la distribution publiée reste rc4.

---

# Recette rc5 — 22 septembre 2026 UTC

Build `20260922T214516Z-3f844ff6cb1e` (versionCode 10) : **66 tests JVM et 39 tests Android réussis**, aucun échec ni ignoré dans ces sélections. **52 tests Python**, lint **0 erreur / 90 avertissements**. APK sans poids ; 158 fichiers compilés correspondent au commit `c3c9cb1`. [Rapport bilingue et preuves](test-results/functional-audit-20260922/development-audit9/README.md).

Import sans apprentissage, utilisation avec empreinte de profil périmée, conservation des annotations, paramètres, projets, lots, UI anglaise et workflows passent sur Android. L’apprentissage de la fixture utilise les 88 images acceptées du lot exporté, reprend après annulation jusqu’à 198 étapes, puis autorise le nettoyage. La campagne des conversions reste séparée et en cours ; aucune qualification ARM, précision métier ou publication HF réelle n’est revendiquée.

---

# Validation de la release 4.2.0-rc4 — 22 septembre 2026

Build `20260922T183415Z-818e4cf57d14` (versionCode 9) : **54 tests JVM, 24 tests Android de base et 2 régressions HF réussis**, sans échec ni test ignoré dans ces sélections. **52 tests Python**, lint **0 erreur / 89 avertissements**. APK sans poids, 143 fichiers de compilation comparés au commit `d9b2239`. [Rapport bilingue et preuves rc4](test-results/stabilization-rc4/README.md).

Les classes de modèles nécessitant des fixtures/options sont exécutées séparément. Les 2 régressions HF concernent des modèles déjà qualifiés ; elles n’augmentent pas la couverture. Prérelease Debug sur émulateur API 28, sans qualification ARM ni nouvelle exécution CI.

---

# Validation du 22 septembre 2026

Build corrigé **`20260922T175957Z-8ef3e5cc2b0d`**, base `351dd3f` : **54 tests JVM**, **48 tests Python**, **24 tests Android réussis et 10 ignorés**, aucun échec. Lint : **0 erreur, 89 avertissements**. APK construite, signée, installée et contrôlée sans poids embarqués ; 181 fichiers sources comparés, aucune différence.

[Rapport détaillé FR/EN et preuves](docs/validation-20260922/README.md). Le rapport distingue les tests réellement exécutés des fixtures absentes, les corrections de compilation et le blocage System UI observé sur l’émulateur logiciel. Les paragraphes suivants conservent l’historique antérieur à cette qualification ; leur absence de SDK ne décrit plus le poste actuel.

---

# Historique rc3 — build et tests du 20 septembre 2026 UTC

## Third stabilization pass — 22 septembre 2026

La troisième passe ajoute la compatibilité partielle, traite `NEGATIVE` comme décision exclusivement humaine, porte les quatre états de localisation sur chaque point, lie explicitement les instances box/masque pour COCO, adapte l’action de l’éditeur et homogénéise les diagnostics runtime. Des tests Kotlin ont été ajoutés pour ces chemins mais n’ont pas été exécutés ici.

Complément instance : les relations peuvent désormais être créées et retirées depuis l’éditeur, sont validées avant revue/export et suivent des règles conservatrices lors des duplications, fusions et séparations. Les tests Kotlin correspondants sont écrits ; leur statut d’exécution est celui consigné ci-dessous et ne doit pas être déduit de leur présence.

Complément i18n : les libellés et confirmations statiques principaux des parcours Setup, éditeur, publication, contrôles, qualité, workflow et lots disposent maintenant de ressources FR/EN. Le test Compose anglais a été étendu à leurs actions critiques, mais n’a pas été exécuté ici faute de SDK Android.

Une passe supplémentaire externalise les descriptions restantes de Setup et Workflow ainsi que leurs valeurs dynamiques principales (compte connecté, lignes inspectées, colonne image et création de dépôt). Les contrôles hôte restent à 47 réussites ; cette vérification ne remplace toujours pas Compose sur Android.

Le complément pointing/grounding ajoute un aller-retour JSONL canonique des quatre états et des tests purs de résolution phrase-région. Le contrat grounding HTTP dispose désormais d’un mode de sortie explicite, d’un validateur de références/seuil et d’un garde de revue humaine ; les tests Kotlin couvrant ces règles sont écrits mais ne sont pas comptés comme exécutés tant que Gradle reste bloqué par l’absence de SDK Android.

Exécuté : `python -m unittest discover -s tools/qa -p 'test_*.py'` (**47/47 réussis**) et `git diff --check` (réussi). Les quatre commandes Gradle demandées ont été tentées ; chacune s’est arrêtée avant compilation/test/lint parce que le SDK Android est introuvable et `ANDROID_HOME` absent. Aucun APK ni schéma KSP n’a été produit, aucun test Android/JVM nouveau n’est revendiqué, et le projet n’est pas déclaré stable ou qualifié.

## Passe de stabilisation du 22 septembre 2026

La passe de finalisation du catalogue dérive maintenant les capacités des contrats installés sans transformer cette capacité théorique en qualification. Les actions principales Modèles/Apprentissage ont été externalisées et un test Compose anglais a été ajouté. Il n’a pas été exécuté ici : le conteneur ne contient ni SDK Android, ni `adb`, ni appareil. Les mesures téléphone ARM, RAM, latence, thermique, SAF réel et corpus représentatif restent **non exécutées**. Aucun nombre de qualification existant n’a été augmenté.

La revue suivante fait partager au preflight et à `prepare()` une inspection unique et valide les contrats distants avant d’afficher leurs capacités. Un test pur de projection COCO a été ajouté. Les 45 contrôles Python hôte passent, mais les nouveaux tests Kotlin/Android ne sont toujours pas exécutés faute de SDK ; aucune qualification supplémentaire n’est annoncée. L’anglais des écrans Modèles/Apprentissage est étendu, tandis que l’externalisation des autres écrans reste ouverte.

La validation suivante porte les contrôles Python hôte à **47 réussites** grâce à la parité stricte des ressources FR/EN et aux libellés anglais critiques. L’export COCO de boîtes et masques ajoute une validation structurelle avant écriture atomique et des tests Kotlin de RLE/JSON, présents mais non exécutés ici faute de SDK Android. Ce résultat ne qualifie toujours ni APK ni appareil.

Les tests unitaires de l'intégrité runtime/documentation, des capacités, de la navigation et du préflight ont été ajoutés. La commande `./gradlew test --no-daemon` a été exécutée mais s'est arrêtée avant les tests faute de SDK Android configuré (`ANDROID_HOME` absent). Aucun nouveau succès Android/JVM n'est revendiqué. Le détail et les limites sont consignés dans [docs/STABILIZATION_2026_09.md](docs/STABILIZATION_2026_09.md).

La seconde passe ajoute les chemins d’exécution du préflight, de maintenance locale et des outils d’annotation. Le téléchargement direct du SDK Android a été tenté mais refusé par le serveur (`HTTP 403`) ; la compilation et les nouveaux tests Kotlin restent donc non exécutés dans ce conteneur.

La passe complémentaire ajoute un test instrumenté de reprise après interruption post-transaction, des tests purs de polygone/remplissage/fusion/séparation et un test de relecture des reçus d’inférence. Ils sont présents mais ne sont pas annoncés comme réussis tant que le SDK Android manque.

Dernière APK : **4.2.0-rc3**, build `20260920T215432Z-cdd13d7fbf98`, sources compilées `2738b13` (132 fichiers comparés au commit, aucune différence).

- **26/26 tests JVM**, aucun ignoré ; lint **0 erreur, 72 avertissements**.
- **19/19 tests Android**, aucun ignoré, **99,092 s** : lots/doublons, concurrence, Room, SAF injecté, tokeniseurs, similarité, apprentissage synthétique et reprise, workflows et gestes de masques, navigation Compose.
- **45 tests Python hôte**, **72 contrôles moteur**, **16 contrôles cibles d’apprentissage** réussis. Ces contrôles portables restent distincts des tests Android.
- APK utilisateur : **365 878 582 octets**, SHA-256 `37af2f8daa53169a44eca215fd4c50ebec6ba0d648759fd31ef8b6c6be083072`. Aucun poids embarqué.
- **5 conversions HF réussies** : inférence RepViT et quatre conversions avec apprentissage/sauvegarde/restauration/reprise ; **1 défaut RTMDet ouvert**, **25 conversions à tester**. Les reçus conservent leurs builds de développement respectifs. [Détails FR/EN](docs/LITERT_QUALIFICATION.md).

Le défaut initial de peinture des masques (0 instance après tap) est corrigé ; le test dédié et la suite finale passent. Les anciennes tentatives et leur échec restent conservés. Les conversions HF entraînables figent encore leurs encodeurs ; la preuve de modification de couches visuelles concerne uniquement la fixture synthétique.

Preuves publiques sélectionnées : [`test-results/litert-rc3/`](test-results/litert-rc3/). Aucun poids privé, identifiant de dépôt privé, clé ni token n’y figure. La CI reste bloquée avant exécution par la facturation GitHub. Téléphone ARM, précision métier, serveur d’agent réel et écritures HF sur dépôt de QA restent à qualifier. **Prérelease Debug, pas release stable.**

---

# Historique rc2 (résultats conservés, antérieurs à rc3)

# Tests courants — production par lots et apprentissage optionnel

Actualisation du 20 septembre 2026 (Europe/Paris), sur le pod de compilation et son émulateur API 28 x86_64 sans KVM. L’apprentissage des tests s’exécute dans Android, aucun optimiseur sur l’hôte.

## Recette finale du build 20260919T234604Z-8a3382d3e997

- **26/26 tests JVM**, aucun ignoré. Lint : **0 erreur, 71 avertissements**.
- **14/14 tests Android métier**, 90,509 s, puis **2/2 tests Compose**, 75,55 s.
- WorkManager : **88 images acceptées du lot exporté**, 8 rejets exclus, lot voisin exclu. Interruption après 12 étapes, reprise jusqu’à 198. Perte de contrôle 0,689740 → 0,003558 sur fixture synthétique.
- Nettoyage refusé avant la fin, puis réellement exécuté : copies d’images et snapshot supprimés, checkpoint et image du lot voisin conservés.
- 155 fichiers sources locaux comparés à ceux du pod : **aucune différence**.
- APK principale : SHA-256 `0fe9e90838183153b32b4a0c712eb814e4e6aa274e09cdb547a38d9e8477d73f`, **403 138 636 octets**, aucun poids détecté. APK Debug universelle, pas une release signée de distribution.
- RepViT HF a aussi passé son test Android sous le même runtime (build précédent `…2bc826cf21ad`, test 67,051 s). Cette durée de test sur émulateur ne mesure pas la latence d’un téléphone ni la précision du modèle.

Preuves : `test-results/batch-production/` (journaux, métriques, reçu de build, inventaire APK et comparaison des sources). Les essais HF complets, téléphone et CI restent à faire. Le réseau d’apprentissage est une fixture synthétique, pas une conversion HF de production qualifiée.


## Exécution intermédiaire avant le dernier build

| Contrôle | Résultat exécuté |
|---|---|
| Build `20260919T233636Z-2bc826cf21ad` | APK et APK de tests produites, vérifiées, installées ; contrôles JVM/lint réussis |
| Suite Android ciblée | **13/13 réussis**, 41,464 s ; journal `test-results/batch-production/android-batch-7.log` |
| Lots 2+1 | Deux copies exclues du second lot ; export réel, purge, réouverture de la base, curseur obsolète sans écrasement |
| Concurrence | Une seule identité acceptée pour deux copies concurrentes ; image distincte conservée |
| Room 1/2/3 → 4 | Trois migrations Android réussies, données et hashes conservés ; schéma v4 généré par KSP |
| SAF injecté | Quatre tests réussis, dont copie relue, écriture refusée, lecture perdue et contenu altéré |
| Apprentissage natif | 48 étapes Android, poids visuels internes modifiés, perte 0,685404 → 0,018374 ; checkpoint rechargé avec sorties identiques |
| Tokeniseurs HF | Encodage/décodage comparés aux références HF épinglées |
| Index de similarité | Recherche persistante et isolation des contrats de modèle réussies |
| Contrôle APK sans poids | Inventaire vide ; garde de build et tests du détecteur ajoutés |
| Reçus de build, garde sans poids | 17 tests hôte réussis ; distincts d’une compilation Android |



Les problèmes HTTP du serveur de test IPv6, de fournisseur SAF Kotlin et de provenance des boîtes ont été corrigés ; leurs premiers échecs restent documentés dans l’historique suivant. Le validateur accepte désormais les ZIP Android `p-<projet>-batch-*` (7 tests hôte).

---

# Historique initial — qualification 4.2.0-rc2 sur le pod

Exécution réelle du 19 septembre 2026, JDK 21.0.12.1+1, Gradle 9.3.1, SDK 36.
Les résultats de préparation RC1 sont conservés dans l’archive initiale et le
commit d’import `c8a8241`. Ce rapport décrit la nouvelle exécution.

| Contrôle | Résultat |
|---|---|
| Tests portables | 196 réussis après correction de l’identité RC2/licence |
| Compilation principale | Réussie après trois corrections Kotlin documentées |
| APK Debug et APK de tests | Produites ; signatures, identité et SHA-256 vérifiés |
| Schéma Room v3 | Généré réellement par KSP et conservé dans `app/schemas/` |
| Tests JVM Android | 25 réussis / 26 ; 1 échec |
| Migrations Room sous Robolectric | 2 réussies ; fixtures SQL reconstruites |
| Tests Moshi | 6 réussis avec les dépendances réelles |
| Capture Compose sous Robolectric | Test exécuté avec succès, pas une recette d’écran complète |
| Lint Debug | 0 erreur ; 68 avertissements |
| Installation et premier affichage | APK installée ; Atelier puis Modèles affichés |
| Tests instrumentés API 28 | Identité + 2 migrations réussies ; suite bloquée dans le fournisseur SAF |
| CI API 28/35 et téléphone | Non exécutés |
| HF réel, modèle ARM, lots 2+1, purge/reprise | Non qualifiés |

Échec JVM : `HfTransferV4Test.disconnectedDownloadResumesHashedPrefix`, assertion
de succès de la reprise à la ligne 38. Ce test utilise un serveur HTTP local de
simulation ; aucun dépôt Hugging Face n’a été modifié.

Tentative complète : `20260919T183132Z-8d4c24b3ba18`, statut **failed**. Les deux
APK restent des artefacts de diagnostic, pas une version qualifiée. Le packager
refuse cette tentative comme attendu. La première tentative de compilation
échouée reste conservée séparément.

Le [rapport du pod](docs/POD_VALIDATION.md) donne chemins, limites et suites à
terminer. Les preuves brutes restent sur le pod et une copie légère est dans
`test-results/pod-20260919/` sur le Chromebook (hors Git).

## Refonte de l’interface

Tentative `20260919T200729Z-bec11e4607bc` : APK principale et de tests compilées,
signées, vérifiées puis installées. Les **2 tests Compose instrumentés passent**
sur l’AVD API 28 (74,293 s de suite JUnit). Ils couvrent l’accès aux projets et au
catalogue sans téléchargement, puis Importer/Export/Qualité/Atelier à 320 dp.

Les 26 tests JVM ont été réexécutés : 25 réussissent, le même test de reprise HTTP
échoue (sur cette exécution, à la ligne 21). Lint : 0 erreur, 68 avertissements.
Les contrôles d’identité ont été relancés localement : 12/12 réussis.
Recette manuelle sur trois images synthétiques importées par SAF dans un projet
séparé : création d’une boîte, annuler/rétablir, propriétés, fermeture et reprise
avec conservation de la boîte. Le canevas mesure 480 × 515 px à 320 dp.
La suite SAF bloquée n’a pas été relancée par ces tests UI.

Le reçu global reste **failed**, malgré les tests UI réussis. La refonte ne rend
pas la version publiable. Voir [la recette visuelle](docs/UI_REDESIGN.md).

Dernière correction : `20260919T203710Z-e9cd5c4ddbbe` borne les aperçus de l’accueil pour éviter leur
débordement sur grand écran. Les deux tests Compose repassent à 960 dp
(75.807 s JUnit). L’accueil est contrôlé visuellement après installation ;
la boîte enregistrée reste présente dans l’éditeur. Les vérifications détaillées
des gestes à 320 dp ci-dessus portent sur `20260919T200729Z-bec11e4607bc` ;
le code de l’éditeur est identique entre les deux tentatives.

## Audit de complétude fonctionnelle

Sur la même APK principale (`abf2f50f…03c2d`), huit nouveaux tests instrumentés
réussissent en deux suites : 6 tests en 51,818 s JUnit, puis 2 en 15,243 s.
Ils couvrent trois téléchargements/inférences avec de vrais poids, préannotation
de lot, HTTP local, export, pack/prompt et correcteur de points persistant.
Les 67 tests numériques existants ont également été relancés avec succès.

Deux contrôles supplémentaires échouent : provenance brute des boîtes après
correction adaptative et validateur Python face au ZIP réellement produit par
Android. L’inspection indépendante de l’archive passe (CRC, SHA et projections).
Ces défauts restent ouverts, comme l’échec JVM et le blocage SAF précédents.

L’entraînement continu des poids, le moteur d’agent et les workflows automatisés
sont absents. Voir [la matrice et les preuves](docs/IMPLEMENTATION_AUDIT.md).

## Affinage du poste d’annotation

Build `20260919T213548Z-f221e25ccc2f` : 2/2 tests Compose à 960 dp, puis 2/2 à
320 dp. Sélection d’image, conservation des annotations et dessin/annuler/rétablir
vérifiés. [Captures et limites](docs/UI_REFINEMENT.md). Le défaut JVM de reprise
HTTP persiste ; aucun verdict global de publication n’est modifié.
