# Qualification sur le pod — 19 septembre 2026

Version : **4.2.0-rc2**. Ce document remplace les constats de préparation RC1
concernant l’absence de SDK. Les preuves historiques restent conservées.

## Provenance et environnement

- Archive importée : `vision-dataset-studio-v4.2-rc2-chromebook.zip`.
- SHA-256 vérifié avant et après transfert :
  `394867d550e41d94957ff5d07d94f2b8edae449cebe498a52b3e2c86be408464`.
- Sources extraites : `/workspace/projects/vision-dataset-studio`.
- Import Git initial : `c8a8241` ; corrections de qualification décrites ci-dessous.
- Pod A4500 ; Ubuntu 24.04 x86_64 ; Temurin 21.0.12.1+1 ; Gradle 9.3.1 ;
  Kotlin CLI 2.2.10 ; SDK 36, build-tools 36.0.0, ADB et émulateur 37.1.11.
- Sources, outils, caches et preuves persistent dans `/workspace`.
- Pas de `/dev/kvm` : émulation CPU logicielle, rendu SwiftShader.

## Corrections effectuées

Le premier vrai build a échoué dans trois fichiers. Les corrections sont :
import `SourceEntryEntity` manquant ; prise en compte de la nullabilité réelle
renvoyée par MetadataExtractor ; garde explicite du lot nullable avant purge.
Les contrats de modèles et les conditions de sécurité de purge sont conservés.

Le contrôle d’identité attendait encore RC1 malgré le `versionName` RC2 ; il a
été aligné sur le candidat. La plateforme installée par la CI a été alignée sur
`compileSdk = 36`. Apache-2.0 a été ajouté sur décision explicite du propriétaire.

## Tests portables

196 contrôles réussis sur le pod : identité 12, preuves de build 15, politiques
44, moteur numérique 67, fiabilité 43, SQLite hôte 9, exports 6.
L’échec initial du contrôle d’identité est conservé avec sa relance corrigée.
Ces résultats ne qualifient pas les parcours Android ou Hugging Face réels.

Logs : `/workspace/qa/vision-dataset-studio/portable/`.

## Build Android

Première tentative : `20260919T181916Z-c9760f9be24d`, échec de compilation,
aucune APK promue. Le schéma Room v3 a été réellement généré par KSP.

Deuxième tentative : `20260919T183132Z-8d4c24b3ba18`.
La compilation et la vérification de l’APK Debug ont réussi.
L’APK de tests est également construite et vérifiée. Le build global termine
**failed** : 25/26 tests JVM Android réussissent, l’assertion de reprise de
`HfTransferV4Test.disconnectedDownloadResumesHashedPrefix` échoue (ligne 38).
Le test utilise MockWebServer en loopback, pas le service HF réel.
Lint : 0 erreur, 68 avertissements. Migrations Room Robolectric : 2/2 ; Moshi :
6/6 ; test de capture Compose exécuté avec succès. Le packager refuse ce reçu
non qualifié, vérifié à l’exécution.

| APK | Octets | SHA-256 |
|---|---:|---|
| Debug | 81965421 | `d619f2a50ad7fd2cf6b12730a9f73407f2d9c4d5319b74bd353c7ed154557f2b` |
| Tests | 2516991 | `f842c077ca7bfc5bf83ac35c9c63ed2b969b4ce69ec854da5589a93bd5007fa4` |

Les reçus et APK sont dans `dist/android/runs/` sur le pod.
Le fichier `dist/android/latest.json` désigne la dernière tentative.

## Contrôle de l’interface

Xvfb, ADB, VNC et noVNC sont installés. L’accès noVNC a répondu HTTP 200 via
le tunnel SSH, sur `http://127.0.0.1:6080/vnc.html`.
Les ports graphiques du pod écoutent uniquement sur loopback.

Les images API 28 Google APIs et AOSP ont démarré mais présentent des blocages
System UI en émulation logicielle. Un démarrage réussi n’établit donc pas la
stabilité de la session ni celle de l’application. L’APK a été installée puis
Atelier et Modèles affichés. Premier affichage : 28,082 s selon ActivityManager,
après expiration de l’attente `am start -W`. Le buffer crash consulté était vide.
À 320 dp, le bouton « Importer » est comprimé dans la bibliothèque de modèles :
point de recette UI à corriger. Captures et hiérarchies ADB :
`/workspace/qa/vision-dataset-studio/ui/`.

Une recette accélérée CI
API 28/35 et une recette sur téléphone dédié restent nécessaires.

## Tests instrumentés API 28

Les deux APK du reçu ont été vérifiées par empreinte puis installées avec succès.
La suite prévoit 8 tests : identité et migrations Room 1→3 / 2→3 ont réussi.
Le premier test SAF a ensuite bloqué : `SafFaultProvider`, exécuté dans le
processus de l’APK de tests, déclenche `NoClassDefFoundError` pour
`kotlin.jvm.internal.Intrinsics`. L’exécution a été arrêtée après collecte du
crash et de la capture ; elle ne constitue pas un résultat « 3/8 réussi » complet.
Les quatre tests SAF n’ont pas de verdict fiable dans cette tentative. Le test
Compose a été lancé séparément : **1 test réussi en 43,343 secondes** (`compose-only.log`).

Preuves : `/workspace/qa/vision-dataset-studio/device-api28/`, notamment
`instrumentation.txt`, `crash-during-tests.txt` et `operator-note.txt`.
Le script de qualification a correctement refusé l’absence de résultat JUnit
complet. Aucune donnée applicative n’a été effacée pour contourner l’échec.

## Publication

Cible retenue : dépôt **public `unicornwhodev/vision-dataset-studio`** ;
licence **Apache-2.0**. Aucun dépôt, package ou release n’a été publié.
Le workflow de qualification, le guide du poste, la roadmap et le plan de
publication sont préparés. Le conteneur GHCR reste à construire et tester.

Pas de test HF en écriture, d’inférence ARM, de benchmark téléphone ni de recette
complète lots 2 + 1 / purge / reprise. Ne pas présenter cette préparation comme
une qualification produit achevée.
