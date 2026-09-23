# Publication — 4.2.0-rc5

La rc5 est une **prérelease Debug de qualification**. L’APK Windows a passé
67 tests JVM et les deux suites Android 39/39 en 4 Ko et 16 Ko. Les preuves
publiques sont sélectionnées dans [windows-rc5-release](../test-results/windows-rc5-release/README.md).

**Signature incompatible avec rc4 :** le certificat Windows est
`16e1c6980c16c5cad26fab306cdfe16bbfe8e1cf039b59f691bb1bebfe558d84` ;
le certificat publié de rc4 est
`05d03f7e822372b5d6d503260477a4a39760788c86f7d4a9130af78f0a351251`.
Le propriétaire indique que l’ancien build a été produit sur une VM et qu’aucun
keystore correspondant n’est disponible localement. L’APK rc5 ne peut donc pas
mettre à jour rc4. Ne pas désinstaller une application contenant des données
pour contourner ce conflit. La clé privée Windows reste hors Git et des packages.

Assets rc5 : APK Debug utilisateur, archive de qualification avec APK instrumentée,
runtime Flex local Maven reproductible, candidat ARM64 **non signé**, `PACKAGE.json`,
`SHA256SUMS` et reçu OCI. Le candidat ARM64 n’est pas installable en l’état.
Le package GHCR contient des artefacts, pas une image de build exécutable.
Sa visibilité privée existante est conservée ; les assets de release sont publics.

`tools/package_verified_release.py` vérifie les octets testés contre chaque blob
du commit, les deux APK, les deux suites Android et le runtime natif. Les reçus
originaux gardent leur commit de base ; `PACKAGE.json` établit le lien vérifié
avec le commit des sources effectivement publiées.

```powershell
python -X utf8 tools/package_verified_release.py --build-dir dist/android/runs/20260923T104142Z-befce3e3596a --candidate-dir dist/distribution/runs/20260923T104437Z-7e7f388b5ee0 --evidence test-results/windows-rc5-release --version 4.2.0-rc5 --previous-certificate-sha256 05d03f7e822372b5d6d503260477a4a39760788c86f7d4a9130af78f0a351251 --allow-incompatible-prerelease-signature
```

## Historique rc4 et préparation initiale

**Préparation locale du 23 septembre 2026 :** un candidat Release ARM64 R8/ressources
optimisé de 100,2 Mo est construit, sans signature de distribution ni qualification
téléphone. Le crash Flex 16 Ko est corrigé ; trois autres bibliothèques gardent des signalements RELRO. La signature par certificat
explicitement choisi et l’inventaire des 111 dépendances runtime sont préparés.
[Reçus, commandes et blocages](WINDOWS_QUALIFICATION_2026_09.md). La distribution
publiée reste rc4 ; le reste de ce document décrit cette publication historique.

[English](en/RELEASE_PLAN.md). Dépôt public : [unicornwhodev/vision-dataset-studio](https://github.com/unicornwhodev/vision-dataset-studio). Licence Apache-2.0.

La rc4 est une **prérelease Debug de qualification**. Elle publie l’état réel de développement demandé par le propriétaire, avec la matrice LiteRT partielle et ses défauts ouverts. Elle ne constitue pas une release stable.

## Artefacts

- `vision-dataset-studio.apk` : seule APK destinée à l’utilisateur, sans poids embarqués.
- `vision-dataset-studio-4.2.0-rc4-qualification.zip` : APK utilisateur, APK instrumentée réservée aux tests, reçus, résultats, documentation FR/EN et licence.
- `PACKAGE.json`, `SHA256SUMS` et digest OCI : provenance et intégrité.

Le package `ghcr.io/unicornwhodev/vision-dataset-studio-qualification:v4.2.0-rc4` est cette archive au format OCI, **pas une image exécutable**. Il est lié au dépôt GitHub ; sa visibilité est actuellement privée, indépendante de celle du dépôt. La release fournit les artefacts publics. Avec un accès GHCR autorisé :

```bash
oras pull ghcr.io/unicornwhodev/vision-dataset-studio-qualification:v4.2.0-rc4
```

Le packageur `tools/package_workstation_release.py` exige un arbre Git propre, le manifeste complet des sources compilées identiques au commit, les APK conformes aux empreintes et les preuves Android du même build. Il refuse tout poids embarqué et toute suite instrumentée en échec ou ignorée. Il conserve séparément les résultats de modèles des builds de développement.

```bash
python3 tools/package_workstation_release.py --build-dir dist/rc4-build \
  --evidence test-results/stabilization-rc4 --version 4.2.0-rc4
```

Le packageur historique rc2 et celui de CI restent distincts. Aucun reçu CI n’est inventé. Le [nouvel essai Actions](https://github.com/unicornwhodev/vision-dataset-studio/actions/runs/35539418339) a été refusé avant démarrage pour un problème de facturation du compte. Le Dockerfile de build n’est pas qualifié.

## Signature et reprise

La clé Debug rc4 est sauvegardée hors Git pour les prochains builds. Le propriétaire confirme qu’aucun utilisateur n’a téléchargé rc2 ; aucune migration de distribution rc2 n’est prévue. Une future version stable exige une signature durable, la revue des notices et une recette sur téléphone.

Sources, SDK, émulateurs, conversions et preuves restent sous `/workspace` sur le volume persistant. Les APK et preuves publiques sont aussi sauvegardées sur le Chromebook avant l’arrêt du pod. Aucun jeton, poids ni clé privée n’est publié.

## Recette rc4

Les preuves du build rc4 sont dans [`test-results/stabilization-rc4/`](../test-results/stabilization-rc4/). La suite de base est exécutée séparément des deux classes de modèles qui exigent des fixtures/options explicites. EdgeNeXt et RepViT ont leur propre reçu Android ; les modèles non exécutés restent non qualifiés. Les preuves du 20 septembre et du build de stabilisation précédent sont conservées avec leurs identifiants d’origine.

Provenance Git : **162 des 163 fichiers enregistrés sont identiques octet par octet**.
Le seul écart est la conversion CRLF vers LF de `tools/gradle_bootstrap.py`,
script Python exécuté sur le poste de build. Les sources Android sont identiques.
`PACKAGE.json` conserve séparément les deux empreintes de ce script ; les reçus
originaux ne sont pas réécrits.
