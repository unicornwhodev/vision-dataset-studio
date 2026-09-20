# Préparation de publication

Destination : `unicornwhodev/vision-dataset-studio`, public. Licence : Apache-2.0.
[English](en/RELEASE_PLAN.md). Le propriétaire a autorisé le push des sources et de la documentation le 20 septembre 2026, après contrôle des secrets. Le dépôt doit conserver son statut de qualification : les fonctionnalités et validations manquantes bloquent encore la release stable.

## Artefacts attendus

| Artefact | Destination | Condition |
|---|---|---|
| Sources et documentation | Dépôt GitHub | Commit identifié et absence de secrets |
| APK Debug et APK de tests | Release de qualification | Reçu récent, signatures et SHA vérifiés |
| Rapports de tests et captures | Assets de release | Résultats réels, aucune donnée privée |
| APK/AAB release | Release stable | Signature durable et recette de distribution |
| Image de build reproductible | GitHub Packages / GHCR | Dockerfile contrôlé, build et test de l'image |

GitHub Releases accepte les APK comme fichiers joints. GitHub Packages ne fournit
pas un registre de paquets APK : GHCR servirait ici à distribuer les outils de build.

## Conditions avant push

1. Comparer les modifications à l'archive d'origine et identifier le commit testé.
2. Mettre à jour le statut de qualification sans convertir un test absent en succès.
3. Vérifier README, licence, notices et inventaire des dépendances résolues.
4. Exclure tokens, clés de signature, modèles téléchargés, données utilisateur,
   caches de build et réglages propres au pod.
5. Vérifier l'identité GitHub utilisée pour créer et pousser le dépôt.

Le connecteur GitHub de cette session a confirmé `unicornwhodev`. Le terminal
était connecté à un autre compte lors du préflight : la connexion du terminal au
compte cible doit être vérifiée avant la création, sans copier de jeton dans le
code, les rapports ou le pod.

## Conditions avant release

- `dist/android/latest.json` désigne la tentative qualifiée et ses deux APK.
- Chaque asset doit correspondre exactement au SHA-256 du reçu.
- La description indique version, commit, tests effectués, tests non exécutés,
  compatibilité testée et problèmes encore ouverts.
- Une signature Debug n'est pas une signature de distribution pérenne.
- La publication n'implique ni test HF réel ni qualification sur téléphone si
  ces résultats ne sont pas documentés séparément.

Références :
- https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases
- https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry

## Fabriquer le paquet de qualification

Après qualification et commit des sources/schémas, exécuter
`python3 tools/package_qualification.py` à partir du build CI de ce même commit.
Le script exige un reçu de succès, vérifie les octets des APK et refuse un arbre
Git modifié ou un `github_sha` différent. Il produit un ZIP et son SHA-256 dans
`dist/packages/`. Ce paquet Debug n’est pas une release stable. Le refus du build
en échec sur le pod a été vérifié ; aucun ZIP de qualification n’a été promu.

## Publication de qualification autorisée

**État réel :** le [premier lancement Actions](https://github.com/unicornwhodev/vision-dataset-studio/actions/runs/35478811998)
a été refusé avant exécution pour un problème de facturation du compte. L’image de build
décrite ci-dessous n’a donc pas été construite ni publiée. La prérelease utilise le build
du pod déjà testé. `tools/package_pod_qualification.py` vérifie les 155 empreintes au
commit testé, l’absence de changement du code Android, les deux APK et les preuves 14+2
tests ; aucun reçu CI n’est inventé et le contrôle du paquet CI reste intact.

Le package `ghcr.io/unicornwhodev/vision-dataset-studio-qualification:v4.2.0-rc2`
contient le ZIP des APK et de la documentation, au format OCI. Il s’extrait avec ORAS :

```bash
oras pull ghcr.io/unicornwhodev/vision-dataset-studio-qualification:v4.2.0-rc2
```

Ce paquet n’est pas une image exécutable avec `docker run`. Un package GHCR nouvellement
créé est privé par défaut ; sa visibilité est distincte de celle du dépôt GitHub.
Si nécessaire, le propriétaire la change en public dans les paramètres du package.
La release GitHub publique fournit aussi les mêmes fichiers et leurs SHA-256.

Pour la future image de build, le workflow manuel
`publish-qualification.yml` construit l’image `packaging/Dockerfile`, compile réellement
l’application dans celle-ci et exécute les tests JVM/lint. Il publie cette même image
sur `ghcr.io/unicornwhodev/vision-dataset-studio-build` et crée une **prérelease** avec
l’APK utilisateur, son SHA-256, le digest de l’image et le ZIP de qualification FR/EN.
L’APK instrumentée reste dans le ZIP technique. Aucun secret ni poids ne fait partie
du contexte Docker. Le jeton éphémère de GitHub Actions autorise la publication.

Ce workflow ne remplace pas la recette instrumentée API 28/35 ni la qualification
complète des modèles et du téléphone ; les notes de release distinguent ces limites.
