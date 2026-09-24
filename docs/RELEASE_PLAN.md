# Livrer Cadryl

[Documentation](README.md) · [English](en/RELEASE_PLAN.md)

La livraison actuelle est **4.2.0-rc6**, une prérelease signée avec la clé durable, [publiée et vérifiée](RC6_PUBLICATION_RECEIPT.json). Les fichiers gardent leurs noms techniques `vision-dataset-studio` pour conserver les liens et les scripts existants. [Contenu et résultats rc6](RC6_RELEASE.md).

## Ce qui est distribué

- `vision-dataset-studio.apk` : app Release ARM64, Android 9+, sans poids de modèle.
- `vision-dataset-studio-4.2.0-rc6-qualification.zip` : APK ARM64/x86_64 et de tests, pilote UI, documentation, notices et preuves sélectionnées.
- Trois archives Maven pour Flex `2.16.1-vds16k1`, Graphics Path `1.0.1-vds16k1` et LiteRT `2.2.0-vds16k2`.
- `PACKAGE.json`, `PUBLICATION_CHECKS.json` et `SHA256SUMS` : provenance et intégrité.

Les fichiers sont publiés dans la [release GitHub](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc6). Le [package GHCR](https://github.com/users/unicornwhodev/packages/container/package/vision-dataset-studio-qualification) garde sa visibilité privée. Il contient des artefacts, pas un conteneur exécutable.

## Avant de publier

Construire les deux paires Release, les signer avec la même clé durable et tester les octets signés. Conserver les résultats partiels ou en échec. Sélectionner ensuite les preuves publiques, sans secret, poids ni corpus utilisateur. Le [guide de tests Release](RELEASE_TESTING.md) détaille les commandes.

Après revue et commit des sources, `tools/package_native_release.py` vérifie un arbre propre, les sources compilées contre Git, les APK contre les reçus de signature et les tests, puis l’audit natif. Il refuse de remplacer un dossier de livraison existant. Ses paramètres `--arm-build`, `--arm-signed`, `--x86-build`, `--x86-signed`, `--evidence`, `--qa-apk` et `--version` doivent désigner les tentatives effectivement testées.

Les archives sont relues avant upload. La publication utilise un nouveau tag ; un tag ou une release existante n’est pas écrasé. Après upload, comparer les assets GitHub aux tailles et SHA locaux, puis relire les couches OCI téléchargées. [Contrôles de publication](PUBLICATION_CHECKS.md).

## Mises à jour

Conserver l’identifiant Android et la [clé durable](SIGNING.md), puis augmenter `versionCode`. Les anciennes rc4/rc5 Debug ont d’autres signatures : leur incompatibilité reste signalée dans les téléchargements. Aucune désinstallation automatique ne doit servir de migration.

La CI préparée n’a pas été exécutée pour cette livraison. Les limites matérielles, la cause ART historique et la revue des notices restent visibles dans la note de release. rc6 n’est pas présentée comme une version stable.

La publication précédente est conservée dans le [reçu rc5](RC5_PUBLICATION_RECEIPT.json).
