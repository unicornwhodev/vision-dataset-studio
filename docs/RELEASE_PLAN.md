# Publication — 4.2.0-rc4

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
