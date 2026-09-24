# Vérifier une publication

[Documentation](README.md) · [Distribution](RELEASE_PLAN.md)

La release doit permettre de retrouver ce qui a été construit et testé. Le packageur compare les sources au commit, les APK aux reçus de signature et les résultats aux empreintes des APK. Une preuve d’un autre build ne suffit pas.

## La sélection publique

On publie les sources, la documentation, les notices et les preuves de recette utiles. Les tokens, clés privées, mots de passe, poids et corpus utilisateur restent hors Git et hors packages. Les captures viennent de données synthétiques. Les journaux sélectionnés sont relus avant publication.

Gitleaks analyse l’arbre exact destiné au commit et l’historique disponible. Les rapports détaillés restent locaux et expurgés. Chaque signalement est examiné : une empreinte publique peut être légitime, mais cela doit être vérifié dans le fichier ou l’artefact d’origine. Aucune règle du scanner n’est désactivée pour obtenir un résultat propre.

Les archives sont aussi inventoriées et relues, y compris leurs APK, AAR et JAR imbriqués. Le contrôle vérifie les chemins, les CRC et l’absence des fichiers privés interdits. `PUBLICATION_CHECKS.json` accompagne les fichiers livrés et donne les résultats de cette sélection précise.

## Publication rc6 vérifiée

Le [reçu du 24 septembre](RC6_PUBLICATION_RECEIPT.json) confirme les dix assets GitHub et les huit couches OCI retéléchargées. Les 706 fichiers sélectionnés, l’index, l’historique de 30 commits et les 16 archives imbriquées ont été contrôlés. Les signalements relus sont des empreintes publiques de sources et de clés ; aucun secret n’a été confirmé. Les reçus bruts sont conservés sans normalisation de fins de ligne.

## Après l’envoi

Les noms, tailles et SHA-256 des assets GitHub doivent correspondre aux fichiers locaux. Le tag doit pointer sur le commit de compilation. Les couches du package GHCR sont téléchargées de nouveau et comparées octet par octet par SHA-256.

Les reçus originaux de build ne sont pas réécrits. Le reçu final de publication relie commit, tag, release, assets et digest OCI. La [publication rc5](RC5_PUBLICATION_RECEIPT.json) reste consultable avec ses résultats historiques ; elle ne sert pas de scan pour rc6.

Un scan et une revue réduisent le risque. Ils ne constituent pas une garantie absolue d’absence de secrets.
