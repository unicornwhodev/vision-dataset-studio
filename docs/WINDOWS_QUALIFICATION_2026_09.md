# Qualification Windows du 23 septembre 2026

**Rapport historique de la publication rc5.** La [campagne P1 suivante](P1_QUALIFICATION_2026_09.md)
ajoute les pannes réelles, le Honor, le correctif HF, DataStore 1.2.1 et la signature
durable. Les chiffres et artefacts ci-dessous restent ceux de rc5 publiée.

Base Git : `23b038fb63af71cd7b9fb3fc6324081d883d1316`, après `git pull --ff-only origin main`.
Le build précède le commit des correctifs. Les reçus identifient les octets du
répertoire de travail ; le packageur les compare aux blobs du commit de release
et conserve cette distinction dans `PACKAGE.json`.

Les [preuves publiques sélectionnées](../test-results/windows-rc5-release/README.md)
conservent les reçus originaux. La signature Windows diffère de rc4 ; consulter
[le plan de publication](RELEASE_PLAN.md) avant toute installation.

## Résultats actuels

| Contrôle | Résultat | Preuve locale |
|---|---|---|
| Build Windows Compose/Room/Moshi/LiteRT | Deux APK réelles, 99 fichiers KSP | `dist/android/runs/20260923T104142Z-befce3e3596a/` |
| JVM / Python | 67 / 70 réussis ; 63 Python au build, 7 contrôles de packaging ajoutés | Rapports du build / [Python avant publication](../test-results/windows-rc5-release/python-tests-publication.log) |
| Lint | 0 erreur, 88 avertissements | Rapports du build |
| Suite de base API 35 x86_64, 4 Ko | 39/39, zéro échec ou ignoré, UI démarrée | `test-results/model-lineage-20260923/final-api35-4k/` |
| Suite de base API 35 x86_64, 16 Ko | 39/39, zéro échec ou ignoré, UI démarrée | `test-results/model-lineage-20260923/final-api35-16k/` |
| Original / deux entraînements successifs | Original et profil intacts ; deuxième génération repart des poids de la première ; activation explicite | `workflow-training-evidence.json` dans chaque recette |
| Arrêt / redémarrage réel du processus | Reçus, graphes et checkpoint identiques avant/après | `lineage-after-process-restart.json` dans chaque recette |
| Flex ARM64 sous traduction Android x86_64 / 16 Ko | Deux tests natifs d’apprentissage réussis, ABI de package ARM64 vérifiée ; aucun téléphone testé | `test-results/flex-fix-20260923/training-16k-104804/` |
| Runtime Flex reconstruit | LOAD et RELRO 16 Ko vérifiés, ARM64 et x86_64 | `dist/native-flex/runs/20260923T100533Z-9ec1f3d7/` |
| Audit natif global | Six signalements RELRO subsistent dans trois autres bibliothèques | `test-results/model-lineage-20260923/native-apk-audit.json` |
| Release ARM64 R8/ressources | 100 190 260 octets, non signée, sans poids | `dist/distribution/runs/20260923T104437Z-7e7f388b5ee0/` |
| Notices | 111 artefacts runtime, aucune métadonnée de licence manquante ; revue native transitive ouverte | `third_party/` |
| CI | Préparée Linux/Windows et API 28/35/35-16 Ko ; non exécutée à distance | [Blocage historique de facturation](https://github.com/unicornwhodev/vision-dataset-studio/actions/runs/35539418339) |

APK Debug universelle : **413 877 313 octets**, SHA-256
`5f5154809b0a79c2e8567c2da6d2479140406e65fac1d38008f1932a58738e98`.
APK instrumentée : **3 263 018 octets**, SHA-256
`0205d79b9609beeaa16b130d0504bfc2dab21ffb025aeb749cff64032a179633`.
Candidat ARM64 : SHA-256 `7d360b92f9245cf88af13c5805da538bcd9eede654c910bc69eed63015c2274f`.
Les APK finales passent l’alignement ZIP 16 Ko. Aucun poids n’est embarqué.

## Conservation du modèle et reprise

La recette utilise un original importé avec son profil, puis deux lots synthétiques
exportés et relus. Le premier apprentissage crée une version séparée. Le second
restaure cette version sans exiger son activation pour l’inférence. Un seul profil
entraîné avance entre générations ; l’original reste accessible et inchangé.

Empreinte de l’original avant/après : `4e87dab654ddae4fcce9e17f2d8e5ead0733f0b539462053541482e74f73be4b`.
Poids internes finaux du premier entraînement et initiaux du deuxième :
`28cb72074ba3d20255ae573e7655fca417946418ae0a74709620a1699e23969a`.
Poids internes finaux du deuxième : `d6e251202182ecbe9ca7cea49edc32d81f02f8ebee202542f1091b79a407e86b`.
Perte de contrôle du deuxième : **0.003297472 → 0.002246924**.

L’interruption/reprise, le refus d’un checkpoint manquant, la copie des poids,
la relecture par une nouvelle session LiteRT, le nettoyage et les empreintes
après redémarrage réel sont contrôlés. Le contrôle synthétique ne prouve aucun
gain de précision métier. Voir [la filiation](MODEL_LINEAGE.md).

## Correctifs et reproduction

Les commandes Windows gèrent `.bat`/`.exe`, les chemins SDK avec espaces, UTF-8
et les transferts ADB binaires. Le cache Gradle reste isolé dans `dist/gradle-home`
sauf variable explicite ; la jonction du cache partagé n’a pas été modifiée.
Les reçus vérifient les sources avant/après, les APK, le runtime réellement
empaqueté et les schémas. Un échec ne promeut pas une ancienne APK.

Flex 2.16.1 est reconstruit depuis les sources épinglées, avec Bazel 6.5.0 et NDK
r25b. Les opérateurs, gradients, Save/Restore et l’API Java sont conservés.
LiteRT Interpreter reste en 1.4.2. Voir [FLEX_16K.md](FLEX_16K.md) pour le build
Linux/WSL préalable et les empreintes. Le build Android ordinaire tourne ensuite
sous Windows. L’ancien contenu ZIP incrémental n’est pas réutilisé lors de
l’assemblage final ; les APK historiques restent dans leurs reçus immuables.

```powershell
python -X utf8 -m unittest discover -s tools/qa -p 'test_*.py'
python -X utf8 tools/build_android.py
python -X utf8 tools/qa/prepare_core_fixtures.py
$env:VDS_ALLOW_TEST_INSTALL = '1'
python -X utf8 tools/qa/run_device_qualification.py --serial SERIAL_DE_RECETTE --expected-page-size 16384 --training-fixture dist/fixtures/training-fixture --tokenizer-fixture dist/fixtures/hf-runtime-fixture
python -X utf8 tools/build_release_candidate.py --abi arm64-v8a
```

Prévoir Python 3.11, JDK 17+ (21 utilisé), SDK/build-tools 36, `adb` et les licences
déjà acceptées. Les fixtures nécessitent le venv `tools/qa/requirements-core.txt`.
Répéter la recette sur un appareil dédié en pages 4096. Le script ne désinstalle
pas l’application et n’effectue aucune écriture HF.

La suite de base exclut explicitement cinq classes : bundles HF, conversions,
photo native, publication HF réelle et conservation entre installations. Elles
ne sont pas déclarées réussies par cette suite. Le test d’apprentissage de base
utilise une fixture synthétique publique, sans entraînement sur l’hôte.

## Historique conservé

- Build Windows initial `20260923T011455Z-dd6ec9350f4a` : 67 JVM, 57 Python,
  39/39 en 4 Ko, APK Debug 366 625 089 octets. En 16 Ko : 19 tests terminés puis
  crash natif Flex. Journaux : `test-results/windows-qualification-20260923/api35-16k/`.
- La vraie base de schéma 4 et une annotation humaine synthétique ont survécu à
  la sauvegarde relue, l’arrêt et `adb install -r` :
  `test-results/data-preservation-5f89951a77fc/`. Ce résultat couvre **4 → 4**,
  avec identité/signature constantes ; aucune ancienne installation v1/v2,
  permission SAF ou clé Keystore n’est qualifiée par cette preuve.
- Le premier Release optimisé plantait sur l’énumération Moshi
  `PointLocalizationState.LOCALIZED`. Les règles R8 ont corrigé le défaut et la
  QA x86_64 a relu l’annotation conservée. Le candidat ARM64 initial mesurait
  77,8 Mo. Le nouveau Flex complet augmente sa taille à 100,2 Mo ; aucune
  réduction d’opérateurs n’a été tentée. Le candidat actuel reste non qualifié
  sur appareil, sans signature de distribution.
- Le premier build Flex corrigé `20260923T101947Z-937d0ee6e6b5` avait déjà passé
  39/39 en 4 Ko et 16 Ko. La recette actuelle ajoute la filiation des modèles.

## Limites restantes

**P1 — qualification globale 16 Ko :** Flex ne plante plus. Les fins RELRO de
`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so` et
`libtensorflowlite_jni.so` restent signalées pour ARM64 et x86_64. Le vérificateur
strict n’a pas été assoupli ; le succès fonctionnel sur émulateur ne supprime pas
ces constats. Un téléphone ARM est reporté à la demande du propriétaire.

**P1 — données et transferts :** ancienne base réellement utilisée, permissions
SAF réelles, perte de volume/quota, corpus représentatif et transferts HF avec
pannes réelles restent à qualifier. Aucun dépôt HF n’a été créé ou modifié.
La CI attend aussi la résolution du blocage de facturation et l’exécution du
nouveau workflow ; aucune exécution distante n’est inventée.

**P2 :** les conversions gardent leurs résultats historiques : 13 succès,
trois timeouts à reprendre et 15 non exécutées. La qualité attend un corpus
annoté autorisé ; les pertes synthétiques ne mesurent pas la généralisation.

**P3 :** le candidat ARM64 est non signé. La clé durable, son certificat et sa
sauvegarde restent à choisir ; la clé Debug n’est pas une clé de distribution.
`tools/sign_release_candidate.py` vérifie le certificat attendu et refuse Debug.
Les notices du runtime modifié sont conservées, mais la revue des composants
transitifs natifs reste ouverte. Aucune publication n’a été faite pendant ce
correctif.

Provenance Git : **162 des 163 fichiers enregistrés sont identiques octet par octet**.
Le seul écart est la conversion CRLF vers LF de `tools/gradle_bootstrap.py`,
script Python exécuté sur le poste de build. Les sources Android sont identiques.
`PACKAGE.json` conserve séparément les deux empreintes de ce script ; les reçus
originaux ne sont pas réécrits.
